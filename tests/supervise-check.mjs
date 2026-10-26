// M9.1 supervisor checks: crash restart, healthy steady state, unhealthy child
// replacement and the exclusive lock. Uses mock children only; the live DSH
// launcher is never started or stopped here.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, readFileSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const supervisor = path.join(repo, 'integrations/supervisor/supervise.mjs');
const mockChild = path.join(repo, 'tests/supervise-child.mjs');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function waitFor(predicate, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await predicate();
    if (value) return value;
    await sleep(100);
  }
  throw new Error('Timeout waiting for ' + label);
}
function readStatus(directory) {
  const file = path.join(directory, 'status.json');
  if (!existsSync(file)) return null;
  try { return JSON.parse(readFileSync(file, 'utf8')); } catch { return null; }
}
function spawnSupervisor(port, directory, mode) {
  return spawn(process.execPath, [supervisor,
    '--port', String(port),
    '--interval-ms', '200',
    '--timeout-ms', '200',
    '--fail-threshold', '2',
    '--backoff-ms', '150',
    '--max-backoff-ms', '400',
    '--max-restarts', '5',
    '--state-dir', directory,
    '--', process.execPath, mockChild, mode, String(port)],
    {stdio: ['ignore', 'ignore', 'pipe']});
}
async function stop(processHandle) {
  if (processHandle.exitCode !== null) return;
  processHandle.kill('SIGTERM');
  await new Promise((resolve) => processHandle.once('exit', resolve));
}

async function caseCrash() {
  const port = 39100 + Math.floor(Math.random() * 500);
  const directory = mkdtempSync(path.join(os.tmpdir(), 'supervise-crash-'));
  const handle = spawnSupervisor(port, directory, 'crash');
  try {
    const status = await waitFor(() => {
      const current = readStatus(directory);
      return current && current.restarts >= 2 ? current : null;
    }, 15000, 'two crash restarts');
    assert.ok(['restarting', 'starting', 'running'].includes(status.state));
    console.log('PASS crash restart: restarts=' + status.restarts);
  } finally {
    await stop(handle);
    assert.equal(existsSync(path.join(directory, 'supervisor.pid')), false, 'lock released');
  }
}

async function caseHealthy() {
  const port = 39200 + Math.floor(Math.random() * 500);
  const directory = mkdtempSync(path.join(os.tmpdir(), 'supervise-ok-'));
  const handle = spawnSupervisor(port, directory, 'ok');
  try {
    const status = await waitFor(() => {
      const current = readStatus(directory);
      return current && current.lastHealth === 'healthy' ? current : null;
    }, 15000, 'healthy probe');
    assert.equal(status.restarts, 0);
    assert.equal(status.consecutiveFailures, 0);
    console.log('PASS healthy steady state: restarts=0');
  } finally {
    await stop(handle);
    assert.equal(existsSync(path.join(directory, 'supervisor.pid')), false, 'lock released');
  }
}

async function caseUnhealthy() {
  const port = 39300 + Math.floor(Math.random() * 500);
  const directory = mkdtempSync(path.join(os.tmpdir(), 'supervise-hang-'));
  const handle = spawnSupervisor(port, directory, 'hang');
  try {
    const status = await waitFor(() => {
      const current = readStatus(directory);
      return current && current.restarts >= 1 ? current : null;
    }, 15000, 'unhealthy restart');
    assert.ok(['restarting', 'running', 'starting'].includes(status.state));
    console.log('PASS unhealthy child replaced: restarts=' + status.restarts);
  } finally {
    await stop(handle);
    assert.equal(existsSync(path.join(directory, 'supervisor.pid')), false, 'lock released');
  }
}

async function caseExclusiveLock() {
  const port = 39400 + Math.floor(Math.random() * 500);
  const directory = mkdtempSync(path.join(os.tmpdir(), 'supervise-lock-'));
  const first = spawnSupervisor(port, directory, 'ok');
  try {
    await waitFor(() => {
      const current = readStatus(directory);
      return current && current.lastHealth === 'healthy' ? current : null;
    }, 15000, 'first supervisor healthy');
    const second = spawn(process.execPath, [supervisor,
      '--port', String(port + 1), '--state-dir', directory,
      '--', process.execPath, mockChild, 'ok', String(port + 1)],
      {stdio: ['ignore', 'ignore', 'pipe']});
    let stderr = '';
    second.stderr.on('data', (chunk) => { stderr += String(chunk); });
    const code = await new Promise((resolve) => second.once('exit', resolve));
    assert.notEqual(code, 0, 'second supervisor must refuse to start');
    assert.match(stderr, /already running/);
    console.log('PASS exclusive lock refused the second supervisor');
  } finally {
    await stop(first);
  }
}

await caseCrash();
await caseHealthy();
await caseUnhealthy();
await caseExclusiveLock();
console.log('SUPERVISE_OK');
