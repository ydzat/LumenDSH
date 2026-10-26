// M9: the stack must follow the slot the relay is serving. A hardcoded A would
// drag the client back to A after a watchdog restart that follows an A→B switch.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const temp = mkdtempSync(path.join(os.homedir(), '.stack-active-'));
const stateDir = path.join(temp, 'slots');
const relayDir = path.join(temp, 'relay');
const port = 39960 + Math.floor(Math.random() * 10);
const slotPort = port + 10;

const mock = http.createServer((request, response) => { response.writeHead(200); response.end('slot b'); });
await new Promise((resolve) => mock.listen(slotPort, '127.0.0.1', resolve));
// Pretend slot B is already running; the listener answers and the state file is alive.
const {mkdirSync} = await import('node:fs');
mkdirSync(stateDir, {recursive: true});
mkdirSync(relayDir, {recursive: true});
writeFileSync(path.join(stateDir, 'B.json'), JSON.stringify({name: 'B', port: slotPort, pid: process.pid}) + '\n');
writeFileSync(path.join(relayDir, 'active.json'), JSON.stringify({host: '127.0.0.1', port: slotPort, slot: 'B'}) + '\n');

let stack = null;
try {
  stack = spawn(process.execPath, [path.join(repo, 'integrations/supervisor/stack.mjs'),
    '--port', String(port), '--slot-name', 'A', '--slot-port', '3082', '--slot-ports', `A=3082,B=${slotPort}`,
    '--runtime', '/nonexistent', '--home', temp, '--workspace', temp,
    '--state-dir', stateDir, '--relay-dir', relayDir], {stdio: ['ignore', 'pipe', 'pipe']});
  let output = '';
  stack.stdout.on('data', (chunk) => { output += String(chunk); });
  stack.stderr.on('data', (chunk) => { output += String(chunk); });
  let active = null;
  for (let attempt = 0; attempt < 50; attempt++) {
    try { active = JSON.parse(readFileSync(path.join(relayDir, 'active.json'), 'utf8')); } catch { active = null; }
    if (active && output.includes('adopting')) break;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert.equal(active?.slot, 'B', 'stack rewrote the active slot: ' + JSON.stringify(active));
  assert.equal(active?.port, slotPort);
  assert.equal(existsSync(path.join(stateDir, 'A.json')), false, 'stack must not start slot A');
  console.log('PASS stack followed the active slot B');
} finally {
  if (stack) stack.kill('SIGTERM');
  mock.close();
  rmSync(temp, {recursive: true, force: true});
}
console.log('STACK_ACTIVE_OK');
