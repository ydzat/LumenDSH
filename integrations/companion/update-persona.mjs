// Replace the existing persona section without restarting DSH or changing sessions.
import {readFile, writeFile, copyFile, rename} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';

export function replacePersona(previous, persona) {
  const begin = '<!-- sliver-wolf-persona-v1 -->';
  const end = '<!-- /sliver-wolf-persona-v1 -->';
  assert.equal(previous.split(begin).length, 2, 'Expected exactly one existing persona');
  assert.equal(previous.split(end).length, 2, 'Expected exactly one closing marker');
  const start = previous.indexOf(begin), finish = previous.indexOf(end);
  assert(finish > start, 'Invalid persona marker order');
  assert(persona.startsWith('# Silver Wolf companion') && persona.includes('Persona revision: 2.'), 'Unexpected persona payload');
  assert(!persona.includes(begin) && !persona.includes(end), 'Nested persona marker');
  return previous.slice(0, start) + begin + '\n' + persona.trimEnd() + '\n' + previous.slice(finish);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const target = path.join(process.env.HOME, 'agent-workspace/AGENTS.local.md');
  const persona = await readFile(new URL('./persona.md', import.meta.url), 'utf8');
  const previous = await readFile(target, 'utf8');
  const next = replacePersona(previous, persona);
  const digest = createHash('sha256').update(persona).digest('hex');
  if (next !== previous) {
    await copyFile(target, target + '.before-persona-' + Date.now());
    const temporary = target + '.persona-update.tmp';
    await writeFile(temporary, next, {mode:0o600, flag:'wx'});
    assert.equal(await readFile(target, 'utf8'), previous, 'Instructions changed during update');
    await rename(temporary, target);
  }
  assert.equal(await readFile(target, 'utf8'), next, 'Persona readback mismatch');
  console.log(JSON.stringify({status:next === previous ? 'unchanged' : 'updated', personaSha256:digest, restart:false}));
}
