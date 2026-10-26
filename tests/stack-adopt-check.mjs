// M9: the stack adopts an existing listener on the slot port instead of
// entering a restart loop when its state file is missing. Uses a mock listener,
// no DSH and no live ports.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, rmSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stack = path.join(repo, 'integrations/supervisor/stack.mjs');
const temp = mkdtempSync(path.join(os.homedir(), '.stack-adopt-'));
const stateDir = path.join(temp, 'state');
const relayDir = path.join(temp, 'relay');
const entrance = 39930 + Math.floor(Math.random() * 40);
const slotPort = entrance + 1;

const mock = http.createServer((request, response) => { response.writeHead(200); response.end('mock'); });
await new Promise((resolve) => mock.listen(slotPort, '127.0.0.1', resolve));

const child = spawn(process.execPath, [stack, '--port', String(entrance), '--slot-name', 'S',
  '--slot-port', String(slotPort), '--runtime', '/nonexistent', '--home', '/nonexistent',
  '--workspace', repo, '--state-dir', stateDir, '--relay-dir', relayDir],
  {stdio: ['ignore', 'ignore', 'pipe']});
let stderr = '';
child.stderr.on('data', (chunk) => { stderr += String(chunk); });

function status(port) {
  return new Promise((resolve) => {
    http.get({host: '127.0.0.1', port, path: '/', timeout: 1500},
      (response) => { response.resume(); resolve(response.statusCode); }).on('error', () => resolve(0));
  });
}

try {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline && (await status(entrance)) !== 200)
    await new Promise((resolve) => setTimeout(resolve, 200));
  assert.equal(await status(entrance), 200, 'relay must serve the adopted listener');
  assert.match(stderr, /adopting existing listener/);
  assert.equal(existsSync(path.join(stateDir, 'S.json')), false);
  console.log('PASS stack adopted an existing slot listener without starting DSH');
} finally {
  child.kill('SIGTERM');
  await new Promise((resolve) => child.once('exit', resolve));
  mock.close();
  rmSync(temp, {recursive: true, force: true});
}
console.log('STACK_ADOPT_OK');
