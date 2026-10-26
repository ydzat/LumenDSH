#!/usr/bin/env node
// M9 stack entry: ensure the active slot is running, then run the relay in the
// foreground. A watchdog supervises this process and health-checks the stable
// port, so either the relay or the slot failing leads to a full stack restart.
import {spawn, spawnSync} from 'node:child_process';
import {mkdirSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const argv = process.argv.slice(2);
const flags = {slotName: 'A'};
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
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--relay-dir': flags.relayDir = path.resolve(next()); break;
    case '--help':
      process.stdout.write('Usage: stack.mjs --port N --slot-name A --slot-port N --runtime R\n' +
        '  --home H --workspace W --state-dir S --relay-dir D\n');
      process.exit(0);
      break;
    default: throw new Error('Unknown option: ' + value);
  }
}
for (const required of ['port', 'slotPort', 'runtime', 'home', 'workspace', 'stateDir', 'relayDir'])
  if (!flags[required]) throw new Error('--' + required.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()) + ' is required');

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const slots = path.join(repo, 'integrations/supervisor/slots.mjs');
const relayScript = path.join(repo, 'integrations/supervisor/relay.mjs');

function slotState() {
  try { return JSON.parse(readFileSync(path.join(flags.stateDir, flags.slotName + '.json'), 'utf8')); }
  catch { return null; }
}
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
function answers(port) {
  return new Promise((resolve) => {
    const request = http.get({host: '127.0.0.1', port, path: '/', timeout: 1500},
      (response) => { response.resume(); resolve(true); });
    request.on('error', () => resolve(false));
    request.on('timeout', () => { request.destroy(); resolve(false); });
  });
}

let slot = slotState();
if (!slot || !alive(slot.pid)) {
  // An orphaned DSH listener may already own the slot port (for example after an
  // interrupted activation). Adopt it instead of failing the whole stack in a
  // restart loop; the relay still owns the stable entrance.
  if (await answers(flags.slotPort)) {
    process.stderr.write('stack: adopting existing listener on ' + flags.slotPort + '\n');
  } else {
    const started = spawnSync(process.execPath, [slots, 'start', '--name', flags.slotName,
      '--port', String(flags.slotPort), '--runtime', flags.runtime, '--home', flags.home,
      '--workspace', flags.workspace, '--stable-port', String(flags.port),
      '--state-dir', flags.stateDir], {encoding: 'utf8', timeout: 60000});
    if (started.status !== 0) {
      process.stderr.write('stack: slot start failed\n' + (started.stderr || started.stdout));
      process.exit(1);
    }
  }
}

// The relay follows active.json; the stack owns that target even when it was
// first written by a migration.
mkdirSync(flags.relayDir, {recursive: true, mode: 0o700});
const activeFile = path.join(flags.relayDir, 'active.json');
const temp = activeFile + '.tmp';
writeFileSync(temp, JSON.stringify({host: '127.0.0.1', port: flags.slotPort,
  generation: Date.now(), slot: flags.slotName}, null, 2) + '\n', {mode: 0o600});
renameSync(temp, activeFile);

const relay = spawn(process.execPath, [relayScript, '--port', String(flags.port), '--state-dir', flags.relayDir],
  {stdio: 'inherit'});
relay.on('exit', (code, signal) => {
  process.stderr.write(`stack: relay exited code=${code} signal=${signal}\n`);
  process.exit(code ?? 1);
});
function shutdown() {
  relay.kill('SIGTERM');
  setTimeout(() => process.exit(0), 1000);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
