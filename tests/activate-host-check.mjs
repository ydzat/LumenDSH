// The slot-aware activator must refuse to "upgrade" onto the slot that is already
// serving; otherwise it would stop the live instance for nothing.
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const script = path.join(repo, 'integrations/companion/activate-host-slot.mjs');
const result = spawnSync(process.execPath, [script, '--slot-name', 'A', '--slot-port', '30999',
  '--target', 'A', '--port', '30998'], {encoding: 'utf8', timeout: 20000});
assert.notEqual(result.status, 0, 'activation onto the live slot must fail');
assert.match(String(result.stderr), /must differ/, 'unexpected error: ' + result.stderr);
console.log('PASS activation refuses to target the live slot');
console.log('ACTIVATE_HOST_OK');
