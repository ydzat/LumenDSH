import assert from 'node:assert/strict';
import {mkdirSync, readFileSync, writeFileSync, renameSync, existsSync} from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {windowsOf} from './upstream/night-plan.mjs';

export const DEFAULT_POLICY = Object.freeze({schemaVersion: 1, agentName: '银狼',
  style: 'Warm, concise, curious and candid. Distinguish facts from attributed impressions.',
  sessionId: '', enabled: false, paused: false,
  windows: [{start: '09:00', end: '22:00', wakes: 2}], skipChance: 0.2,
  minGapMin: 180, idleMinutes: 20, activities: ['consolidate', 'reflect'],
  budget: {dailyRuns: 2, steps: 8, toolCalls: 16, writes: 3, outputTokens: 4096, seconds: 180}});

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
  assert(Array.isArray(input.activities) && input.activities.length > 0 && input.activities.length <= 2 &&
    new Set(input.activities).size === input.activities.length && input.activities.every(x => ['consolidate', 'reflect'].includes(x)), 'Invalid activities');
  keys(input.budget, Object.keys(DEFAULT_POLICY.budget));
  for (const [key, low, high] of [['dailyRuns',1,12],['steps',1,32],['toolCalls',1,64],['writes',0,10],['outputTokens',256,16384],['seconds',10,600]])
    integer(input.budget[key], low, high, key);
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
export class TurnBudget {
  constructor(limits) { this.limits = {...limits}; this.steps = 0; this.tools = 0; this.writes = 0; }
  step() { if(this.steps>=this.limits.steps)return false;this.steps++;return true; }
  tool(name, input) {
    if (!READ_TOOLS.has(name) && !WRITE_TOOLS.has(name)) return 'Autonomous local maintenance only permits bounded Mnemon reads and non-destructive writes.';
    if (name === 'mnemon_runtime_memory' && input?.action !== 'add')
      return 'Autonomous maintenance cannot remove or replace existing hot memory; preserve the source evidence.';
    if (this.tools >= this.limits.toolCalls) return 'Autonomous tool-call budget exhausted.';
    if (WRITE_TOOLS.has(name) && this.writes >= this.limits.writes) return 'Autonomous memory-write budget exhausted.';
    this.tools++; if (WRITE_TOOLS.has(name)) this.writes++;
  }
}

export function activityPrompt(activity, record) {
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
