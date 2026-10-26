import test from 'node:test';
import assert from 'node:assert/strict';
import {appendFileSync, existsSync, mkdtempSync, rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {appendExperience, experienceStats, readExperiences, recentExperiences} from '../integrations/companion/experience.mjs';
import {candidateSituation, deriveCandidates, pickCandidate} from '../integrations/companion/candidates.mjs';
import {addStance, bottleneckMet, emptyGrowth, growthDay, growthSummary, STAGES, updateGrowth} from '../integrations/companion/growth.mjs';
import {buildSelfDoc} from '../integrations/companion/self-doc.mjs';
import {listExtensions, markExtension, scaffoldExtension} from '../integrations/companion/extend-self.mjs';
import {appendNote, updateNote} from '../integrations/companion/self-notes.mjs';

const day = 86400000;
const now = new Date('2026-10-26T05:00:00Z').getTime();
const at = (ms) => new Date(now - ms).toISOString();

function tempDir(prefix) { return mkdtempSync(path.join(os.homedir(), prefix)); }

test('experience stream appends, validates and reports recent entries', () => {
  const directory = tempDir('.exp-');
  try {
    const file = path.join(directory, 'experience.jsonl');
    const first = appendExperience(file, {kind: 'failure', summary: 'build failed', severity: 4, tags: ['build'], source: 'host'}, new Date(now - day));
    appendExperience(file, {kind: 'friction', summary: 'manual step', tags: ['dev'], source: 'agent'}, new Date(now));
    assert.throws(() => appendExperience(file, {kind: 'nope', summary: 'x'}));
    assert.throws(() => appendExperience(file, {kind: 'failure', summary: '  '}));
    appendFileSync(file, 'not json\n');
    const read = readExperiences(file);
    assert.equal(read.experiences.length, 2);
    assert.equal(read.experiences[0].id, first.id);
    assert.equal(read.skipped, 1);
    assert.equal(recentExperiences(file, {sinceMs: 2 * day, now}).length, 2);
    assert.equal(recentExperiences(file, {sinceMs: 2 * day, kinds: ['failure'], now}).length, 1);
    assert.deepEqual(experienceStats(file, now).byKind, {failure: 1, friction: 1});
  } finally { rmSync(directory, {recursive: true, force: true}); }
});

test('candidates are derived from real evidence and stay quiet when empty', () => {
  const experiences = [
    {id: 'f1', kind: 'failure', summary: 'build failed', severity: 4, tags: [], refs: [], atMs: now - day, at: at(day)},
    {id: 'f2', kind: 'failure', summary: 'old resolved', severity: 2, tags: [], refs: [], atMs: now - 20 * day, at: at(20 * day)},
    {id: 'r1', kind: 'result', summary: 'fixed f1', severity: 2, tags: [], refs: ['f1'], atMs: now - 1000, at: at(1000)},
  ];
  const notes = [
    {id: 'n1', kind: 'friction', text: '重复手动步骤', tags: ['manual'], status: 'open', at: at(2 * day)},
    {id: 'n2', kind: 'friction', text: '又是手动步骤', tags: ['manual'], status: 'open', at: at(day)},
  ];
  const candidates = deriveCandidates({experiences, notes, runs: [], now});
  assert.ok(candidates.length > 0);
  assert.ok(candidates.some((candidate) => candidate.kind === 'pattern' && candidate.title.includes('manual')));
  assert.ok(!candidates.some((candidate) => candidate.kind === 'failure' && candidate.situation.includes('old resolved')));
  const picked = pickCandidate(candidates, {runs: []});
  assert.ok(picked && picked.id);
  assert.match(candidateSituation(picked, now), /Candidate:/);
  assert.deepEqual(deriveCandidates({experiences: [], notes: [], runs: [], now}), []);
});

test('attempted candidates are skipped while recent', () => {
  const experiences = [{id: 'f9', kind: 'failure', summary: 'still broken', severity: 4, tags: [], refs: [], atMs: now - day, at: at(day)}];
  const candidates = deriveCandidates({experiences, notes: [], runs: [], now});
  const picked = pickCandidate(candidates, {runs: []});
  const runs = [{candidateId: picked.id}];
  assert.equal(pickCandidate(candidates, {runs}), null);
});

test('growth advances only when a bottleneck has evidence', () => {
  let growth = emptyGrowth(now);
  assert.equal(growth.stage, 1);
  assert.equal(bottleneckMet(1, {experiences: [], runs: []}), false);
  const observation = {id: 'e1', kind: 'observation', summary: 'noticed', source: 'agent', atMs: now};
  ({growth} = updateGrowth(growth, {experiences: [observation], runs: [], notes: [], now}));
  assert.equal(growth.stage, 2);
  const fixRun = {id: 'r1', status: 'completed', candidateKind: 'failure'};
  ({growth} = updateGrowth(growth, {experiences: [observation], runs: [fixRun], notes: [], now}));
  assert.equal(growth.stage, 3);
  const extension = {id: 'e2', kind: 'extension', summary: 'new module', atMs: now};
  ({growth} = updateGrowth(growth, {experiences: [observation, extension], runs: [fixRun], notes: [], now}));
  assert.equal(growth.stage, 4);
  assert.equal(STAGES[3].stage, 4);
  assert.match(growthSummary(growth, {experiences: [observation, extension], runs: [fixRun]}), /阶段 4\/4/);
});

test('stances need stage 3, evidence and a unique text', () => {
  const growth = {...emptyGrowth(now), stage: 2, stances: []};
  assert.throws(() => addStance(growth, {text: '先写测试', evidence: ['e1']}), /stage 3/);
  growth.stage = 3;
  assert.throws(() => addStance(growth, {text: '先写测试', evidence: []}), /evidence/);
  const stance = addStance(growth, {text: '先写测试再改代码', evidence: ['e1']});
  assert.equal(stance.text, '先写测试再改代码');
  assert.equal(addStance(growth, {text: '先写测试再改代码', evidence: ['e2']}).id, stance.id);
});

test('closed notes with a repeated tag promote a stance from stage 3', async () => {
  const directory = tempDir('.growth-');
  try {
    const file = path.join(directory, 'notes.jsonl');
    const a = appendNote(file, {kind: 'idea', text: 'verify before commit', tags: ['verify']});
    const b = appendNote(file, {kind: 'idea', text: 'always run the test first', tags: ['verify']});
    updateNote(file, a.id, 'done');
    updateNote(file, b.id, 'done');
    const notes = (await import('../integrations/companion/self-notes.mjs')).readNotes(file).notes;
    const {growth} = updateGrowth({...emptyGrowth(now), stage: 3}, {experiences: [], runs: [], notes, now});
    assert.equal(growth.stances.length, 1);
    assert.match(growth.stances[0].text, /test|commit/);
  } finally { rmSync(directory, {recursive: true, force: true}); }
});

test('self document states facts and bounds itself', () => {
  const growth = {...emptyGrowth(now), stage: 3, day: 9, stances: [{id: 's1', text: '先验证再提交'}]};
  const doc = buildSelfDoc({growth, notes: [{id: 'n1', kind: 'need', text: '缺少处境信息', status: 'open'}],
    experiences: [{id: 'e1', kind: 'failure', summary: 'build failed'}], policy: {}}, new Date(now), 4000);
  assert.match(doc, /我是谁/);
  assert.match(doc, /阶段 3\/4/);
  assert.match(doc, /先验证再提交/);
  assert.match(doc, /缺少处境信息/);
  assert.match(doc, /build failed/);
  assert.match(doc, /self_experience/);
  const bounded = buildSelfDoc({growth, notes: [], experiences: [], policy: {}}, new Date(now), 150);
  assert.ok(bounded.length <= 150);
});

test('extension scaffolding creates a real module and refuses duplicates', () => {
  const directory = tempDir('.ext-');
  try {
    const result = scaffoldExtension({name: 'voice-queue', requirement: 'queue speech instead of dropping it', root: directory, now: new Date(now)});
    assert.ok(existsSync(path.join(directory, 'voice-queue', 'index.mjs')));
    assert.ok(existsSync(path.join(directory, 'voice-queue', 'test', 'contract.test.mjs')));
    assert.throws(() => scaffoldExtension({name: 'voice-queue', requirement: 'x', root: directory}), /already exists/);
    assert.throws(() => scaffoldExtension({name: 'Bad Name', requirement: 'x', root: directory}), /kebab/);
    assert.equal(listExtensions(directory).length, 1);
    assert.equal(markExtension(directory, 'voice-queue', 'verified').status, 'verified');
    assert.throws(() => markExtension(directory, 'voice-queue', 'weird'), /Unknown extension status/);
  } finally { rmSync(directory, {recursive: true, force: true}); }
});
