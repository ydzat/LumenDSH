#!/usr/bin/env node
// M9.1 watchdog. Runs outside DSH (Termux tmux/boot loop) and supervises one
// launcher process. It never touches DSH sessions or credentials itself.
import {spawn} from 'node:child_process';
import {appendFileSync, closeSync, existsSync, mkdirSync, openSync, readFileSync, renameSync, unlinkSync, writeFileSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';

function usage() {
  process.stdout.write(`Usage: supervise.mjs --port N [options] -- COMMAND [ARGS...]\n` +
    `Options: --interval-ms --timeout-ms --fail-threshold --max-restarts\n` +
    `         --backoff-ms --max-backoff-ms --state-dir\n`);
}

const argv = process.argv.slice(2);
const flags = {intervalMs: 5000, timeoutMs: 3000, failThreshold: 3, maxRestarts: Infinity,
  backoffMs: 1000, maxBackoffMs: 30000, startupGraceMs: 25000,
  stateDir: path.join(os.homedir(), '.local/state/sliver-wolf-supervisor')};
let command = [];
let afterSeparator = false;
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  if (afterSeparator) { command.push(value); continue; }
  if (value === '--') { afterSeparator = true; continue; }
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  switch (value) {
    case '--port': flags.port = Number(next()); break;
    case '--interval-ms': flags.intervalMs = Number(next()); break;
    case '--timeout-ms': flags.timeoutMs = Number(next()); break;
    case '--fail-threshold': flags.failThreshold = Number(next()); break;
    case '--max-restarts': flags.maxRestarts = Number(next()); break;
    case '--backoff-ms': flags.backoffMs = Number(next()); break;
    case '--max-backoff-ms': flags.maxBackoffMs = Number(next()); break;
    case '--startup-grace-ms': flags.startupGraceMs = Number(next()); break;
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--help': usage(); process.exit(0); break;
    default: throw new Error('Unknown option: ' + value);
  }
}
if (!Number.isInteger(flags.port) || flags.port <= 0) throw new Error('--port is required');
if (command.length === 0) throw new Error('Command is required after --');

const stateDir = flags.stateDir;
mkdirSync(stateDir, {recursive: true, mode: 0o700});
const lockFile = path.join(stateDir, 'supervisor.pid');
const statusFile = path.join(stateDir, 'status.json');
const logFile = path.join(stateDir, 'supervisor.log');
const holdFile = path.join(stateDir, 'hold');

function log(line) {
  const text = `${new Date().toISOString()} ${line}\n`;
  process.stderr.write(text);
  try { appendFileSync(logFile, text, {mode: 0o600}); } catch { /* logging must not stop supervision */ }
}

function acquireLock() {
  try {
    const fd = openSync(lockFile, 'wx', 0o600);
    writeFileSync(fd, String(process.pid));
    closeSync(fd);
    return;
  } catch (error) {
    if (error.code !== 'EEXIST') throw error;
  }
  let pid = 0;
  try { pid = Number(readFileSync(lockFile, 'utf8').trim()); } catch { pid = 0; }
  if (pid > 0) { try { process.kill(pid, 0); } catch { pid = 0; } }
  if (pid > 0) throw new Error(`Supervisor already running as ${pid}`);
  unlinkSync(lockFile);
  acquireLock();
}

const state = {supervisorPid: process.pid, childPid: null, state: 'starting', restarts: 0,
  consecutiveFailures: 0, lastHealth: 'unknown', lastHealthAt: null, lastExit: null,
  startedAt: new Date().toISOString(), updatedAt: null};
function saveState(extra = {}) {
  Object.assign(state, extra);
  state.updatedAt = new Date().toISOString();
  const temp = statusFile + '.tmp';
  writeFileSync(temp, JSON.stringify(state, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, statusFile);
}

let child = null, stopping = false, killing = false, backoff = flags.backoffMs, childStartedAt = 0;

function cleanupAndExit() {
  try { unlinkSync(lockFile); } catch { /* already gone */ }
  saveState({state: 'stopped', supervisorPid: null, childPid: null});
  process.exit(0);
}

function startChild() {
  if (stopping) return;
  saveState({state: 'starting'});
  log(`starting: ${command.join(' ')}`);
  childStartedAt = Date.now();
  child = spawn(command[0], command.slice(1), {stdio: 'inherit', env: process.env});
  saveState({childPid: child.pid ?? null, state: 'running'});
  child.on('exit', (code, signal) => {
    child = null;
    saveState({childPid: null, lastExit: {code, signal, at: new Date().toISOString()}});
    if (stopping) { cleanupAndExit(); return; }
    if (killing) killing = false;
    if (state.restarts >= flags.maxRestarts) {
      log('restart limit reached; stopping');
      stopping = true;
      saveState({state: 'stopped'});
      cleanupAndExit();
      return;
    }
    const wait = backoff;
    backoff = Math.min(backoff * 2, flags.maxBackoffMs);
    saveState({restarts: state.restarts + 1, state: 'restarting', consecutiveFailures: 0});
    log(`child exited code=${code} signal=${signal}; restart #${state.restarts} in ${wait}ms`);
    setTimeout(startChild, wait);
  });
}

function probe() {
  return new Promise((resolve) => {
    const request = http.get({host: '127.0.0.1', port: flags.port, path: '/', timeout: flags.timeoutMs},
      (response) => { response.resume(); resolve(true); });
    request.on('timeout', () => { request.destroy(); resolve(false); });
    request.on('error', () => resolve(false));
  });
}

let healthBusy = false;
setInterval(async () => {
  if (stopping || healthBusy || !child) return;
  if (Date.now() - childStartedAt < flags.startupGraceMs) return;
  // Deliberate maintenance (host activation) holds restarts so the watchdog does
  // not race the installer for the slot port.
  if (existsSync(holdFile)) return;
  healthBusy = true;
  const healthy = await probe();
  healthBusy = false;
  if (stopping || !child) return;
  if (healthy) {
    state.lastHealthAt = new Date().toISOString();
    if (state.lastHealth !== 'healthy' || state.consecutiveFailures !== 0) {
      saveState({lastHealth: 'healthy', consecutiveFailures: 0});
      log('health probe ok');
    }
    return;
  }
  const failures = state.consecutiveFailures + 1;
  saveState({lastHealth: 'unhealthy', consecutiveFailures: failures});
  log(`health probe failed (${failures}/${flags.failThreshold})`);
  if (failures >= flags.failThreshold && child && !killing) {
    log('child unhealthy; terminating for restart');
    killing = true;
    backoff = flags.backoffMs;
    child.kill('SIGTERM');
    setTimeout(() => { if (child) child.kill('SIGKILL'); }, 5000);
  }
}, flags.intervalMs);

function shutdown(signal) {
  if (stopping) return;
  stopping = true;
  log(`stopping on ${signal}`);
  saveState({state: 'stopping'});
  if (!child) { cleanupAndExit(); return; }
  child.kill('SIGTERM');
  setTimeout(() => { if (child) child.kill('SIGKILL'); }, 5000);
  setTimeout(cleanupAndExit, 7000);
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));

acquireLock();
saveState();
startChild();
