#!/usr/bin/env node
// M9.2 stable entrance. A small TCP relay that forwards the client-facing port
// to whichever slot is currently active. It holds no DSH state and terminates
// connections when the active slot changes so the client reconnects cleanly.
import {closeSync, mkdirSync, openSync, readFileSync, renameSync, unlinkSync, writeFileSync, watch} from 'node:fs';
import net from 'node:net';
import path from 'node:path';

const argv = process.argv.slice(2);
const flags = {stateDir: '', port: 0};
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  switch (value) {
    case '--port': flags.port = Number(next()); break;
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--help':
      process.stdout.write('Usage: relay.mjs --port N --state-dir DIR\n');
      process.exit(0);
      break;
    default: throw new Error('Unknown option: ' + value);
  }
}
if (!Number.isInteger(flags.port) || flags.port <= 0) throw new Error('--port is required');
if (!flags.stateDir) throw new Error('--state-dir is required');

const stateDir = flags.stateDir;
mkdirSync(stateDir, {recursive: true, mode: 0o700});
const activeFile = path.join(stateDir, 'active.json');
const lockFile = path.join(stateDir, 'relay.pid');
const statusFile = path.join(stateDir, 'relay-status.json');

function log(line) { process.stderr.write(`${new Date().toISOString()} ${line}\n`); }
function readActive() {
  try {
    const value = JSON.parse(readFileSync(activeFile, 'utf8'));
    if (!value || !Number.isInteger(value.port) || typeof value.host !== 'string') return null;
    return value;
  } catch { return null; }
}
function writeStatus(extra = {}) {
  const status = {listening: flags.port, active: active, connections: clients.size,
    switchedAt, updatedAt: new Date().toISOString(), ...extra};
  const temp = statusFile + '.tmp';
  writeFileSync(temp, JSON.stringify(status, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, statusFile);
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
  if (pid > 0) throw new Error(`Relay already running as ${pid}`);
  unlinkSync(lockFile);
  acquireLock();
}

let active = readActive();
let switchedAt = null;
const clients = new Set();
const server = net.createServer((client) => {
  if (!active) { client.destroy(); return; }
  const upstream = net.connect({host: active.host, port: active.port});
  clients.add(client);
  const closeBoth = () => { client.destroy(); upstream.destroy(); clients.delete(client); };
  upstream.on('connect', () => {
    client.pipe(upstream);
    upstream.pipe(client);
  });
  client.on('error', closeBoth);
  upstream.on('error', closeBoth);
  client.on('close', () => { clients.delete(client); upstream.destroy(); });
  upstream.on('close', () => { clients.delete(client); client.destroy(); });
});

function applyActive(reason) {
  const next = readActive();
  if (!next) { log('no active slot; keeping current ' + JSON.stringify(active)); return; }
  if (active && next.host === active.host && next.port === active.port) return;
  active = next;
  switchedAt = new Date().toISOString();
  for (const client of [...clients]) client.destroy();
  clients.clear();
  log(`switch ${reason}: ${active.host}:${active.port}`);
  writeStatus();
}

acquireLock();
server.listen(flags.port, '127.0.0.1', () => {
  log(`relay listening on 127.0.0.1:${flags.port}`);
  writeStatus();
});
server.on('error', (error) => { log('listen failed: ' + error.message); process.exit(1); });

try {
  watch(activeFile, () => applyActive('file change'));
} catch { /* watching is best-effort; polling below still applies */ }
setInterval(() => applyActive('poll'), 500);

function shutdown() {
  log('stopping');
  server.close();
  for (const client of [...clients]) client.destroy();
  try { unlinkSync(lockFile); } catch { /* already gone */ }
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
