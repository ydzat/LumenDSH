// Growth engine for the development agent. Four stages with concrete
// bottlenecks, promoted only by evidence in the experience stream and runs:
// observe -> self-fix -> self-extension -> self-review. Stances are durable
// positions backed by evidence, capped and never rewritten.
import {randomUUID} from 'node:crypto';

const DAY = 86400000;
export const STAGES = [
  {stage: 1, name: '观察', bottleneck: '记录一条自己发现的摩擦或缺口', evidence: 'agent-observation'},
  {stage: 2, name: '自修', bottleneck: '完成一轮针对失败或纠正的开发', evidence: 'fix-run'},
  {stage: 3, name: '自造', bottleneck: '创建并验证一个新模块或工具', evidence: 'extension'},
  {stage: 4, name: '自审', bottleneck: '回顾并评估自己的一次改动', evidence: 'review'},
];
const MAX_STANCES = 12;

export function emptyGrowth(now = Date.now()) {
  return {schemaVersion: 1, startedAt: new Date(now).toISOString(), stage: 1, day: 1,
    stances: [], lastAdvancedAt: null};
}

export function growthDay(growth, now = Date.now()) {
  const started = Date.parse(growth?.startedAt ?? '');
  if (!Number.isFinite(started)) return 1;
  return Math.max(1, Math.floor((now - started) / DAY) + 1);
}

function hasObservation(experiences) {
  return experiences.some((item) => item.source === 'agent' ||
    ['friction', 'observation', 'capability-gap'].includes(item.kind));
}
function hasFixRun(runs) {
  return runs.some((run) => run.status === 'completed' &&
    ['failure', 'correction'].includes(run.candidateKind));
}
function hasExtension(experiences) {
  return experiences.some((item) => item.kind === 'extension');
}
function hasReview(experiences) {
  return experiences.some((item) => item.kind === 'review');
}

export function bottleneckMet(stage, {experiences = [], runs = []}) {
  if (stage === 1) return hasObservation(experiences);
  if (stage === 2) return hasFixRun(runs);
  if (stage === 3) return hasExtension(experiences);
  if (stage === 4) return hasReview(experiences);
  return false;
}

export function promoteStances(growth, notes, now = Date.now()) {
  if (!Array.isArray(growth.stances)) growth.stances = [];
  if (growth.stage < 3) return growth;
  const closed = notes.filter((note) => note.status === 'done');
  const byTag = new Map();
  for (const note of closed) for (const tag of note.tags ?? []) {
    const bucket = byTag.get(tag) ?? [];
    bucket.push(note);
    byTag.set(tag, bucket);
  }
  for (const [tag, items] of byTag) {
    if (items.length < 2) continue;
    const text = items.sort((a, b) => String(b.updatedAt ?? b.at ?? '').localeCompare(String(a.updatedAt ?? a.at ?? '')))[0].text;
    if (growth.stances.some((stance) => stance.text === text)) continue;
    if (growth.stances.length >= MAX_STANCES) break;
    growth.stances.push({id: randomUUID(), text: String(text).slice(0, 200), tag,
      evidence: items.map((item) => item.id), createdAt: new Date(now).toISOString()});
  }
  return growth;
}

export function updateGrowth(growth, {experiences = [], notes = [], runs = [], now = Date.now()}) {
  const next = growth?.schemaVersion === 1 ? {...growth, stances: [...(growth.stances ?? [])]} : emptyGrowth(now);
  next.day = growthDay(next, now);
  let changed = false;
  while (next.stage < 4 && bottleneckMet(next.stage, {experiences, runs})) {
    next.stage += 1;
    next.lastAdvancedAt = new Date(now).toISOString();
    changed = true;
  }
  const before = next.stances.length;
  promoteStances(next, notes, now);
  if (next.stances.length !== before) changed = true;
  return {growth: next, changed};
}

export function addStance(growth, {text, evidence = []}, now = Date.now()) {
  if (growth.stage < 3) throw new Error('Stances appear from stage 3 (自造) onwards');
  const clean = String(text ?? '').trim();
  if (!clean || clean.length > 200) throw new Error('Stance text must be 1..200 characters');
  if (!Array.isArray(evidence) || evidence.length === 0) throw new Error('A stance needs at least one evidence id');
  if (growth.stances.some((stance) => stance.text === clean)) return growth.stances.find((stance) => stance.text === clean);
  if (growth.stances.length >= MAX_STANCES) throw new Error('Stance list is full (12)');
  const stance = {id: randomUUID(), text: clean, evidence: evidence.slice(0, 8).map(String), createdAt: new Date(now).toISOString()};
  growth.stances.push(stance);
  return stance;
}

export function growthSummary(growth, {experiences = [], runs = []} = {}) {
  const current = STAGES.find((item) => item.stage === growth.stage) ?? STAGES[0];
  const met = bottleneckMet(current.stage, {experiences, runs});
  return `第 ${growth.day} 天 · 阶段 ${current.stage}/4「${current.name}」` +
    (met ? ' · 瓶颈已满足，等待下一次评估' : ` · 瓶颈：${current.bottleneck}`);
}
