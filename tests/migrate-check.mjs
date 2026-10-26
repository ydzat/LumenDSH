// M9.2 migration checks: replace a mock direct launcher with the relay + slot
// architecture, and prove that a failed slot start leaves the direct launcher
// untouched.
import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = process.env.DSH_TEST_RUNTIME ??
  '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime';
const sourceHome = process.env.DSH_TEST_HOME ?? path.join(os.homedir(), '.dsh');
if (!existsSync(path.join(runtime, 'node_modules/@deepseek-ai/dsh/lib/bin.js'))) {
  console.log('SKIP migrate-check: no DSH runtime at ' + runtime);
  process.exit(0);
}
const migrate = path.join(repo, 'integrations/supervisor/migrate.mjs');
const slots = path.join(repo, 'integrations/supervisor/slots.mjs');
const tempRoot = mkdtempSync(path.join(os.homedir(), '.migrate-check-'));
const copiedHome = path.join(tempRoot, 'dsh-home');
cpSync(sourceHome, copiedHome, {recursive: true});
const stateDir = path.join(tempRoot, 'state');
const relayDir = path.join(tempRoot, 'relay');
const port = 39900 + Math.floor(Math.random() * 80);
const slotPort = port + 1;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function directBackend() {
  return spawn(process.execPath, ['-e', `const http=require('http');http.createServer((q,r)=>{r.writeHead(200);r.end('direct')}).listen(${port},'127.0.0.1')`],
    {stdio: ['ignore', 'ignore', 'ignore']});
}
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
function statusOf(port) {
  return new Promise((resolve) => {
    const request = http.get({host: '127.0.0.1', port, path: '/', timeout: 2000},
      (response) => { response.resume(); resolve(response.statusCode); });
    request.on('error', () => resolve(0));
    request.on('timeout', () => { request.destroy(); resolve(0); });
  });
}
function migrateRun(extra = []) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [migrate, '--port', String(port), '--slot-name', 'M',
      '--slot-port', String(slotPort), '--runtime', runtime, '--home', copiedHome,
      '--workspace', repo, '--state-dir', stateDir, '--relay-dir', relayDir,
      '--observe-ms', '400', '--stop-command', 'kill ' + direct.pid,
      '--restore-command', 'true', ...extra], {stdio: ['ignore', 'pipe', 'pipe']});
    let out = '', err = '';
    child.stdout.on('data', (chunk) => { out += String(chunk); });
    child.stderr.on('data', (chunk) => { err += String(chunk); });
    child.on('exit', (code) => resolve({code, out, err}));
  });
}
function stopSlot() {
  spawnSync(process.execPath, [slots, 'stop', '--name', 'M', '--state-dir', stateDir], {encoding: 'utf8'});
}

let direct = null;
try {
  // Failure first: a broken runtime must fail before the direct launcher stops.
  direct = directBackend();
  await sleep(600);
  assert.equal(await statusOf(port), 200, 'direct launcher must serve before migration');
  const failed = await Promise.race([
    migrateRun(['--runtime', path.join(tempRoot, 'missing-runtime')]),
    sleep(20000).then(() => { throw new Error('migration failure case timed out'); })
  ]);
  assert.equal(failed.code, 1);
  const failedReceipt = JSON.parse(failed.out);
  assert.equal(failedReceipt.status, 'failed');
  assert.equal(failedReceipt.stoppedDirect ?? false, false);
  assert.equal(alive(direct.pid), true, 'direct launcher must survive a failed migration');
  console.log('PASS failed slot start left the direct launcher untouched');

  // Success: cut the direct launcher over to relay + slot.
  const migrated = await Promise.race([
    migrateRun(),
    sleep(60000).then(() => { throw new Error('migration success case timed out'); })
  ]);
  assert.equal(migrated.code, 0, migrated.err || migrated.out);
  const receipt = JSON.parse(migrated.out);
  assert.equal(receipt.status, 'migrated');
  assert.equal(receipt.statusCode, 200);
  assert.equal(alive(direct.pid), false, 'direct launcher must be stopped');
  const viaRelay = await statusOf(port);
  assert.equal(viaRelay, 401, 'relay must serve the slot (401 = DSH auth alive)');
  const cookieProbe = readFileSync(path.join(stateDir, 'M.json'), 'utf8');
  assert.ok(cookieProbe.includes('"port": ' + slotPort));
  console.log('PASS migrated the stable port to relay + slot with a receipt');
  const relayPid = receipt.relayPid;
  try { process.kill(relayPid, 'SIGTERM'); } catch { /* already gone */ }
} finally {
  try { stopSlot(); } catch { /* ignore */ }
  if (direct) { try { direct.kill('SIGKILL'); } catch { /* gone */ } }
  rmSync(tempRoot, {recursive: true, force: true});
}
console.log('MIGRATE_OK');
