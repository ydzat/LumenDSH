// Adapt the pinned upstream bundle; do not reconstruct its plugin dependency graph.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const [packageRoot, outputDirectory, cliPath] = process.argv.slice(2);
assert(packageRoot && outputDirectory && cliPath, 'Usage: prepare-profile.mjs PACKAGE OUTPUT ABSOLUTE_MNEMON_CLI');
assert(path.posix.isAbsolute(cliPath) && !/[\r\n]/.test(cliPath), 'CLI path must be an absolute phone path');
const manifest = JSON.parse(await readFile(path.join(packageRoot, 'package.json'), 'utf8'));
assert.equal(manifest.name, 'dsh-mnemon');
assert.equal(manifest.version, '0.5.34');
const original = await readFile(path.join(packageRoot, 'cordis.patch.yml'), 'utf8');
let patch = original.replaceAll('\r\n', '\n');
function replaceOnce(anchor, replacement) {
  assert.equal(patch.split(anchor).length, 2, 'Upstream bundle changed: ' + anchor.trim());
  patch = patch.replace(anchor, replacement);
}
replaceOnce('            routingGuidance: true\n',
  '            routingGuidance: true\n' +
  '            storageScope: workspace\n' +
  '            syncEnabled: false\n' +
  '            idleReview:\n' +
  '              enabled: false\n' +
  '            taskAgentModel:\n' +
  '              mode: inherit\n' +
  '              followConversation: true\n' +
  '            cliPath: ' + JSON.stringify(cliPath) + '\n');
replaceOnce('            embedding:\n              enabled: false\n              endpoint: http://localhost:11434\n              model: nomic-embed-text\n',
  '            embedding:\n              enabled: true\n              endpoint: http://127.0.0.1:3083/v1\n              model: sw-qwen3-embedding-0.6b-q8\n              protocol: openai\n');
// The phone-local owner cookie can provision and manage memory spaces.
replaceOnce('            remoteAccess: read-only\n', '            remoteAccess: trusted-host\n');
replaceOnce('            timeoutMs: 10000\n', '            timeoutMs: 120000\n');
replaceOnce('          name: dsh-mnemon-strategy-auto-capture\n          disabled: true\n',
  '          name: dsh-mnemon-strategy-auto-capture\n          disabled: false\n          config:\n' +
  '            instruction: >-\n' +
  '              Remember at most one durable user preference, correction, confirmed fact,\n' +
  '              or shared experience per turn when it is useful. Use the current\n' +
  '              conversation or accessible evidence. Compare existing memory first.\n' +
  '              Distinguish observed facts from attributed impressions; never invent\n' +
  '              experiences, feelings of the user, or historical events. Do not store\n' +
  '              secrets, raw audio, transient progress, or duplicate entries. Respect\n' +
  '              requests not to remember. No candidate means no write.\n');
// The official bundle is already registered by the DSH plugin installer. Override
// its existing child IDs rather than inserting a duplicate bundle or agent.
const children = patch.indexOf('        - id: mnemon\n');
assert(children >= 0, 'Upstream bundle children missing');
patch = patch.slice(children).split('\n').map(line => line.startsWith('        ') ? line.slice(8) : line).join('\n');
await mkdir(outputDirectory, {recursive: true});
await writeFile(path.join(outputDirectory, 'companion.patch.yml'), patch, {mode: 0o600, flag: 'wx'});
await writeFile(path.join(outputDirectory, 'provenance.json'), JSON.stringify({
  schemaVersion: 1, package: manifest.name, version: manifest.version, license: manifest.license,
  source: 'https://github.com/omdsh-dev/dsh-mnemon',
  upstreamBundleSha256: createHash('sha256').update(original).digest('hex'),
  adaptedBundleSha256: createHash('sha256').update(patch).digest('hex'),
  changes: ['workspace storage', 'no Git sync', 'no independent idle-review agent',
    'explicit native CLI path', 'bounded evidence-based current-agent capture', 'measured phone semantic indexing deadline'],
  status: 'prepared-not-activated',
}, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
console.log('COMPANION_PROFILE_PREPARED_NOT_ACTIVATED');
