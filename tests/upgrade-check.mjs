// M9.3 upgrade orchestration checks: real slots over a copied DSH_HOME, a relay
// on a scratch port, a switch receipt, a rollback cycle, and a failed target
// that rolls the relay back. The live instance and port 3080 are untouched.
import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from "node:fs";
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = process.env.DSH_TEST_RUNTIME ??
  '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime';
const sourceHome = process.env.DSH_TEST_HOME ?? path.join(os.homedir(), '.dsh');
const bin = path.join(runtime, 'node_modules/@deepseek-ai/dsh/lib/bin.js');
if (!existsSync(bin)) { console.log('SKIP upgrade-check: no DSH runtime at ' + runtime); process.exit(0); }

const slotsScript = path.join(repo, 'integrations/supervisor/slots.mjs');
const relayScript = path.join(repo, 'integrations/supervisor/relay.mjs');
const upgradeScript = path.join(repo, 'integrations/supervisor/upgrade.mjs');
const tempRoot = mkdtempSync(path.join(os.homedir(), '.upgrade-check-'));
const copiedHome = path.join(tempRoot, 'dsh-home');
cpSync(sourceHome, copiedHome, {recursive: true});
const stateDir = path.join(tempRoot, 'state');
const relayDir = path.join(tempRoot, 'relay');
const relayPort = 39800 + Math.floor(Math.random() * 100);
const portA = relayPort + 1, portB = relayPort + 2, portC = relayPort + 3;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function run(script, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [script, ...args], {stdio: ['ignore', 'pipe', 'pipe']});
    let out = '', err = '';
    child.stdout.on('data', (chunk) => { out += String(chunk); });
    child.stderr.on('data', (chunk) => { err += String(chunk); });
    child.on('exit', (code) => code === 0 ? resolve(out) : reject(new Error(err || 'exit ' + code)));
  });
}
function slotArgs(name, port, extra = []) {
  return ['--name', name, '--port', String(port), '--runtime', runtime, '--home', copiedHome,
    '--workspace', repo, '--stable-port', String(relayPort), '--state-dir', stateDir, ...extra];
}
function startSlot(name, port) { return run(slotsScript, ['start', ...slotArgs(name, port)]); }
function stopSlot(name) { return run(slotsScript, ['stop', '--name', name, '--state-dir', stateDir]); }
function upgrade(args) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [upgradeScript, '--state-dir', stateDir, '--relay-dir', relayDir, ...args],
      {stdio: ['ignore', 'pipe', 'pipe']});
    let out = '', err = '';
    child.stdout.on('data', (chunk) => { out += String(chunk); });
    child.stderr.on('data', (chunk) => { err += String(chunk); });
    child.on('exit', (code) => resolve({code, out, err}));
  });
}
function waitPort(port) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + 10000;
    const attempt = () => {
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => { socket.destroy(); resolve(); });
      socket.once('error', () => { socket.destroy(); Date.now() > deadline ? reject(new Error('relay did not listen')) : setTimeout(attempt, 100); });
    };
    attempt();
  });
}

let relay = null;
try {
  await startSlot('A', portA);
  await startSlot('B', portB);
  await startSlot('C', portC);
  mkdirSync(relayDir, {recursive: true, mode: 0o700});
  const activeFile = path.join(relayDir, 'active.json');
  writeFileSync(activeFile, JSON.stringify({host: '127.0.0.1', port: portA, generation: Date.now()}));
  relay = spawn(process.execPath, [relayScript, '--port', String(relayPort), '--state-dir', relayDir],
    {stdio: ['ignore', 'ignore', 'ignore']});
  await waitPort(relayPort);

  const first = await upgrade(['--from', 'A', '--to', 'B', '--observe-ms', '800']);
  assert.equal(first.code, 0, first.err);
  const firstReceipt = JSON.parse(first.out);
  assert.equal(firstReceipt.status, 'switched');
  assert.equal(firstReceipt.observed.relayPort, portB);
  assert.ok(existsSync(firstReceipt.evidence));
  console.log('PASS upgrade switched A→B with receipt');

  const back = await upgrade(['--from', 'B', '--to', 'A', '--observe-ms', '800']);
  const backReceipt = JSON.parse(back.out);
  assert.equal(backReceipt.status, 'switched');
  assert.equal(backReceipt.observed.relayPort, portA);
  console.log('PASS rollback cycle switched B→A');

  const failing = upgrade(['--from', 'A', '--to', 'B', '--observe-ms', '1500', '--rollback-on-fail', '--rollback-to', 'C']);
  await sleep(500);
  const stateB = JSON.parse(readFileSync(path.join(stateDir, 'B.json'), 'utf8'));
  process.kill(stateB.pid, 'SIGKILL');
  const failure = await failing;
  assert.equal(failure.code, 1);
  const failureReceipt = JSON.parse(failure.out);
  assert.equal(failureReceipt.status, 'rolled-back');
  assert.equal(failureReceipt.rolledBackTo, 'C');
  assert.equal(failureReceipt.rolledBackPort, portC);
  console.log('PASS failed target rolled the relay back to slot C');
} finally {
  if (relay) relay.kill('SIGTERM');
  for (const name of ['A', 'B', 'C']) { try { await stopSlot(name); } catch { /* already gone */ } }
  rmSync(tempRoot, {recursive: true, force: true});
}
console.log('UPGRADE_OK');
