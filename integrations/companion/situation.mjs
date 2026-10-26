// Builds the bounded "situation" block handed to an autonomous development turn.
// It reports state instead of prescribing tasks: open self-notes, recent run
// outcomes, the last recorded error and the current cadence. The agent decides
// what, if anything, is worth doing.
import {openNotes} from './self-notes.mjs';

const KIND_LABEL = {friction: 'friction', curiosity: 'curiosity', need: 'need', idea: 'idea', result: 'result'};
const MAX_NOTES = 8;
const MAX_RUNS = 3;
const MAX_CHARS = 1600;

function line(value) { return String(value ?? '').replace(/\s+/g, ' ').trim(); }

export function buildSituation({notesFile, notes, runs = [], lastError = null, now = new Date()}, limit = MAX_CHARS) {
  let resolved = notes;
  if (!Array.isArray(resolved) && notesFile) {
    try { resolved = openNotes(notesFile); } catch { resolved = []; }
  }
  const open = (Array.isArray(resolved) ? resolved : []).filter((note) => note?.status === 'open' || note?.status === undefined);
  const parts = [];
  parts.push(`Situation at ${now.toISOString()}`);
  if (open.length === 0) {
    parts.push('Open self-notes: none. If something feels worth improving, write a note first, then act on it.');
  } else {
    const selected = open.slice(-MAX_NOTES);
    parts.push('Open self-notes (your own recorded friction, curiosity, needs and ideas):');
    for (const note of selected) parts.push(`- [${KIND_LABEL[note.kind] ?? note.kind}] ${line(note.text)}`);
    if (open.length > selected.length) parts.push(`- (${open.length - selected.length} older open notes omitted)`);
  }
  const recent = (Array.isArray(runs) ? runs : []).filter((run) => run && run.startedAt).slice(-MAX_RUNS);
  if (recent.length) {
    parts.push('Recent autonomous runs:');
    for (const run of recent) parts.push(`- ${line(run.activity)} ${line(run.status)} at ${line(run.startedAt)}` +
      (run.endedAt ? `, ended ${line(run.endedAt)}` : '') +
      (Number.isFinite(run.steps) ? `, steps ${run.steps}` : '') +
      (Number.isFinite(run.tools) ? `, tools ${run.tools}` : ''));
  }
  if (lastError) parts.push(`Last recorded error: ${line(lastError)}`);
  let text = parts.join('\n');
  if (text.length > limit) text = text.slice(0, Math.max(0, limit - 20)) + '\n… (truncated)';
  return text;
}
