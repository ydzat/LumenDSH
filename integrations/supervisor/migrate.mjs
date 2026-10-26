#!/usr/bin/env node
// M9.2 one-time migration: replace the direct launcher on the stable port with
// the relay + slot architecture. Intended to run detached (setsid/nohup) because
// the cutover drops the current client connection. On failure it restores the
// original launcher command.
import {spawn, spawnSync} from 'node:child_process';
import {closeSync, mkdirSync, openSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import http from 'node:http';
import net from 'node:net';
import path from 'node:path';

const argv = process.argv.slice(2);
const flags = {observeMs: 3000, stateDir: path.join(process.env.HOME ?? '.', '.local/state/sliver-wolf-slots'),
  relayDir: path.join(process.env.HOME ?? '.', '.local/state/sliver-wolf-supervisor')};
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  switch (value) {
    case '--port': flags.port = Number(next()); break;
    case '--slot-name': flags.slotName = next(); break;
    case '--slot-port': flags.slotPort = Number(next()); break;
    case '--runtime': flags.runtime = path.resolve(next()); break;
    case '--home': flags.home = path.resolve(next()); break;
    case '--workspace': flags.workspace = path.resolve(next()); break;
    case '--stop-command': flags.stopCommand = next(); break;
    case '--restore-command': flags.restoreCommand = next(); break;
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--relay-dir': flags.relayDir = path.resolve(next()); break;
    case '--observe-ms': flags.observeMs = Number(next()); break;
    case '--receipt-dir': flags.receiptDir = path.resolve(next()); break;
    case '--help':
      process.stdout.write('Usage: migrate.mjs --port N --slot-name A --slot-port N --runtime R --home H\n' +
        '  --workspace W --stop-command CMD [--restore-command CMD] [--state-dir DIR] [--relay-dir DIR]\n');
      process.exit(0);
      break;
    default: throw new Error('Unknown option: ' + value);
  }
}
for (const required of ['port', 'slotName', 'slotPort', 'runtime', 'home', 'workspace', 'stopCommand'])
  if (!flags[required]) throw new Error('--' + required.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()) + ' is required');
const receiptDir = flags.receiptDir ?? path.join(flags.stateDir, 'receipts');
mkdirSync(flags.relayDir, {recursive: true, mode: 0o700});
mkdirSync(receiptDir, {recursive: true, mode: 0o700});
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const slots = path.join(repo, 'integrations/supervisor/slots.mjs');
const relayScript = path.join(repo, 'integrations/supervisor/relay.mjs');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function run(command) {
  const result = spawnSync('bash', ['-c', command], {encoding: 'utf8', timeout: 60000});
  if (result.status !== 0) throw new Error('command failed: ' + command + ' ' + (result.stderr ?? ''));
  return result.stdout;
}
function slotState() {
  try { return JSON.parse(readFileSync(path.join(flags.stateDir, flags.slotName + '.json'), 'utf8')); }
  catch { return null; }
}
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
function waitPort(port, timeoutMs) {
  return new Promise((resolve) => {
    const deadline = Date.now() + timeoutMs;
    const attempt = () => {
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => { socket.destroy(); resolve(true); });
      socket.once('error', () => {
        socket.destroy();
        if (Date.now() > deadline) resolve(false); else setTimeout(attempt, 150);
      });
    };
    attempt();
  });
}
function httpStatus(port, cookie) {
  return new Promise((resolve) => {
    const request = http.get({host: '127.0.0.1', port, path: '/', timeout: 3000,
      headers: {host: '127.0.0.1:' + port, ...(cookie ? {cookie} : {})}},
      (response) => { response.resume(); resolve(response.statusCode); });
    request.on('error', () => resolve(0));
    request.on('timeout', () => { request.destroy(); resolve(0); });
  });
}
async function mintCookie(port) {
  const state = slotState();
  const log = readFileSync(state.log, 'utf8');
  const match = log.match(/dsh web: http:\/\/127\.0\.0\.1:\d+\/\?token=([A-Za-z0-9_-]+)/);
  if (!match) throw new Error('slot log has no token URL');
  return new Promise((resolve, reject) => {
    const request = http.request({host: '127.0.0.1', port, path: '/?token=' + encodeURIComponent(match[1]),
      headers: {host: '127.0.0.1:' + port}, timeout: 3000}, (response) => {
      const cookie = response.headers['set-cookie']?.[0]?.split(';')[0] ?? null;
      response.resume();
      response.on('end', () => cookie ? resolve(cookie) : reject(new Error('no cookie')));
    });
    request.on('error', reject);
    request.on('timeout', () => { request.destroy(); reject(new Error('token exchange timeout')); });
    request.end();
  });
}
function receipt(value) {
  const file = path.join(receiptDir, new Date().toISOString().replace(/[:.]/g, '-') + '-migrate.json');
  writeFileSync(file, JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  return file;
}

let relay = null;
const record = {schemaVersion: 1, port: flags.port, slot: flags.slotName, slotPort: flags.slotPort,
  startedAt: new Date().toISOString()};
try {
  const start = spawnSync(process.execPath, [slots, 'start', '--name', flags.slotName, '--port', String(flags.slotPort),
    '--runtime', flags.runtime, '--home', flags.home, '--workspace', flags.workspace,
    '--stable-port', String(flags.port), '--state-dir', flags.stateDir], {encoding: 'utf8', timeout: 60000});
  if (start.status !== 0) throw new Error('slot start failed: ' + (start.stderr || start.stdout));
  record.slotPid = slotState().pid;

  const activeFile = path.join(flags.relayDir, 'active.json');
  const temp = activeFile + '.tmp';
  writeFileSync(temp, JSON.stringify({host: '127.0.0.1', port: flags.slotPort, generation: Date.now(),
    slot: flags.slotName}, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, activeFile);

  run(flags.stopCommand);
  record.stoppedDirect = true;

  const relayLog = path.join(flags.stateDir, 'relay.log');
  const fd = openSync(relayLog, 'a', 0o600);
  relay = spawn(process.execPath, [relayScript, '--port', String(flags.port), '--state-dir', flags.relayDir],
    {detached: true, stdio: ['ignore', fd, fd]});
  closeSync(fd);
  relay.unref();
  record.relayPid = relay.pid;

  if (!(await waitPort(flags.port, 15000))) throw new Error('relay did not listen');
  await sleep(flags.observeMs);
  const cookie = await mintCookie(flags.port);
  const status = await httpStatus(flags.port, cookie);
  record.statusCode = status;
  if (status !== 200) throw new Error('relay did not serve the slot (HTTP ' + status + ')');
  record.status = 'migrated';
} catch (error) {
  record.error = String(error.message);
  record.status = 'failed';
  if (record.stoppedDirect) {
    try { if (relay) process.kill(relay.pid, 'SIGKILL'); } catch { /* already gone */ }
    if (flags.restoreCommand) {
      try { run(flags.restoreCommand); record.restored = true; } catch (restoreError) { record.restoreError = String(restoreError.message); }
    }
  }
}
record.finishedAt = new Date().toISOString();
record.evidence = receipt(record);
process.stdout.write(JSON.stringify(record) + '\n');
process.exit(record.status === 'migrated' ? 0 : 1);
