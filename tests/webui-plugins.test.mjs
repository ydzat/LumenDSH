import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const plugins = ['integrations/webui/composer', 'integrations/webui/shell'];

for (const dir of plugins) {
  const full = path.join(root, dir);
  test(`${dir} is a loadable client plugin package`, () => {
    const manifest = JSON.parse(readFileSync(path.join(full, 'package.json'), 'utf8'));
    assert.match(manifest.name, /^@lumendsh\/webui-/);
    assert.equal(manifest.type, 'module');
    assert.ok(manifest.dsh?.bundle?.patch, 'declares a bundle patch');
    assert.equal(manifest.dsh?.client?.platform, 'web');
    assert.equal(manifest.exports?.['./client']?.default, './lib/client.js');
    assert.ok(existsSync(path.join(full, manifest.dsh.bundle.patch)), 'patch file exists');
    assert.ok(existsSync(path.join(full, 'lib/client.js')), 'client bundle exists');
    assert.ok(existsSync(path.join(full, 'lib/index.js')), 'host entry exists');
    const client = readFileSync(path.join(full, 'lib/client.js'), 'utf8');
    if (dir.endsWith('composer')) assert.match(client, /ctx\.slots \?\? ctx\.get\('slots'\)/);
    assert.match(client, /__ModuleLoader__\.load/);
    assert.match(client, /exports\.apply\s*=/);
    assert.match(client, /exports\.inject\s*=/);
    const host = readFileSync(path.join(full, 'lib/index.js'), 'utf8');
    assert.match(host, /export function apply/);
    assert.match(host, /export const inject/);
  });
}
