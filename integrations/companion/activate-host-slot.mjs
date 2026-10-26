#!/usr/bin/env node
// Slot-aware companion host activation. Installs an immutable host revision,
// stops the slot, and lets the watchdog restart the stack so the new host loads
// under the relay architecture. Restores the profile and the slot on failure.
import {copyFile, mkdir, readFile, writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const home = process.env.HOME;
const root = path.join(home, 'sliver-wolf-companion');
const source = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const flags = {slotName: 'A', stateDir: path.join(home, '.local/state/sliver-wolf-slots'), port: 3080};
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  if (value === '--slot-name') flags.slotName = next();
  else if (value === '--state-dir') flags.stateDir = path.resolve(next());
  else if (value === '--port') flags.port = Number(next());
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
async function cookie() {
  const value = JSON.parse(await readFile(connectionFile, 'utf8'));
  return value.cookie;
}
async function sessionIds() {
  const method = 'session/list';
  const response = await fetch(`http://127.0.0.1:${flags.port}/api/${method}`, {method: 'POST',
    headers: {Cookie: await cookie(), 'Content-Type': 'application/json'},
    body: JSON.stringify({type: 'client-request', rpcId: randomUUID(), method, payload: {args: {_request: {}}}}),
    signal: AbortSignal.timeout(5000)});
  const body = await response.json();
  assert(body.result.ok, 'session/list failed');
  return body.result.value.items.map((item) => item.sessionId).sort();
}
async function entranceReady() {
  try { const response = await fetch(`http://127.0.0.1:${flags.port}/`, {signal: AbortSignal.timeout(2000)}); return response.status; }
  catch { return 0; }
}
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const before = await sessionIds();
assert(before.length > 0, 'No sessions to preserve');
const profile = path.join(home, '.dsh/profiles/web');
const backup = path.join(root, 'host-slot-backup-' + Date.now());
await mkdir(backup, {mode: 0o700});
const retained = ['package.json', 'cordis.patch.yml', 'pnpm-lock.yaml'];
for (const file of retained) await copyFile(path.join(profile, file), path.join(backup, file));

let stopped = false;
try {
  // The slot must be down before the profile is rewritten.
  execFileSync(process.execPath, [slots, 'stop', '--name', flags.slotName, '--state-dir', flags.stateDir],
    {stdio: 'inherit'});
  stopped = true;

  execFileSync('dsh', ['plugin', '--profile', 'web', 'add', 'link:' + candidate, '--ignore-scripts'],
    {stdio: 'inherit'});
  const patchFile = path.join(profile, 'cordis.patch.yml');
  let patch = await readFile(patchFile, 'utf8');
  patch = patch.replace('    timeoutMs: 10000\n', '    timeoutMs: 120000\n');
  assert(patch.includes('    timeoutMs: 120000\n'), 'Unexpected Mnemon source deadline configuration');
  await writeFile(patchFile, patch.replace('- id: mnemon-source-documents\n  name: dsh-mnemon-source-documents',
    '- id: mnemon-source-documents\n  disabled: true\n  name: dsh-mnemon-source-documents'), {mode: 0o600});

  // The watchdog notices the dead slot and restarts the stack, which starts a
  // fresh slot over the updated profile.
  let ready = 0;
  for (let attempt = 0; attempt < 150; attempt++) {
    ready = await entranceReady();
    if (ready === 200 || ready === 401) break;
    await sleep(1000);
  }
  assert(ready === 200 || ready === 401, 'Entrance did not recover after slot restart');

  let after = null;
  for (let attempt = 0; attempt < 60; attempt++) {
    try { after = await sessionIds(); break; } catch { await sleep(1000); }
  }
  assert(after, 'Session list unavailable after recovery');
  assert.deepEqual(after, before, 'Session set changed across activation');
  const status = await fetch(`http://127.0.0.1:${flags.port}/sliver-wolf/companion/status`,
    {headers: {Cookie: await cookie()}, signal: AbortSignal.timeout(5000)});
  assert.equal(status.status, 200, 'Companion status endpoint failed after activation');
  await writeFile(path.join(root, 'host-activation-slot.json'),
    JSON.stringify({revision, backup, sessionsPreserved: before.length, status: 'active'}, null, 2) + '\n', {mode: 0o600});
  console.log('HOST_SLOT_ACTIVATED_SESSIONS_PRESERVED', before.length);
  console.log('HOST_REVISION', revision);
} catch (error) {
  for (const file of retained) await copyFile(path.join(backup, file), path.join(profile, file));
  if (stopped) {
    try {
      execFileSync(process.execPath, [slots, 'start', '--name', flags.slotName, '--port', '3082',
        '--runtime', '/data/data/com.termux/files/home/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime',
        '--home', path.join(home, '.dsh'), '--workspace', path.join(home, 'agent-workspace'),
        '--stable-port', '3080', '--state-dir', flags.stateDir], {stdio: 'inherit'});
    } catch { /* the watchdog may restart the stack anyway */ }
  }
  throw error;
}
