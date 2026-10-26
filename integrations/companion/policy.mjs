import assert from 'node:assert/strict';
import {mkdirSync, readFileSync, writeFileSync, renameSync, existsSync} from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {windowsOf, occurrences, historyOf, dayOf, makePlan} from './upstream/night-plan.mjs';

export const DEFAULT_POLICY = Object.freeze({schemaVersion: 1, agentName: '银狼',
  style: 'Warm, concise, curious and candid. Distinguish facts from attributed impressions.',
  sessionId: '', enabled: false, paused: false,
  windows: [{start: '09:00', end: '22:00', wakes: 2}], skipChance: 0.2,
  minGapMin: 180, idleMinutes: 20, activities: ['consolidate', 'reflect'],
  budget: {dailyRuns: 2, steps: 8, toolCalls: 16, writes: 3, outputTokens: 4096, seconds: 180},
  develop: {gapMin: 0, idleMinutes: 0, dailyRuns: 0}});

function keys(value, allowed) {
  assert(value && typeof value === 'object' && !Array.isArray(value), 'Expected object');
  for (const key of Object.keys(value)) assert(allowed.includes(key), 'Unknown field: ' + key);
}
function integer(value, low, high, name) {
  assert(Number.isInteger(value) && value >= low && value <= high, 'Invalid ' + name);
}
export function validatePolicy(input) {
  keys(input, Object.keys(DEFAULT_POLICY));
  assert.equal(input.schemaVersion, 1, 'Unsupported behavior schema');
  assert(typeof input.agentName === 'string' && input.agentName.trim() === input.agentName &&
    input.agentName.length > 0 && input.agentName.length <= 40 && !/[\r\n\x00-\x1f]/.test(input.agentName), 'Invalid Agent name');
  assert(typeof input.style === 'string' && input.style.length <= 2000, 'Invalid style');
  assert(typeof input.sessionId === 'string' && (input.sessionId === '' || /^session-[a-zA-Z0-9-]{1,100}$/.test(input.sessionId)), 'Invalid session');
  for (const key of ['enabled', 'paused']) assert.equal(typeof input[key], 'boolean', 'Invalid ' + key);
  assert(!input.enabled || input.sessionId, 'Select an existing conversation first');
  assert(Array.isArray(input.windows) && input.windows.length > 0 && input.windows.length <= 8, 'Invalid windows');
  for (const window of input.windows) {
    keys(window, ['start', 'end', 'wakes']); integer(window.wakes, 0, 12, 'window wakes');
    assert(typeof window.start === 'string' && typeof window.end === 'string', 'Invalid window time');
  }
  windowsOf(input);
  assert(Number.isFinite(input.skipChance) && input.skipChance >= 0 && input.skipChance <= 1, 'Invalid skip chance');
  integer(input.minGapMin, 1, 10080, 'minimum gap'); integer(input.idleMinutes, 1, 1440, 'idle minutes');
  assert(Array.isArray(input.activities) && input.activities.length > 0 && input.activities.length <= 3 &&
    new Set(input.activities).size === input.activities.length &&
    input.activities.every(x => ['consolidate', 'reflect', 'develop'].includes(x)), 'Invalid activities');
  keys(input.budget, Object.keys(DEFAULT_POLICY.budget));
  for (const [key, low, high] of [['dailyRuns',1,12],['steps',1,32],['toolCalls',1,64],['writes',0,10],['outputTokens',256,16384],['seconds',10,600]])
    integer(input.budget[key], low, high, key);
  if (input.develop !== undefined) {
    keys(input.develop, ['gapMin','idleMinutes','dailyRuns']);
    integer(input.develop.gapMin, 0, 1440, 'develop gap');
    integer(input.develop.idleMinutes, 0, 1440, 'develop idle');
    integer(input.develop.dailyRuns, 0, 10000, 'develop daily runs');
  }
  return structuredClone(input);
}

export function atomicJson(file, value) {
  mkdirSync(path.dirname(file), {recursive: true, mode: 0o700});
  const temp = file + '.tmp';
  writeFileSync(temp, JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  renameSync(temp, file);
}
export function loadJson(file, initial) {
  try { return JSON.parse(readFileSync(file, 'utf8')); }
  catch (error) { if (error.code === 'ENOENT') return structuredClone(initial); throw error; }
}

/** Immutable validated versions plus an atomic pointer; malformed active data is an error. */
export class PolicyStore {
  constructor(directory) { this.directory = directory; this.file = path.join(directory, 'behavior.json'); }
  read() { return validatePolicy(loadJson(this.file, DEFAULT_POLICY)); }
  save(value, expectedRevision) {
    const previous = this.read();
    if (expectedRevision !== undefined) assert.equal(this.revision(previous), expectedRevision, 'Behavior changed; reload before saving');
    const policy = validatePolicy(value);
    for (const item of [previous, policy]) {
      const file = path.join(this.directory, 'versions', this.revision(item) + '.json');
      if (!existsSync(file)) atomicJson(file, item);
    }
    atomicJson(this.file, policy);
    return policy;
  }
  revision(value = this.read()) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
  restore(revision, expectedRevision) {
    assert(/^[0-9a-f]{64}$/.test(revision), 'Invalid version');
    const value = validatePolicy(loadJson(path.join(this.directory, 'versions', revision + '.json'), null));
    assert.equal(this.revision(value), revision, 'Behavior version hash mismatch');
    return this.save(value, expectedRevision);
  }
}

// Named operations follow dsh-mnemon 0.5.34's MEMORY_TOOLS table. Generic write
// dispatch is excluded: a scoped policy must know which operation it is admitting.
export const READ_TOOLS = new Set(['mnemon_memory_bodies','mnemon_recall','mnemon_related',
  'mnemon_status','mnemon_document_search','mnemon_view_route']);
export const WRITE_TOOLS = new Set(['mnemon_document_create','mnemon_runtime_memory',
  'mnemon_remember','mnemon_link']);
// Command surface admitted to an autonomous development turn. Anything outside
// this list is refused before execution; network, privilege and main-push forms
// are rejected even when they match the general shape.
export const DEVELOP_COMMAND = /^(?:git (?:status|diff|log|add|commit|push|switch|checkout|branch|rev-parse|rev-list)\b|node (?:tooling\/|tests\/)|bash (?:tooling\/|tests\/)|ls\b|cat\b|mkdir\b|rm -rf? (?:artifacts|\.tmp|dist)\b)/;

// Development turns keep the policy budget but get a floor that a build and a
// verification pass can actually fit in. Memory activities are unchanged.
export function limitsForActivity(limits, activity) {
  const result = {...limits};
  if (activity === 'develop') {
    result.steps = Math.max(result.steps, 1000);
    result.toolCalls = Math.max(result.toolCalls, 5000);
    result.writes = Math.max(result.writes, 500);
    result.seconds = Math.max(result.seconds, 36000);
    result.outputTokens = Math.max(result.outputTokens, 65536);
  }
  return result;
}

export class TurnBudget {
  constructor(limits, activity = 'consolidate') {
    this.limits = {...limits}; this.activity = activity;
    this.steps = 0; this.tools = 0; this.writes = 0;
  }
  step() { if(this.steps>=this.limits.steps)return false;this.steps++;return true; }
  tool(name, input) {
    if (this.tools >= this.limits.toolCalls) return 'Autonomous tool-call budget exhausted.';
    if (this.activity === 'develop') return this.developTool(name, input);
    if (!READ_TOOLS.has(name) && !WRITE_TOOLS.has(name)) return 'Autonomous local maintenance only permits bounded Mnemon reads and non-destructive writes.';
    if (name === 'mnemon_runtime_memory' && input?.action !== 'add')
      return 'Autonomous maintenance cannot remove or replace existing hot memory; preserve the source evidence.';
    if (WRITE_TOOLS.has(name) && this.writes >= this.limits.writes) return 'Autonomous memory-write budget exhausted.';
    this.tools++; if (WRITE_TOOLS.has(name)) this.writes++;
  }
  developTool(name, input) {
    if (['read','glob','grep'].includes(name)) { this.tools++; return; }
    if (['write','edit'].includes(name)) {
      if (this.writes >= this.limits.writes) return 'Autonomous write budget exhausted.';
      this.tools++; this.writes++; return;
    }
    if (['self_experience', 'self_note', 'self_close', 'self_stance', 'extend_self'].includes(name)) {
      if (this.writes >= this.limits.writes) return 'Autonomous write budget exhausted.';
      this.tools++; this.writes++; return;
    }
    if (name !== 'bash') return 'Autonomous development permits only repository read/write tools, self-development tools and allowlisted commands.';
    const command = typeof input?.command === 'string' ? input.command : '';
    if (!DEVELOP_COMMAND.test(command)) return 'Autonomous development permits only allowlisted repository commands.';
    if (/\bgit push\b/.test(command) && /\b(?:main|master)\b/.test(command)) return 'Autonomous development never pushes main.';
    if (/\bgit push\b/.test(command) && /(?:--force\b|\s-f\b)/.test(command)) return 'Autonomous development never force-pushes.';
    if (/(?:^|[^\w-])(?:ssh|sudo|curl|wget|nc|ncat|scp|sftp|telnet)(?:[^\w-]|$)/.test(command))
      return 'Autonomous development never uses network, upload or privilege commands.';
    const publication = /^git (?:add|commit|push)\b/.test(command);
    if (publication && this.writes >= this.limits.writes) return 'Autonomous publication budget exhausted.';
    this.tools++; if (publication) this.writes++;
  }
}

// At-most-once recovery: an interrupted run is never replayed, but the next
// wake is brought forward so a fresh turn can inspect actual state and continue.
export function recoveryTarget(policy, state, now = Date.now()) {
  const interrupted = Array.isArray(state?.runs) &&
    state.runs.some(run => run.status === 'interrupted-unreplayed');
  if (!interrupted || !policy.enabled || policy.paused || !policy.sessionId) return null;
  const current = occurrences(policy, now).find(w => w.startsAt <= now && now < w.endsAt);
  if (current) return {atMs: now + 60000, windowKey: current.key, window: current.label};
  const next = occurrences(policy, now).sort((a, b) => a.startsAt - b.startsAt)[0];
  return next ? {atMs: next.startsAt + 60000, windowKey: next.key, window: next.label} : null;
}

export function recoveryPlan(policy, state, now = Date.now()) {
  const current = occurrences(policy, now).find(w => w.startsAt <= now && now < w.endsAt);
  if (!current) return null;
  const at = new Date(now);
  return {...historyOf(state, now), schema: 2, action: 'wake', fired: false, woke: false,
    atMs: now - 1, at: at.getHours() * 60 + at.getMinutes(),
    atText: `${new Date(now).toISOString().slice(0, 10)} ${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`,
    window: current.label, windowKey: current.key, startsAt: current.startsAt,
    endsAt: current.endsAt, withinWindow: current.label + ' 恢复执行',
    recovery: true, createdAt: new Date(now).toISOString()};
}

// The first activity of a day should not wait for a random afternoon slot that
// may have been planned before the policy changed. Move it to the earliest
// allowed moment (or now) and never let the first slot be a random skip.
export function firstWakeSoon(state, plan, now = Date.now()) {
  if (!plan || plan.fired || !['wake', 'skip'].includes(plan.action)) return plan;
  if ((state?.daily?.[dayOf(now)] ?? 0) > 0) return plan;
  if (!Number.isFinite(plan.startsAt) || !Number.isFinite(plan.endsAt)) return plan;
  const earliest = Math.max(now + 60000, plan.startsAt + 60000);
  if (earliest >= plan.endsAt - 30000) return plan;
  if (plan.action === 'wake' && earliest >= plan.atMs) return plan;
  const at = new Date(earliest);
  return {...plan, action: 'wake', atMs: earliest, at: at.getHours() * 60 + at.getMinutes(),
    atText: `${dayOf(earliest)} ${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`,
    firstWake: true};
}

// The development activity has its own cadence: it starts as soon as the gap
// after the previous run allows (and the assistant is idle), instead of waiting
// for a random daily slot meant for companionship activities.
// Upstream makePlan only defaults an omitted state, not an explicit null, so a
// cleared plan (first activity of a day after a restart) must be normalised here.
export function planFor(policy, previous, now = Date.now()) {
  return makePlan(policy, previous ?? {}, now);
}

export function developDue(policy, state, now = Date.now()) {
  if (!policy.enabled || policy.paused || !policy.sessionId) return {due: false, reason: 'inactive'};
  if (!Array.isArray(policy.activities) || !policy.activities.includes('develop'))
    return {due: false, reason: 'disabled'};
  const config = {...DEFAULT_POLICY.develop, ...(policy.develop ?? {})};
  const period = state?.period;
  if (period && (period.phase === 'peak' || period.phase === 'valley')) {
    const reported = Date.parse(period.reportedAt ?? '');
    const fresh = Number.isFinite(reported) && now - reported < 6 * 3600000;
    if (fresh && period.phase === 'peak') return {due: false, reason: 'peak'};
  }
  const today = dayOf(now);
  const used = state?.developDaily?.[today] ?? 0;
  if (config.dailyRuns > 0 && used >= config.dailyRuns) return {due: false, reason: 'daily'};
  const last = Number.isFinite(state?.lastDevelopAt) ? state.lastDevelopAt : 0;
  const waitMs = last + config.gapMin * 60000 - now;
  if (waitMs > 0) return {due: false, waitMs, reason: 'gap'};
  return {due: true, idleMinutes: config.idleMinutes, reason: 'ready'};
}

export function activityPrompt(activity, record, situation = '') {
  if (activity === 'develop') return `[Sliver Wolf local activity ${record.id}; develop]\n` +
    (situation ? situation + '\n\n' : '') +
    'This is a scheduled self-development activity in our existing conversation, not a new identity. ' +
    'Read the situation above, then decide what, if anything, is worth doing. If you act, make it one bounded step: inspect, implement, verify, commit and push to agent/dev. ' +
    'When something felt wrong, missing or worth exploring, append it to your self-notes ledger so the next turn can see it. ' +
    'Work only inside this repository and only on the agent/dev branch. ' +
    'Never push main, never force-push, never restart DSH, never install an APK, never touch credentials, never use network or privilege commands. ' +
    'Stop on the first failure and record it instead of retrying. ' +
    `At most ${record.limits.writes} writes, ${record.limits.toolCalls} tool calls and ${record.limits.steps} model steps. ` +
    'Finish with a short factual outcome and the evidence paths.';
  return `[Sliver Wolf local activity ${record.id}; ${activity}]\n` +
    'This is a scheduled activity in our existing conversation, not a new identity. ' +
    'Inspect the available Mnemon sources and semantically recall relevant evidence first. ' +
    (activity === 'consolidate' ?
      'Consolidate useful existing evidence into one concise sourced document if it adds value; preserve original records and conflicting facts. ' :
      'Reflect briefly on a real shared experience or a useful association. Mark interpretations as attributed observations, never as established facts. ') +
    'Separate shared experiences, general knowledge and attributed observations with explicit document titles and source references. ' +
    'Include source record IDs, conversation provenance and date. Do not copy the same information into multiple layers. ' +
    'Do not invent events, store secrets, delete memories, browse accounts, execute commands, or start another Agent. ' +
    `At most ${record.limits.writes} memory mutations and ${record.limits.steps} model steps. ` +
    'No useful new evidence means no write. Finish with a short factual outcome and source references.';
}
