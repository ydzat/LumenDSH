// Derive development candidates from real experience instead of inventing a
// task. When nothing qualifies the caller stays quiet: no candidate means no
// wake, which is a correct outcome, not a failure.
import {createHash} from 'node:crypto';

const DAY = 86400000;
const KIND_WEIGHT = {failure: 5, correction: 4.5, 'capability-gap': 4, friction: 3.5,
  observation: 1.5, result: 2, extension: 2, review: 2.5, stance: 1};
const RESOLVING = new Set(['result', 'review', 'extension', 'stance']);

function stableId(parts) {
  return createHash('sha256').update(parts.join('\u0000')).digest('hex').slice(0, 16);
}
function ageDays(atMs, now) { return Math.max(0, (now - (atMs || 0)) / DAY); }

export function deriveCandidates({experiences = [], notes = [], runs = [], now = Date.now()} = {}) {
  const candidates = [];
  const resolved = new Set();
  for (const experience of experiences) {
    if (RESOLVING.has(experience.kind)) for (const ref of experience.refs ?? []) resolved.add(ref);
  }
  const push = (candidate) => candidates.push(candidate);

  for (const experience of experiences) {
    const age = ageDays(experience.atMs, now);
    if (experience.kind === 'failure' && age <= 14 && !resolved.has(experience.id)) {
      push({id: stableId(['failure', experience.id]), kind: 'failure',
        title: '未解决的失败：' + experience.summary, situation: experience.summary,
        evidence: [experience.id], severity: experience.severity ?? 3, atMs: experience.atMs});
    }
    if (experience.kind === 'correction' && age <= 14 && !resolved.has(experience.id)) {
      push({id: stableId(['correction', experience.id]), kind: 'correction',
        title: '一条纠正尚未消化：' + experience.summary, situation: experience.summary,
        evidence: [experience.id], severity: experience.severity ?? 3, atMs: experience.atMs});
    }
    if (experience.kind === 'capability-gap' && age <= 30 && !resolved.has(experience.id)) {
      push({id: stableId(['capability-gap', experience.id]), kind: 'capability-gap',
        title: '能力缺口：' + experience.summary, situation: experience.summary,
        evidence: [experience.id], severity: experience.severity ?? 3, atMs: experience.atMs});
    }
  }

  // Repeated friction: the same tag keeps showing up. A single annoyance is noise;
  // a pattern is a real thing to design around.
  const friction = [...experiences.filter((item) => item.kind === 'friction'),
    ...notes.filter((note) => note.kind === 'friction')];
  const byTag = new Map();
  for (const item of friction) {
    for (const tag of item.tags ?? []) {
      const bucket = byTag.get(tag) ?? [];
      bucket.push(item);
      byTag.set(tag, bucket);
    }
  }
  for (const [tag, items] of byTag) {
    const unique = [...new Map(items.map((item) => [item.id ?? item.text, item])).values()];
    if (unique.length < 2) continue;
    const latest = unique.sort((a, b) => (b.atMs ?? 0) - (a.atMs ?? 0))[0];
    push({id: stableId(['pattern', tag]), kind: 'pattern',
      title: '反复出现的摩擦：' + tag, situation: latest.summary ?? latest.text,
      evidence: unique.map((item) => item.id ?? item.text), severity: 3, atMs: latest.atMs ?? now, repetitions: unique.length});
  }

  for (const note of notes) {
    if (note.status !== 'open' && note.status !== undefined) continue;
    const atMs = Number.isFinite(Date.parse(note.at)) ? Date.parse(note.at) : 0;
    push({id: stableId(['note', note.id]), kind: 'note', title: '自己的需求：' + note.text,
      situation: note.text, evidence: [note.id], severity: 2, atMs, noteKind: note.kind});
  }

  // A completed development run without any follow-up is itself a situation:
  // what did that change, and did it actually help?
  const recentRuns = runs.filter((run) => run.activity === 'develop' && run.status === 'completed'
    && Number.isFinite(Date.parse(run.endedAt)) && now - Date.parse(run.endedAt) <= DAY);
  for (const run of recentRuns) {
    const followed = experiences.some((item) => (item.refs ?? []).includes(run.id));
    if (followed) continue;
    push({id: stableId(['run', run.id]), kind: 'result', title: '回顾刚完成的一轮开发',
      situation: `开发轮次 ${run.id} 已完成（${run.steps ?? '?'} 步 / ${run.tools ?? '?'} 次工具），还没有留下任何评估或后续记录。`,
      evidence: [run.id], severity: 2, atMs: Date.parse(run.endedAt)});
  }

  const attempted = new Map();
  for (const run of runs.slice(-5)) if (run.candidateId) attempted.set(run.candidateId, (attempted.get(run.candidateId) ?? 0) + 1);

  for (const candidate of candidates) {
    const age = ageDays(candidate.atMs, now);
    const freshness = Math.max(0, 2 - age / 7);
    const severity = (candidate.severity ?? 2) * 0.4;
    const repetition = Math.min(2, (candidate.repetitions ?? 1) - 1) * 0.5;
    const penalty = (attempted.get(candidate.id) ?? 0) * 3;
    candidate.score = (KIND_WEIGHT[candidate.kind] ?? 2) + freshness + severity + repetition - penalty;
    candidate.ageDays = age;
  }
  return candidates.sort((a, b) => b.score - a.score);
}

export function pickCandidate(candidates, {runs = []} = {}) {
  const recent = new Set(runs.slice(-3).map((run) => run.candidateId).filter(Boolean));
  return candidates.find((candidate) => !recent.has(candidate.id)) ?? null;
}

export function candidateSituation(candidate, now = Date.now()) {
  if (!candidate) return '';
  const lines = [`Candidate: ${candidate.title}`,
    `What happened: ${candidate.situation}`,
    `Why it is worth attention now: kind=${candidate.kind}, age=${candidate.ageDays.toFixed(1)}d, score=${candidate.score.toFixed(2)}`];
  if (candidate.evidence?.length) lines.push(`Evidence ids: ${candidate.evidence.slice(0, 6).join(', ')}`);
  return lines.join('\n');
}
