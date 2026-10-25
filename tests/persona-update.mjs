import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {replacePersona} from '../integrations/companion/update-persona.mjs';
const persona = await readFile(new URL('../integrations/companion/persona.md', import.meta.url), 'utf8');
const begin = '<!-- sliver-wolf-persona-v1 -->', end = '<!-- /sliver-wolf-persona-v1 -->';
const old = `User-owned prefix\r\n${begin}\nold\n${end}\r\nUser-owned suffix`;
const next = replacePersona(old, persona);
assert(next.startsWith('User-owned prefix\r\n'));
assert(next.endsWith(`${end}\r\nUser-owned suffix`));
assert.equal(replacePersona(next, persona), next);
for (const malformed of ['', begin, end + begin, old + begin, old + end]) {
  assert.throws(() => replacePersona(malformed, persona));
}
console.log('Persona replacement: preservation, idempotence and malformed-marker checks passed');
