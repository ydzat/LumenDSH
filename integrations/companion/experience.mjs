// Append-only experience stream. The host records what actually happened (run
// outcomes, errors, human interruptions) and the agent records what it noticed
// (friction, corrections, capability gaps, extensions). Candidates for the next
// development turn are derived from this file; nothing is rewritten in place.
import {appendFileSync, mkdirSync, readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';

export const EXPERIENCE_KINDS = ['failure', 'correction', 'friction', 'result',
  'observation', 'capability-gap', 'extension', 'review', 'stance'];
const kinds = new Set(EXPERIENCE_KINDS);

function ensureDirectory(file) { mkdirSync(path.dirname(file), {recursive: true, mode: 0o700}); }

export function appendExperience(file, experience, now = new Date()) {
  const kind = experience?.kind;
  if (!kinds.has(kind)) throw new Error('Unknown experience kind: ' + kind);
  const summary = String(experience?.summary ?? '').trim();
  if (!summary || summary.length > 500) throw new Error('Experience summary must be 1..500 characters');
  const detail = experience?.detail === undefined ? undefined : String(experience.detail).slice(0, 2000);
  const severity = Number.isInteger(experience?.severity) ? Math.min(5, Math.max(1, experience.severity)) : 2;
  const tags = (Array.isArray(experience?.tags) ? experience.tags : []).slice(0, 8).map(String);
  if (tags.some((tag) => tag.length > 32)) throw new Error('Experience tag too long');
  const refs = (Array.isArray(experience?.refs) ? experience.refs : []).slice(0, 8).map(String);
  const record = {schemaVersion: 1, type: 'experience', id: randomUUID(), kind, summary,
    severity, tags, refs, at: now.toISOString()};
  if (detail !== undefined) record.detail = detail;
  if (experience?.source) record.source = String(experience.source).slice(0, 40);
  ensureDirectory(file);
  appendFileSync(file, JSON.stringify(record) + '\n', {mode: 0o600});
  return record;
}

export function readExperiences(file) {
  let text = '';
  try { text = readFileSync(file, 'utf8'); }
  catch (error) { if (error.code === 'ENOENT') return {experiences: [], skipped: 0}; throw error; }
  const experiences = [];
  let skipped = 0;
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    let record;
    try { record = JSON.parse(line); } catch { skipped++; continue; }
    if (record?.schemaVersion !== 1 || record?.type !== 'experience' || !kinds.has(record.kind)
      || typeof record.summary !== 'string' || !record.id) { skipped++; continue; }
    experiences.push({id: record.id, kind: record.kind, summary: record.summary,
      detail: record.detail, severity: Number(record.severity ?? 2), tags: Array.isArray(record.tags) ? record.tags : [],
      refs: Array.isArray(record.refs) ? record.refs : [], source: record.source ?? null, at: record.at ?? null,
      atMs: Number.isFinite(Date.parse(record.at)) ? Date.parse(record.at) : 0});
  }
  return {experiences, skipped};
}

export function recentExperiences(file, {sinceMs = 7 * 86400000, kinds: wanted = null, limit = 20, now = Date.now()} = {}) {
  const {experiences} = readExperiences(file);
  const cutoff = now - sinceMs;
  return experiences
    .filter((item) => item.atMs >= cutoff && (!wanted || wanted.includes(item.kind)))
    .sort((a, b) => b.atMs - a.atMs)
    .slice(0, limit);
}

export function experienceStats(file, now = Date.now()) {
  const {experiences, skipped} = readExperiences(file);
  const byKind = {};
  for (const item of experiences) byKind[item.kind] = (byKind[item.kind] ?? 0) + 1;
  const last24h = experiences.filter((item) => item.atMs >= now - 86400000).length;
  return {total: experiences.length, byKind, last24h, skipped};
}
