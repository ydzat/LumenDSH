#!/usr/bin/env node
// M9.2 slot manager. Starts/stops an independent DSH web instance ("slot") over
// a shared DSH_HOME and mints an entrance-authority cookie for it, so the relay
// can switch the client between slots without re-pairing.
import {spawn} from 'node:child_process';
import {appendFileSync, closeSync, existsSync, mkdirSync, openSync, readdirSync, readFileSync, renameSync, unlinkSync, writeFileSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';

const argv = process.argv.slice(2);
const command = argv[0];
const flags = {host: '127.0.0.1', stateDir: path.join(os.homedir(), '.local/state/sliver-wolf-slots')};
for (let index = 1; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  switch (value) {
    case '--name': flags.name = next(); break;
    case '--host': flags.host = next(); break;
    case '--port': flags.port = Number(next()); break;
    case '--runtime': flags.runtime = path.resolve(next()); break;
    case '--home': flags.home = path.resolve(next()); break;
    case '--workspace': flags.workspace = path.resolve(next()); break;
    case '--stable-port': flags.stablePort = Number(next()); break;
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--relay-dir': flags.relayDir = path.resolve(next()); break;
    case '--help':
      process.stdout.write('Usage: slots.mjs start|stop|status|switch --name N [options]\n' +
        '  start: --port --runtime --home --workspace --stable-port\n' +
        '  switch: --port --relay-dir\n');
      process.exit(0);
      break;
    default: throw new Error('Unknown option: ' + value);
  }
}
if (!command || !flags.name) throw new Error('Command and --name are required');
mkdirSync(flags.stateDir, {recursive: true, mode: 0o700});
const stateFile = path.join(flags.stateDir, flags.name + '.json');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function readState() {
  try { return JSON.parse(readFileSync(stateFile, 'utf8')); } catch { return null; }
}
function writeState(value) {
  const temp = stateFile + '.tmp';
  writeFileSync(temp, JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, stateFile);
}
function alive(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  try { process.kill(pid, 0); return true; } catch { return false; }
}
function findListener(port) {
  if (!Number.isInteger(port) || port <= 0) return 0;
  for (const entry of readdirSync('/proc')) {
    if (!/^\d+$/.test(entry)) continue;
    let command = '';
    try { command = readFileSync('/proc/' + entry + '/cmdline', 'utf8').replaceAll('\0', ' '); } catch { continue; }
    if (command.includes('bin.js web') && command.includes('--port ' + port)) return Number(entry);
  }
  return 0;
}
function exchangeToken(host, port, stablePort, token) {
  return new Promise((resolve, reject) => {
    const request = http.request({host, port, path: '/?token=' + encodeURIComponent(token),
      headers: {host: '127.0.0.1:' + stablePort}, timeout: 5000}, (response) => {
      const cookie = response.headers['set-cookie']?.[0]?.split(';')[0] ?? null;
      response.resume();
      response.on('end', () => resolve(cookie));
    });
    request.on('error', reject);
    request.on('timeout', () => { request.destroy(); reject(new Error('token exchange timeout')); });
    request.end();
  });
}
function cookieWorks(host, port, stablePort, cookie) {
  return new Promise((resolve) => {
    const request = http.get({host, port, path: '/',
      headers: {host: '127.0.0.1:' + stablePort, cookie}, timeout: 5000},
      (response) => { response.resume(); resolve(response.statusCode === 200); });
    request.on('error', () => resolve(false));
    request.on('timeout', () => { request.destroy(); resolve(false); });
  });
}

async function start() {
  if (!Number.isInteger(flags.port) || flags.port <= 0) throw new Error('--port is required');
  if (!flags.runtime || !flags.home || !flags.workspace) throw new Error('--runtime, --home and --workspace are required');
  if (!Number.isInteger(flags.stablePort) || flags.stablePort <= 0) throw new Error('--stable-port is required');
  if (readState() && alive(readState().pid)) throw new Error('Slot ' + flags.name + ' is already running');
  const logFile = path.join(flags.stateDir, flags.name + '.log');
  const bin = path.join(flags.runtime, 'node_modules/@deepseek-ai/dsh/lib/bin.js');
  const logFd = openSync(logFile, 'w', 0o600);
  const child = spawn(process.execPath, ['--expose-internals', bin, 'web', '--host', flags.host,
    '--port', String(flags.port), '--no-open'], {cwd: flags.workspace, detached: true,
    env: {...process.env, DSH_HOME: flags.home, DSH_TELEMETRY_DISABLED: '1'},
    stdio: ['ignore', logFd, logFd]});
  closeSync(logFd);
  child.unref();
  const failStart = (message) => {
    try { process.kill(child.pid, 'SIGKILL'); } catch { /* already gone */ }
    throw new Error(message);
  };
  let token = null;
  const deadline = Date.now() + 120000;
  while (!token && Date.now() < deadline) {
    await sleep(150);
    let log = '';
    try { log = readFileSync(logFile, 'utf8'); } catch { /* not written yet */ }
    const match = log.match(/dsh web: http:\/\/[^\s/]+:\d+\/\?token=([A-Za-z0-9_-]+)/);
    if (match) { token = match[1]; break; }
    try { process.kill(child.pid, 0); } catch { failStart('slot exited during startup'); }
  }
  if (!token) failStart('slot did not print a token URL');
  const cookie = await exchangeToken(flags.host, flags.port, flags.stablePort, token).catch((error) => failStart(error.message));
  if (!cookie) failStart('token exchange returned no cookie');
  if (!(await cookieWorks(flags.host, flags.port, flags.stablePort, cookie)))
    failStart('slot did not authenticate with the entrance-authority cookie');
  writeState({name: flags.name, host: flags.host, port: flags.port, stablePort: flags.stablePort, pid: child.pid,
    startedAt: new Date().toISOString(), log: logFile, healthy: true});
  process.stdout.write(JSON.stringify({name: flags.name, host: flags.host, port: flags.port, pid: child.pid,
    stablePort: flags.stablePort, healthy: true}) + '\n');
  process.exit(0);
}

async function stop() {
  const state = readState();
  if (!state) {
    // An adopted orphan has no state file. Find the listener by its command line
    // so a host upgrade can still stop it cleanly.
    const pid = findListener(flags.port);
    if (pid > 0) {
      process.kill(pid, 'SIGTERM');
      const deadline = Date.now() + 10000;
      while (alive(pid) && Date.now() < deadline) await sleep(200);
      if (alive(pid)) process.kill(pid, 'SIGKILL');
      process.stdout.write(JSON.stringify({name: flags.name, stopped: true, adopted: true, pid}) + '\n');
      return;
    }
    throw new Error('Slot ' + flags.name + ' has no state');
  }
  if (alive(state.pid)) {
    process.kill(state.pid, 'SIGTERM');
    const deadline = Date.now() + 10000;
    while (alive(state.pid) && Date.now() < deadline) await sleep(200);
    if (alive(state.pid)) process.kill(state.pid, 'SIGKILL');
  }
  try { unlinkSync(stateFile); } catch { /* already gone */ }
  process.stdout.write(JSON.stringify({name: flags.name, stopped: true}) + '\n');
}

async function status() {
  const state = readState();
  process.stdout.write(JSON.stringify(state ? {...state, alive: alive(state.pid)} : {name: flags.name, alive: false}) + '\n');
}

async function switchSlot() {
  const state = readState();
  if (!state || !alive(state.pid)) throw new Error('Slot ' + flags.name + ' is not running');
  if (!flags.relayDir) throw new Error('--relay-dir is required for switch');
  mkdirSync(flags.relayDir, {recursive: true, mode: 0o700});
  const activeFile = path.join(flags.relayDir, 'active.json');
  const temp = activeFile + '.tmp';
  writeFileSync(temp, JSON.stringify({host: '127.0.0.1', port: state.port, generation: Date.now()}, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, activeFile);
  process.stdout.write(JSON.stringify({switched: true, active: state.port}) + '\n');
}

if (command === 'start') await start();
else if (command === 'stop') await stop();
else if (command === 'status') await status();
else if (command === 'switch') await switchSlot();
else throw new Error('Unknown command: ' + command);
