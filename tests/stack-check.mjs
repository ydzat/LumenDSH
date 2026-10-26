// M9 stack checks: watchdog + stack restart the whole entrance when the slot
// dies. Uses a copied DSH_HOME and scratch ports; the live stack is untouched.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {cpSync, existsSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = process.env.DSH_TEST_RUNTIME ??
  '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime';
const sourceHome = process.env.DSH_TEST_HOME ?? path.join(os.homedir(), '.dsh');
if (!existsSync(path.join(runtime, 'node_modules/@deepseek-ai/dsh/lib/bin.js'))) {
  console.log('SKIP stack-check: no DSH runtime at ' + runtime);
  process.exit(0);
}
const supervisor = path.join(repo, 'integrations/supervisor/supervise.mjs');
const stack = path.join(repo, 'integrations/supervisor/stack.mjs');
const tempRoot = mkdtempSync(path.join(os.homedir(), '.stack-check-'));
const copiedHome = path.join(tempRoot, 'dsh-home');
cpSync(sourceHome, copiedHome, {recursive: true});
const stateDir = path.join(tempRoot, 'state');
const relayDir = path.join(tempRoot, 'relay');
const port = 39950 + Math.floor(Math.random() * 40);
const slotPort = port + 1;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function probe() {
  return new Promise((resolve) => {
    const request = http.get({host: '127.0.0.1', port, path: '/', timeout: 1500},
      (response) => { response.resume(); resolve(response.statusCode); });
    request.on('error', () => resolve(0));
    request.on('timeout', () => { request.destroy(); resolve(0); });
  });
}
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
function slotPid() {
  try { return JSON.parse(readFileSync(path.join(stateDir, 'S.json'), 'utf8')).pid; } catch { return null; }
}

const supervise = spawn(process.execPath, [supervisor, '--port', String(port),
  '--interval-ms', '300', '--timeout-ms', '300', '--fail-threshold', '2',
  '--backoff-ms', '200', '--max-backoff-ms', '500', '--startup-grace-ms', '20000', '--state-dir', stateDir,
  '--', process.execPath, stack, '--port', String(port), '--slot-name', 'S', '--slot-port', String(slotPort),
  '--runtime', runtime, '--home', copiedHome, '--workspace', repo,
  '--state-dir', stateDir, '--relay-dir', relayDir], {stdio: ['ignore', 'ignore', 'pipe']});
let superviseStderr = '';
supervise.stderr.on('data', (chunk) => { superviseStderr += String(chunk); });

try {
  const deadline = Date.now() + 40000;
  while (Date.now() < deadline && (await probe()) !== 401) await sleep(200);
  assert.equal(await probe(), 401, 'relay + slot must answer through the entrance');
  const firstPid = slotPid();
  assert.ok(firstPid && alive(firstPid), 'slot must be running');
  console.log('PASS stack served the entrance with slot ' + firstPid);

  process.kill(firstPid, 'SIGKILL');
  const restarted = Date.now() + 40000;
  let current = null;
  while (Date.now() < restarted) {
    current = slotPid();
    if (current && current !== firstPid && alive(current) && (await probe()) === 401) break;
    await sleep(300);
  }
  assert.ok(current && current !== firstPid, 'a new slot must be started');
  assert.equal(await probe(), 401, 'entrance must recover');
  console.log('PASS watchdog restarted the stack with slot ' + current);
} finally {
  supervise.kill('SIGTERM');
  await new Promise((resolve) => supervise.once('exit', resolve));
  try { const state = JSON.parse(readFileSync(path.join(stateDir, 'S.json'), 'utf8')); process.kill(state.pid, 'SIGKILL'); } catch { /* already gone */ }
  rmSync(tempRoot, {recursive: true, force: true});
}
if (superviseStderr.includes('stack: relay exited')) console.log('note: relay restart observed');
console.log('STACK_OK');
