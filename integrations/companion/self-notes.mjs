// Append-only self-notes ledger: the agent records friction, curiosity, ideas and
// needs while working, so a later development turn can pick from its own recorded
// needs instead of inventing a task. Records are never rewritten; status changes
// are appended as update records. No network, no secrets.
import {appendFileSync, mkdirSync, readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';

export const NOTE_KINDS = ['need', 'curiosity', 'friction', 'idea', 'result'];
export const NOTE_STATUSES = ['open', 'done', 'dropped'];
const kinds = new Set(NOTE_KINDS);
const statuses = new Set(NOTE_STATUSES);

function ensureDirectory(file) { mkdirSync(path.dirname(file), {recursive: true, mode: 0o700}); }

export function appendNote(file, note, now = new Date()) {
  const kind = note?.kind ?? 'idea';
  if (!kinds.has(kind)) throw new Error('Unknown note kind: ' + kind);
  const text = String(note?.text ?? '').trim();
  if (!text || text.length > 500) throw new Error('Note text must be 1..500 characters');
  const tags = (Array.isArray(note?.tags) ? note.tags : []).slice(0, 8).map((tag) => String(tag));
  if (tags.some((tag) => tag.length > 32)) throw new Error('Note tag too long');
  const record = {schemaVersion: 1, type: 'note', id: randomUUID(), kind, text, tags, at: now.toISOString()};
  ensureDirectory(file);
  appendFileSync(file, JSON.stringify(record) + '\n', {mode: 0o600});
  return record;
}

export function updateNote(file, id, status, detail = '', now = new Date()) {
  if (!statuses.has(status)) throw new Error('Unknown note status: ' + status);
  const record = {schemaVersion: 1, type: 'update', id: String(id), status,
    detail: String(detail).slice(0, 500), at: now.toISOString()};
  ensureDirectory(file);
  appendFileSync(file, JSON.stringify(record) + '\n', {mode: 0o600});
  return record;
}

export function readNotes(file) {
  let text = '';
  try { text = readFileSync(file, 'utf8'); }
  catch (error) { if (error.code === 'ENOENT') return {notes: [], skipped: 0}; throw error; }
  const notes = new Map();
  let skipped = 0;
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    let record;
    try { record = JSON.parse(line); } catch { skipped++; continue; }
    if (record?.schemaVersion !== 1) { skipped++; continue; }
    if (record.type === 'note' && record.id && kinds.has(record.kind) && typeof record.text === 'string') {
      notes.set(record.id, {id: record.id, kind: record.kind, text: record.text,
        tags: Array.isArray(record.tags) ? record.tags : [], at: record.at ?? null, status: 'open'});
    } else if (record.type === 'update' && notes.has(record.id) && statuses.has(record.status)) {
      const note = notes.get(record.id);
      note.status = record.status;
      if (record.detail) note.detail = record.detail;
      note.updatedAt = record.at ?? null;
    } else skipped++;
  }
  return {notes: [...notes.values()], skipped};
}

export function openNotes(file) {
  return readNotes(file).notes.filter((note) => note.status === 'open');
}
