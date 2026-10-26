#!/usr/bin/env node
// Slot-aware companion host activation. The live slot keeps serving while the
// new host is installed and started in the other slot; the relay switches only
// after the target verifies, and the previous slot stays as the rollback point.
// On any failure the profile is restored and the relay is pointed back.
import {copyFile, mkdir, readFile, rename, unlink, writeFile} from 'node:fs/promises';
import {existsSync, readFileSync} from 'node:fs';
import http from 'node:http';
import {execFileSync} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const home = process.env.HOME;
const root = path.join(home, 'sliver-wolf-companion');
const source = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const flags = {slotName: null, slotPort: null, target: null, targetPort: null,
  stateDir: path.join(home, '.local/state/sliver-wolf-slots'),
  relayDir: path.join(home, '.local/state/sliver-wolf-supervisor'),
  supervisorState: path.join(home, '.local/state/sliver-wolf-stack'),
  port: 3080, soakMs: 60000, stopSource: true};
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  if (value === '--slot-name') flags.slotName = next();
  else if (value === '--slot-port') flags.slotPort = Number(next());
  else if (value === '--target') flags.target = next();
  else if (value === '--target-port') flags.targetPort = Number(next());
  else if (value === '--state-dir') flags.stateDir = path.resolve(next());
  else if (value === '--relay-dir') flags.relayDir = path.resolve(next());
  else if (value === '--supervisor-state') flags.supervisorState = path.resolve(next());
  else if (value === '--port') flags.port = Number(next());
  else if (value === '--soak-ms') flags.soakMs = Number(next());
  else if (value === '--stop-source') flags.stopSource = true;
  else if (value === '--keep-source') flags.stopSource = false;
  else throw new Error('Unknown option: ' + value);
}
const slots = path.join(path.resolve(source, '../..'), 'integrations/supervisor/slots.mjs');
const files = ['package.json', 'cordis.patch.yml', 'host.mjs', 'policy.mjs', 'model-config.mjs',
  'documents.mjs', 'document-index-worker.mjs', 'situation.mjs', 'self-notes.mjs',
  'experience.mjs', 'candidates.mjs', 'growth.mjs', 'self-doc.mjs', 'extend-self.mjs',
  'upstream/night-plan.mjs', 'upstream/NOTICE.md',
  'upstream/LICENSE-dsh-bili-whale', 'upstream/LICENSE-dsh-mnemon'];
const hash = createHash('sha256');
for (const file of files) { hash.update(file); hash.update(await readFile(path.join(source, file))); }
const revision = hash.digest('hex');
const candidate = path.join(root, 'host-' + revision);
for (const file of files) {
  await mkdir(path.dirname(path.join(candidate, file)), {recursive: true, mode: 0o700});
  await copyFile(path.join(source, file), path.join(candidate, file));
}

const connectionFile = path.join(home, '.local/state/sliver-wolf-backend/connection-3080.json');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const defaultPort = (name) => (name === 'B' ? 3083 : 3082);

async function cookie() { return JSON.parse(await readFile(connectionFile, 'utf8')).cookie; }
async function entranceReady(host, port, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://${host}:${port}/`, {signal: AbortSignal.timeout(2000)});
      if (response.status === 200 || response.status === 401) return response.status;
    } catch { /* not ready yet */ }
    await sleep(1000);
  }
  return 0;
}
// Slot listeners authenticate against the authority in the Host header. The
// client's cookie was minted for the stable entrance, so direct slot queries must
// present that same Host or they get "unauthorized".
function request(host, port, {method = 'GET', pathName = '/', body = null, cookieValue = null, timeout = 5000} = {}) {
  return new Promise((resolve, reject) => {
    const payload = body === null ? null : JSON.stringify(body);
    const request = http.request({host, port, path: pathName, method,
      headers: {Host: '127.0.0.1:' + flags.port, ...(cookieValue ? {Cookie: cookieValue} : {}),
        ...(payload ? {'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload)} : {})},
      timeout}, (response) => {
      let text = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { text += chunk; });
      response.on('end', () => resolve({status: response.statusCode ?? 0, text}));
    });
    request.on('timeout', () => { request.destroy(new Error('timeout')); });
    request.on('error', reject);
    if (payload) request.write(payload);
    request.end();
  });
}
async function post(host, port, method) {
  const response = await request(host, port, {method: 'POST', pathName: `/api/${method}`,
    cookieValue: await cookie(), body: {type: 'client-request', rpcId: randomUUID(), method,
      payload: {args: {_request: {}}}}, timeout: 5000});
  if (response.status !== 200) throw new Error(`session/list ${response.status}: ${response.text.slice(0, 80)}`);
  return JSON.parse(response.text);
}
async function sessionIds(host, port) {
  const body = await post(host, port, 'session/list');
  assert(body.result.ok, 'session/list failed on ' + port);
  return body.result.value.items.map((item) => item.sessionId).sort();
}
async function companionStatus(host, port) {
  const response = await request(host, port, {pathName: '/sliver-wolf/companion/status',
    cookieValue: await cookie(), timeout: 4000});
  return response.status;
}
function readJson(file) {
  try { return JSON.parse(readFileSync(file, 'utf8')); } catch { return null; }
}
function readSlotState(name) { return readJson(path.join(flags.stateDir, name + '.json')); }
function readActive() { return readJson(path.join(flags.relayDir, 'active.json')); }
async function pointRelay(name, host, port) {
  await mkdir(flags.relayDir, {recursive: true, mode: 0o700});
  const file = path.join(flags.relayDir, 'active.json');
  await writeFile(file + '.tmp', JSON.stringify({host, port, generation: Date.now(), slot: name}, null, 2) + '\n', {mode: 0o600});
  await rename(file + '.tmp', file);
}
function startSlot(name, host, port) {
  execFileSync(process.execPath, [slots, 'start', '--name', name, '--host', host, '--port', String(port),
    '--runtime', '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime',
    '--home', path.join(home, '.dsh'), '--workspace', path.join(home, 'agent-workspace'),
    '--stable-port', String(flags.port), '--state-dir', flags.stateDir], {stdio: 'inherit'});
}
function stopSlot(name, port) {
  execFileSync(process.execPath, [slots, 'stop', '--name', name, '--port', String(port),
    '--state-dir', flags.stateDir], {stdio: 'inherit'});
}

const active = readActive();
const currentName = flags.slotName ?? active?.slot ?? 'A';
const currentState = readSlotState(currentName);
const currentPort = flags.slotPort ?? (currentState?.port ?? active?.port ?? defaultPort(currentName));
const currentHost = currentState?.host ?? active?.host ?? '127.0.0.2';
const targetName = flags.target ?? (currentName === 'A' ? 'B' : 'A');
const targetPort = flags.targetPort ?? (readSlotState(targetName)?.port ?? defaultPort(targetName));
const targetHost = readSlotState(targetName)?.host ?? '127.0.0.2';
assert.notEqual(targetName, currentName, 'Target slot must differ from the live slot');
const currentStatus = await entranceReady(currentHost, currentPort, 10000);
assert(currentStatus === 200 || currentStatus === 401, 'Live slot ' + currentName + ' is not serving on ' + currentPort);

const before = await sessionIds(currentHost, currentPort);
assert(before.length > 0, 'No sessions to preserve');
const profile = path.join(home, '.dsh/profiles/web');
const backup = path.join(root, 'host-slot-backup-' + Date.now());
await mkdir(backup, {mode: 0o700});
const retained = ['package.json', 'cordis.patch.yml', 'pnpm-lock.yaml'];
for (const file of retained) await copyFile(path.join(profile, file), path.join(backup, file));

const holdFile = path.join(flags.supervisorState, 'hold');
await mkdir(flags.supervisorState, {recursive: true, mode: 0o700});
await writeFile(holdFile, String(process.pid), {mode: 0o600});
let targetStarted = false;
let switched = false;
const startedAt = new Date().toISOString();
const receipt = {schemaVersion: 1, revision, from: currentName, to: targetName,
  fromPort: currentPort, toPort: targetPort, startedAt, soakMs: flags.soakMs, status: 'starting'};

async function verifyTarget() {
  const status = await entranceReady(targetHost, targetPort, 30000);
  assert(status === 200 || status === 401, 'Target slot did not answer on ' + targetHost + ':' + targetPort);
  assert.equal(await companionStatus(targetHost, targetPort), 200, 'Companion status missing on the target slot');
  assert.deepEqual(await sessionIds(targetHost, targetPort), before, 'Session set changed on the target slot');
  assert(existsSync(path.join(root, 'behavior', 'self-doc.md')), 'Self document was not generated');
}
async function verifyLive() {
  const status = await entranceReady('127.0.0.1', flags.port, 30000);
  assert(status === 200 || status === 401, 'Stable entrance did not answer');
  assert.equal(await companionStatus('127.0.0.1', flags.port), 200, 'Companion status missing through the relay');
  assert.deepEqual(await sessionIds('127.0.0.1', flags.port), before, 'Session set changed through the relay');
}

try {
  // Keep the source slot serving; install the host into the shared profile and
  // start the target slot on the new revision.
  execFileSync('dsh', ['plugin', '--profile', 'web', 'add', 'link:' + candidate, '--ignore-scripts'],
    {stdio: 'inherit'});
  const patchFile = path.join(profile, 'cordis.patch.yml');
  let patch = await readFile(patchFile, 'utf8');
  if (patch.includes('    timeoutMs: 10000\n')) {
    await writeFile(patchFile, patch.replace('    timeoutMs: 10000\n', '    timeoutMs: 120000\n'), {mode: 0o600});
    patch = await readFile(patchFile, 'utf8');
  }
  assert(patch.includes('    timeoutMs: 120000\n'), 'Unexpected Mnemon source deadline configuration');
  if (!patch.includes('disabled: true\n  name: dsh-mnemon-source-documents')) {
    await writeFile(patchFile, patch.replace('- id: mnemon-source-documents\n  name: dsh-mnemon-source-documents',
      '- id: mnemon-source-documents\n  disabled: true\n  name: dsh-mnemon-source-documents'), {mode: 0o600});
  }
  try { stopSlot(targetName, targetPort); } catch { /* target was not running */ }
  startSlot(targetName, targetHost, targetPort);
  targetStarted = true;
  await verifyTarget();
  await pointRelay(targetName, targetHost, targetPort);
  switched = true;
  await verifyLive();
  receipt.verified = {target: true, relay: true, sessions: before.length};
  const soakEnd = Date.now() + flags.soakMs;
  while (Date.now() < soakEnd) {
    await sleep(Math.min(2000, Math.max(0, soakEnd - Date.now())));
    const status = await companionStatus('127.0.0.1', flags.port);
    if (status !== 200) throw new Error('Companion status degraded during soak: ' + status);
  }
  receipt.status = 'active';
  receipt.completedAt = new Date().toISOString();
  if (flags.stopSource) {
    stopSlot(currentName, currentPort);
    receipt.sourceStopped = true;
  } else {
    receipt.sourceRetained = true;
  }
  await writeFile(path.join(root, 'host-activation-slot.json'), JSON.stringify(receipt, null, 2) + '\n', {mode: 0o600});
  await mkdir(path.join(flags.stateDir, 'receipts'), {recursive: true, mode: 0o700});
  await writeFile(path.join(flags.stateDir, 'receipts',
    new Date().toISOString().replace(/[:.]/g, '-') + '-host-' + currentName + '-to-' + targetName + '.json'),
    JSON.stringify(receipt, null, 2) + '\n', {mode: 0o600});
  console.log('HOST_ACTIVATED_VIA_SLOT', targetName, 'revision', revision);
  console.log('HOST_SLOT_ACTIVATED_SESSIONS_PRESERVED', before.length);
  console.log('HOST_REVISION', revision);
} catch (error) {
  for (const file of retained) await copyFile(path.join(backup, file), path.join(profile, file));
  if (switched) { try { await pointRelay(currentName, currentHost, currentPort); } catch { /* keep original error */ } }
  if (targetStarted) { try { stopSlot(targetName, targetPort); } catch { /* keep original error */ } }
  receipt.status = 'rolled-back';
  receipt.error = String(error.message);
  await writeFile(path.join(root, 'host-activation-slot.json'), JSON.stringify(receipt, null, 2) + '\n', {mode: 0o600});
  throw error;
} finally {
  await unlink(holdFile).catch(() => {});
}
