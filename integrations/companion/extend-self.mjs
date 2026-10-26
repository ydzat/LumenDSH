// Scaffold a new module for a capability the agent found missing. The tool only
// creates a real, testable starting point (manifest, entry point, failing-by-
// default contract test, README, registry entry); the agent still has to
// implement and verify it. Nothing is mounted automatically.
import {existsSync, mkdirSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import path from 'node:path';

const NAME = /^[a-z][a-z0-9-]{1,40}$/;

export function readRegistry(root) {
  try { return JSON.parse(readFileSync(path.join(root, 'registry.json'), 'utf8')); }
  catch { return {version: 1, extensions: []}; }
}

function writeRegistry(root, registry) {
  const file = path.join(root, 'registry.json');
  const temp = file + '.tmp';
  writeFileSync(temp, JSON.stringify(registry, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, file);
}

export function scaffoldExtension({name, requirement, root, now = new Date()}) {
  if (!NAME.test(String(name ?? ''))) throw new Error('Extension name must be kebab-case (2..40)');
  const clean = String(requirement ?? '').trim();
  if (!clean || clean.length > 500) throw new Error('Extension requirement must be 1..500 characters');
  const directory = path.join(root, name);
  if (existsSync(directory)) throw new Error('Extension already exists: ' + name);
  mkdirSync(directory, {recursive: true, mode: 0o700});
  const files = {
    'README.md': `# ${name}\n\n## Requirement\n\n${clean}\n\n## Status\n\nscaffolded — implement the entry point and make the contract test pass.\n`,
    'package.json': JSON.stringify({name: '@lumendsh/' + name, version: '0.1.0', private: true,
      type: 'module', main: 'index.mjs', scripts: {test: 'node --test'}}, null, 2) + '\n',
    'index.mjs': `// ${name}: ${clean}\nexport const name = ${JSON.stringify(name)};\nexport const description = ${JSON.stringify(clean)};\n\n/** DSH plugin entry point. Implement the capability, then update the test. */\nexport function apply(ctx, config = {}) {\n  throw new Error('Not implemented yet');\n}\n`,
    'test/contract.test.mjs': `import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {name, description, apply} from '../index.mjs';\n\ntest('${name} declares itself', () => {\n  assert.equal(name, '${name}');\n  assert.ok(description.length > 0);\n  assert.equal(typeof apply, 'function');\n});\n`,
  };
  for (const [relative, content] of Object.entries(files)) {
    const target = path.join(directory, relative);
    mkdirSync(path.dirname(target), {recursive: true, mode: 0o700});
    writeFileSync(target, content, {mode: 0o600});
  }
  const registry = readRegistry(root);
  registry.extensions.push({name, requirement: clean, path: directory,
    createdAt: now.toISOString(), status: 'scaffolded'});
  writeRegistry(root, registry);
  return {name, path: directory, files: Object.keys(files), registry};
}

export function markExtension(root, name, status, now = new Date()) {
  if (!['scaffolded', 'implemented', 'verified'].includes(status)) throw new Error('Unknown extension status');
  const registry = readRegistry(root);
  const entry = registry.extensions.find((extension) => extension.name === name);
  if (!entry) throw new Error('Unknown extension: ' + name);
  entry.status = status;
  entry.updatedAt = now.toISOString();
  writeRegistry(root, registry);
  return entry;
}

export function listExtensions(root) {
  return readRegistry(root).extensions;
}
