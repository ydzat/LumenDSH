#!/usr/bin/env node
// M9.3 upgrade orchestration. Switches the relay between two running slots,
// observes the new slot and writes a durable receipt. On failure with
// --rollback-on-fail it switches back to the previous slot.
import {mkdirSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import path from 'node:path';

const argv = process.argv.slice(2);
const flags = {observeMs: 3000, rollbackOnFail: false,
  stateDir: path.join(process.env.HOME ?? '.', '.local/state/sliver-wolf-slots'),
  relayDir: path.join(process.env.HOME ?? '.', '.local/state/sliver-wolf-supervisor')};
for (let index = 0; index < argv.length; index++) {
  const value = argv[index];
  const next = () => { if (index + 1 >= argv.length) throw new Error('Missing value for ' + value); return argv[++index]; };
  switch (value) {
    case '--from': flags.from = next(); break;
    case '--to': flags.to = next(); break;
    case '--state-dir': flags.stateDir = path.resolve(next()); break;
    case '--relay-dir': flags.relayDir = path.resolve(next()); break;
    case '--observe-ms': flags.observeMs = Number(next()); break;
    case '--rollback-on-fail': flags.rollbackOnFail = true; break;
    case '--rollback-to': flags.rollbackTo = next(); break;
    case '--receipt-dir': flags.receiptDir = path.resolve(next()); break;
    case '--help':
      process.stdout.write('Usage: upgrade.mjs --from A --to B [--state-dir DIR] [--relay-dir DIR]\n' +
        '                  [--observe-ms N] [--rollback-on-fail] [--receipt-dir DIR]\n');
      process.exit(0);
      break;
    default: throw new Error('Unknown option: ' + value);
  }
}
if (!flags.from || !flags.to) throw new Error('--from and --to are required');
const receiptDir = flags.receiptDir ?? path.join(flags.stateDir, 'receipts');
mkdirSync(flags.relayDir, {recursive: true, mode: 0o700});
mkdirSync(receiptDir, {recursive: true, mode: 0o700});
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function readSlot(name) {
  try { return JSON.parse(readFileSync(path.join(flags.stateDir, name + '.json'), 'utf8')); }
  catch { return null; }
}
function alive(pid) {
  try { process.kill(pid, 0); return true; } catch { return false; }
}
function pointRelay(name, slot) {
  const activeFile = path.join(flags.relayDir, 'active.json');
  const temp = activeFile + '.tmp';
  writeFileSync(temp, JSON.stringify({host: slot.host ?? '127.0.0.1', port: slot.port,
    generation: Date.now(), slot: name}, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, activeFile);
}
function writeReceipt(value) {
  const file = path.join(receiptDir, new Date().toISOString().replace(/[:.]/g, '-') + '-' + value.from + '-to-' + value.to + '.json');
  writeFileSync(file, JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  return file;
}

const from = readSlot(flags.from), to = readSlot(flags.to);
if (!from || !alive(from.pid)) throw new Error('Source slot ' + flags.from + ' is not running');
if (!to || !alive(to.pid)) throw new Error('Target slot ' + flags.to + ' is not running');

const receipt = {schemaVersion: 1, from: flags.from, to: flags.to, fromPort: from.port, toPort: to.port,
  startedAt: new Date().toISOString(), observeMs: flags.observeMs, rollback: false};
pointRelay(flags.to, to);
await sleep(flags.observeMs);
const target = readSlot(flags.to);
const healthy = Boolean(target && alive(target.pid));
const activeFile = path.join(flags.relayDir, 'active.json');
const active = JSON.parse(readFileSync(activeFile, 'utf8'));
receipt.observed = {targetAlive: healthy, relayPort: active.port, relayOk: active.port === to.port};
if (!healthy || !receipt.observed.relayOk) {
  receipt.failed = true;
  if (flags.rollbackOnFail) {
    const targetName = flags.rollbackTo ?? flags.from;
    const targetSlot = targetName === flags.from ? from : readSlot(targetName);
    if (!targetSlot || !alive(targetSlot.pid)) throw new Error('Rollback slot ' + targetName + ' is not running');
    pointRelay(targetName, targetSlot);
    receipt.rollback = true;
    receipt.rolledBackTo = targetName;
    receipt.rolledBackPort = targetSlot.port;
  }
}
receipt.finishedAt = new Date().toISOString();
receipt.status = receipt.rollback ? 'rolled-back' : receipt.failed ? 'failed' : 'switched';
receipt.evidence = writeReceipt(receipt);
process.stdout.write(JSON.stringify(receipt) + '\n');
process.exit(receipt.status === 'switched' ? 0 : 1);
