// M9: boot-stack is idempotent. When the entrance already answers it must exit
// without starting a supervisor. The mock server lives in this process, so the
// child is spawned asynchronously (spawnSync would freeze the event loop and the
// mock could never answer).
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const script = path.join(repo, 'integrations/supervisor/boot-stack.sh');
const temp = mkdtempSync(path.join(os.homedir(), '.boot-stack-'));
const stateDir = path.join(temp, 'state');
const port = 39980 + Math.floor(Math.random() * 10);
const mock = http.createServer((request, response) => { response.writeHead(200); response.end('mock'); });
await new Promise((resolve) => mock.listen(port, '127.0.0.1', resolve));

let child = null;
try {
  child = spawn('bash', [script], {stdio: ['ignore', 'pipe', 'pipe'],
    env: {...process.env, LUMENDSH_PORT: String(port), LUMENDSH_STACK_STATE: stateDir}});
  let stdout = '', stderr = '';
  child.stdout.on('data', (chunk) => { stdout += String(chunk); });
  child.stderr.on('data', (chunk) => { stderr += String(chunk); });
  const timeout = setTimeout(() => child.kill('SIGKILL'), 20000);
  const code = await new Promise((resolve) => child.once('exit', resolve));
  clearTimeout(timeout);
  assert.equal(code, 0, stderr || stdout);
  assert.equal(existsSync(path.join(stateDir, 'supervisor.pid')), false,
    'a serving entrance must not start a second supervisor');
  const log = readFileSync(path.join(stateDir, 'boot.log'), 'utf8');
  assert.match(log, /entrance already answers/);
  console.log('PASS boot-stack left an already-serving entrance alone');
} finally {
  if (child && child.exitCode === null) child.kill('SIGKILL');
  mock.close();
  rmSync(temp, {recursive: true, force: true});
}
console.log('BOOT_STACK_OK');
