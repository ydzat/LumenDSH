import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {appendNote, updateNote} from '../integrations/companion/self-notes.mjs';
import {buildSituation} from '../integrations/companion/situation.mjs';

const now = new Date('2026-10-26T04:00:00Z');

test('situation lists open notes, recent runs, error and omits closed notes', () => {
  const text = buildSituation({notes: [
    {id: '1', kind: 'friction', text: 'wake message reads like a task list', status: 'open'},
    {id: '2', kind: 'idea', text: 'record curiosity after each run', status: 'open'},
    {id: '3', kind: 'need', text: 'already done thing', status: 'done'},
  ], runs: [
    {activity: 'develop', status: 'completed', startedAt: '2026-10-26T01:00:00Z', endedAt: '2026-10-26T01:05:00Z', steps: 12, tools: 9},
    {activity: 'develop', status: 'failed', startedAt: '2026-10-26T02:00:00Z', steps: 3, tools: 1},
  ], lastError: 'boom', now}, 4000);
  assert.match(text, /wake message reads like a task list/);
  assert.match(text, /record curiosity after each run/);
  assert.doesNotMatch(text, /already done thing/);
  assert.match(text, /develop completed at 2026-10-26T01:00:00Z/);
  assert.match(text, /develop failed at 2026-10-26T02:00:00Z/);
  assert.match(text, /Last recorded error: boom/);
});

test('empty notes ask for a note before acting', () => {
  const text = buildSituation({notes: [], now});
  assert.match(text, /Open self-notes: none/);
  assert.match(text, /write a note first/);
});

test('the situation is bounded and reads from the notes file', () => {
  const directory = mkdtempSync(path.join(os.homedir(), '.situation-'));
  try {
    const file = path.join(directory, 'notes.jsonl');
    const note = appendNote(file, {kind: 'curiosity', text: 'why does a restart lose the wake context'});
    updateNote(file, note.id, 'done');
    appendNote(file, {kind: 'need', text: 'situation-aware wake'});
    const text = buildSituation({notesFile: file, now}, 120);
    assert.ok(text.length <= 120, 'bounded output');
    assert.match(text, /truncated/);
    assert.match(text, /Situation at/);
    const full = buildSituation({notesFile: file, now}, 4000);
    assert.match(full, /situation-aware wake/);
    assert.doesNotMatch(full, /why does a restart lose/);
  } finally {
    rmSync(directory, {recursive: true, force: true});
  }
});
