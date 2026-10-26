// M9.2 relay checks: forwards to the active slot, follows a switch, closes old
// connections so the client reconnects, and releases its lock. Mock upstreams
// only.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, renameSync, writeFileSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const relayScript = path.join(repo, 'integrations/supervisor/relay.mjs');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function serve(name) {
  return new Promise((resolve) => {
    const server = http.createServer((request, response) => {
      response.writeHead(200, {'content-type': 'text/plain'});
      response.end(name);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}
function request(port) {
  return new Promise((resolve, reject) => {
    http.get({host: '127.0.0.1', port, path: '/', timeout: 2000}, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => resolve(body));
    }).on('error', reject);
  });
}
function setActive(directory, port) {
  const temp = path.join(directory, 'active.json.tmp');
  writeFileSync(temp, JSON.stringify({host: '127.0.0.1', port, generation: Date.now()}));
  renameSync(temp, path.join(directory, 'active.json'));
}
async function waitFor(predicate, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await sleep(100);
  }
  throw new Error('Timeout waiting for ' + label);
}

const slotA = await serve('A');
const slotB = await serve('B');
const directory = mkdtempSync(path.join(os.tmpdir(), 'relay-check-'));
const port = 39500 + Math.floor(Math.random() * 400);
setActive(directory, slotA.address().port);
const relay = spawn(process.execPath, [relayScript, '--port', String(port), '--state-dir', directory],
  {stdio: ['ignore', 'ignore', 'pipe']});
let relayStderr = '';
relay.stderr.on('data', (chunk) => { relayStderr += String(chunk); });

try {
  await waitFor(async () => {
    try { return (await request(port)) === 'A'; } catch { return false; }
  }, 15000, 'relay forwarding to A');
  console.log('PASS relay forwards to active slot A');

  // A long-lived client connection must be dropped when the slot changes.
  const persistent = net.connect({host: '127.0.0.1', port});
  await new Promise((resolve) => persistent.once('connect', resolve));
  const closed = new Promise((resolve) => persistent.once('close', resolve));

  setActive(directory, slotB.address().port);
  await waitFor(async () => {
    try { return (await request(port)) === 'B'; } catch { return false; }
  }, 15000, 'relay forwarding to B');
  console.log('PASS relay follows the switch to slot B');
  await Promise.race([closed, sleep(3000)]);
  assert.equal(persistent.destroyed, true, 'previous connection must be dropped');
  console.log('PASS previous client connection dropped on switch');
} finally {
  relay.kill('SIGTERM');
  await new Promise((resolve) => relay.once('exit', resolve));
  assert.equal(existsSync(path.join(directory, 'relay.pid')), false, 'relay lock released');
  slotA.close();
  slotB.close();
}
console.log('RELAY_OK');
