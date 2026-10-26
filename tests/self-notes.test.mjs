import test from 'node:test';
import assert from 'node:assert/strict';
import {appendFileSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {appendNote, openNotes, readNotes, updateNote} from '../integrations/companion/self-notes.mjs';

function tempFile() {
  const directory = mkdtempSync(path.join(os.homedir(), '.self-notes-'));
  return {directory, file: path.join(directory, 'notes.jsonl')};
}

test('notes append, update and filter without rewriting history', () => {
  const {directory, file} = tempFile();
  try {
    const need = appendNote(file, {kind: 'need', text: '开发轮次缺少处境信息', tags: ['develop']});
    const idea = appendNote(file, {kind: 'idea', text: '把最近提交和失败写进唤醒消息'});
    updateNote(file, idea.id, 'done', '已在 host 中实现');
    const read = readNotes(file);
    assert.equal(read.skipped, 0);
    assert.equal(read.notes.length, 2);
    assert.equal(read.notes.find((note) => note.id === need.id).status, 'open');
    const done = read.notes.find((note) => note.id === idea.id);
    assert.equal(done.status, 'done');
    assert.equal(done.detail, '已在 host 中实现');
    assert.deepEqual(openNotes(file).map((note) => note.text), ['开发轮次缺少处境信息']);
    // appends never rewrite earlier lines
    assert.equal(readFileSync(file, 'utf8').trim().split('\n').length, 3);
  } finally {
    rmSync(directory, {recursive: true, force: true});
  }
});

test('invalid notes are refused and malformed lines are skipped on read', () => {
  const {directory, file} = tempFile();
  try {
    assert.throws(() => appendNote(file, {kind: 'unknown', text: 'x'}), /Unknown note kind/);
    assert.throws(() => appendNote(file, {kind: 'need', text: '   '}), /1\.\.500/);
    assert.throws(() => appendNote(file, {kind: 'need', text: 'x'.repeat(501)}), /1\.\.500/);
    assert.throws(() => updateNote(file, 'missing', 'unknown'), /Unknown note status/);
    appendFileSync(file, 'not json\n');
    const note = appendNote(file, {kind: 'curiosity', text: '大肥鱼的处境唤醒'});
    const read = readNotes(file);
    assert.equal(read.notes.length, 1);
    assert.equal(read.notes[0].id, note.id);
    assert.equal(read.skipped, 1);
  } finally {
    rmSync(directory, {recursive: true, force: true});
  }
});

test('unknown status updates leave the note open and count as skipped', () => {
  const {directory, file} = tempFile();
  try {
    const note = appendNote(file, {kind: 'friction', text: 'host 激活会短暂断线'});
    appendFileSync(file, JSON.stringify({schemaVersion: 1, type: 'update', id: note.id, status: 'weird', at: null}) + '\n');
    const read = readNotes(file);
    assert.equal(read.skipped, 1);
    assert.equal(read.notes[0].status, 'open');
  } finally {
    rmSync(directory, {recursive: true, force: true});
  }
});
