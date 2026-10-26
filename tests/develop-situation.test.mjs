import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {activityPrompt} from '../integrations/companion/policy.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('develop prompt carries the situation and drops task-list wording', () => {
  const record = {id: 'r1', limits: {writes: 10, toolCalls: 10, steps: 10}};
  const text = activityPrompt('develop', record,
    'Situation at 2026-10-26T04:00:00Z\n- [friction] the wake message reads like a task list');
  assert.match(text, /the wake message reads like a task list/);
  assert.match(text, /decide what, if anything, is worth doing/);
  assert.match(text, /self-notes ledger/);
  assert.doesNotMatch(text, /pick one small improvement/);
  assert.match(activityPrompt('develop', record), /decide what, if anything, is worth doing/);
  assert.doesNotMatch(activityPrompt('develop', record), /Situation at/);
});

test('companion modules parse', () => {
  for (const file of ['host.mjs', 'policy.mjs', 'situation.mjs', 'self-notes.mjs']) {
    const result = spawnSync(process.execPath, ['--check', path.join(repo, 'integrations/companion', file)],
      {encoding: 'utf8'});
    assert.equal(result.status, 0, file + ': ' + (result.stderr || ''));
  }
});
