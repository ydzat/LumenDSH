// M9.2 slot checks: start a real DSH web slot over a copied DSH_HOME, mint an
// entrance-authority cookie, verify it, switch the relay target and stop. Uses a
// temporary home copy so the live instance is untouched.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {cpSync, existsSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const script = path.join(repo, 'integrations/supervisor/slots.mjs');
const runtime = process.env.DSH_TEST_RUNTIME ??
  '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime';
const home = process.env.DSH_TEST_HOME ?? path.join(os.homedir(), '.dsh');
const bin = path.join(runtime, 'node_modules/@deepseek-ai/dsh/lib/bin.js');

if (!existsSync(bin)) {
  console.log('SKIP slots-check: no DSH runtime at ' + runtime);
  process.exit(0);
}

const tempRoot = mkdtempSync(path.join(os.homedir(), '.slots-check-'));
const copiedHome = path.join(tempRoot, 'dsh-home');
cpSync(home, copiedHome, {recursive: true});
const stateDir = path.join(tempRoot, 'state');
const relayDir = path.join(tempRoot, 'relay');
const port = 39700 + Math.floor(Math.random() * 200);

function run(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [script, ...args], {stdio: ['ignore', 'pipe', 'pipe']});
    let out = '', err = '';
    child.stdout.on('data', (chunk) => { out += String(chunk); });
    child.stderr.on('data', (chunk) => { err += String(chunk); });
    child.on('exit', (code) => code === 0 ? resolve(out) : reject(new Error(err || 'exit ' + code)));
  });
}

try {
  const started = JSON.parse(await run(['start', '--name', 'B', '--port', String(port),
    '--runtime', runtime, '--home', copiedHome, '--workspace', repo,
    '--stable-port', String(port + 1), '--state-dir', stateDir]));
  assert.equal(started.healthy, true);
  assert.equal(started.port, port);
  console.log('PASS slot started with entrance-authority cookie');

  const status = JSON.parse(await run(['status', '--name', 'B', '--state-dir', stateDir]));
  assert.equal(status.alive, true);
  console.log('PASS slot status alive');

  await run(['switch', '--name', 'B', '--state-dir', stateDir, '--relay-dir', relayDir]);
  const active = JSON.parse(readFileSync(path.join(relayDir, 'active.json'), 'utf8'));
  assert.equal(active.port, port);
  console.log('PASS switch wrote relay target');

  const stopped = JSON.parse(await run(['stop', '--name', 'B', '--state-dir', stateDir]));
  assert.equal(stopped.stopped, true);
  assert.equal(existsSync(path.join(stateDir, 'B.json')), false);
  console.log('PASS slot stopped and state removed');
} finally {
  rmSync(tempRoot, {recursive: true, force: true});
}
console.log('SLOTS_OK');
