import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync, rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {DEFAULT_POLICY, validatePolicy, PolicyStore, TurnBudget} from '../integrations/companion/policy.mjs';
import {makePlan, completePlan} from '../integrations/companion/upstream/night-plan.mjs';

test('behavior rejects unknown fields, invalid budgets and missing selected session', () => {
  assert.deepEqual(validatePolicy(DEFAULT_POLICY), DEFAULT_POLICY);
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, enabled:true}));
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, remoteUrl:'https://example.com'}));
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, budget:{...DEFAULT_POLICY.budget,writes:0.5}}));
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, windows:[{start:'25:00',end:'26:00',wakes:1}]}));
});
test('immutable behavior recovery validates hashes and preserves optimistic concurrency', () => {
  const root = mkdtempSync(path.join(os.tmpdir(),'sw-behavior-'));
  try {
    const store = new PolicyStore(root), first = store.read(), revision = store.revision();
    const changed = store.save({...first, agentName:'Test'}, revision);
    assert.throws(() => store.save(first, revision));
    assert.deepEqual(store.restore(revision, store.revision(changed)),first);
    writeFileSync(path.join(root,'versions',revision+'.json'),JSON.stringify(changed));
    assert.throws(() => store.restore(revision));
    writeFileSync(path.join(root,'behavior.json'),'{broken');
    assert.throws(() => store.read());
  } finally {rmSync(root,{recursive:true});}
});
test('reused planner preserves random reservation and window counts across midnight', () => {
  const now = new Date(2026,9,25,23,50).getTime();
  const config = {...DEFAULT_POLICY,windows:[{start:'23:00',end:'02:00',wakes:2}],skipChance:0,minGapMin:10};
  const plan = makePlan(config,{},now,()=>0.5);
  assert.equal(makePlan(config,plan,now+1000,()=>0),plan);
  const completed = completePlan(plan,plan.atMs);
  assert.equal(completed.usedCount[plan.windowKey],1);
  assert.deepEqual(completePlan(completed,plan.atMs+1000),completed);
  const next = makePlan(config,completed,plan.atMs+1000,()=>0);
  assert(next.atMs >= plan.atMs + 600000);
});
test('autonomous guards count attempted writes and reject unbounded dispatch', () => {
  const b = new TurnBudget({...DEFAULT_POLICY.budget,steps:2,writes:1,toolCalls:3});
  assert.equal(b.tool('mnemon_recall'),undefined);
  assert.equal(b.tool('mnemon_remember'),undefined);
  assert.match(b.tool('mnemon_remember'),/write-budget|write budget/);
  assert.match(b.tool('run_code'),/only permits/);
  assert.match(b.tool('mnemon_view_action'),/only permits/);
  assert.match(b.tool('mnemon_runtime_memory',{action:'remove'}),/cannot remove/);
  assert.equal(b.tool('mnemon_related'),undefined);
  assert.match(b.tool('mnemon_recall'),/tool-call budget/);
  assert(b.step()); assert(b.step()); assert(!b.step());
  assert.equal(b.steps,2,'Denied entry must not count as an executed model step');
});
test('develop is accepted in the activity list, up to three distinct entries', () => {
  assert.equal(validatePolicy({...DEFAULT_POLICY, activities:['develop']}).activities.length, 1);
  const three = {...DEFAULT_POLICY, activities:['consolidate','reflect','develop']};
  assert.deepEqual(validatePolicy(three).activities, ['consolidate','reflect','develop']);
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, activities:['develop','develop']}));
  assert.throws(() => validatePolicy({...DEFAULT_POLICY, activities:['develop','unknown']}));
});
test('develop activity admits bounded repository tools and refuses escalation', () => {
  const limits = {...DEFAULT_POLICY.budget, steps: 8, writes: 2, toolCalls: 20};
  const budget = new TurnBudget(limits, 'develop');
  assert.equal(budget.tool('read', {file_path:'README.md'}), undefined);
  assert.equal(budget.tool('glob', {pattern:'docs/*.md'}), undefined);
  assert.equal(budget.tool('bash', {command:'git status --short'}), undefined);
  assert.equal(budget.tool('bash', {command:'node tests/relay-check.mjs'}), undefined);
  assert.equal(budget.tool('write', {file_path:'docs/x.md'}), undefined);
  assert.equal(budget.tool('edit', {file_path:'docs/x.md'}), undefined);
  assert.match(budget.tool('write', {file_path:'docs/y.md'}), /write budget/);
  assert.match(budget.tool('bash', {command:'git push origin main'}), /never pushes main/);
  assert.match(budget.tool('bash', {command:'git push --force origin agent/dev'}), /never force-pushes/);
  assert.match(budget.tool('bash', {command:'curl https://example.com'}), /only allowlisted/);
  assert.match(budget.tool('bash', {command:'sudo id'}), /only allowlisted/);
  assert.match(budget.tool('bash', {command:'rm -rf /'}), /only allowlisted/);
  assert.match(budget.tool('web_search'), /only repository/);
  const publisher = new TurnBudget({...limits, writes:5}, 'develop');
  assert.equal(publisher.tool('bash', {command:'git commit -m x'}), undefined);
});
test('develop prompt states the branch, red lines and evidence expectation', async () => {
  const {activityPrompt} = await import('../integrations/companion/policy.mjs');
  const text = activityPrompt('develop', {id:'r1', limits:{writes:2, toolCalls:16, steps:8}});
  assert.match(text, /agent\/dev/);
  assert.match(text, /Never push main/);
  assert.match(text, /never restart DSH/);
  assert.match(text, /evidence paths/);
});
test('develop turns get a practical budget floor without changing memory budgets', async () => {
  const {limitsForActivity} = await import('../integrations/companion/policy.mjs');
  const base = {...DEFAULT_POLICY.budget};
  assert.deepEqual(limitsForActivity(base, 'consolidate'), base);
  const develop = limitsForActivity(base, 'develop');
  assert.equal(develop.steps, 1000);
  assert.equal(develop.toolCalls, 5000);
  assert.equal(develop.writes, 500);
  assert.equal(develop.seconds, 36000);
  assert.equal(develop.outputTokens, 65536);
  assert.equal(develop.dailyRuns, base.dailyRuns);
  const larger = {...base, steps: 2000, seconds: 40000};
  const kept = limitsForActivity(larger, 'develop');
  assert.equal(kept.steps, 2000);
  assert.equal(kept.seconds, 40000);
});
test('recovery wake brings the next turn forward without replaying the interrupted one', async () => {
  const {recoveryTarget, recoveryPlan} = await import('../integrations/companion/policy.mjs');
  const policy = {...DEFAULT_POLICY, enabled:true, sessionId:'session-test',
    windows:[{start:'09:00',end:'22:00',wakes:2}]};
  const interrupted = {runs:[{status:'interrupted-unreplayed'}], usedCount:{}};
  const inside = new Date(2026,9,26,10,0).getTime();
  const target = recoveryTarget(policy, interrupted, inside);
  assert.ok(target, 'inside a window a recovery target must exist');
  assert.equal(target.atMs, inside + 60000);
  assert.equal(recoveryTarget(policy, {runs:[{status:'completed'}]}, inside), null);
  assert.equal(recoveryTarget({...policy, enabled:false}, interrupted, inside), null);
  assert.equal(recoveryTarget({...policy, paused:true}, interrupted, inside), null);

  const outside = new Date(2026,9,26,3,0).getTime();
  const deferred = recoveryTarget(policy, interrupted, outside);
  assert.ok(deferred.atMs > outside);
  assert.equal(deferred.atMs, new Date(2026,9,26,9,0).getTime() + 60000);

  const key = target.windowKey;
  const plan = recoveryPlan(policy, {usedCount:{[key]:1}}, inside);
  assert.ok(plan, 'inside a window a recovery plan must exist');
  assert.equal(plan.action, 'wake');
  assert.equal(plan.recovery, true);
  assert.ok(plan.atMs <= inside);
  assert.equal(plan.usedCount[key], 1, 'history must be preserved');
});
test('first activity of the day moves to the earliest allowed moment', async () => {
  const {firstWakeSoon} = await import('../integrations/companion/policy.mjs');
  const startsAt = new Date(2026,9,26,9,0).getTime();
  const endsAt = new Date(2026,9,26,22,0).getTime();
  const plan = {schema:2, action:'wake', fired:false, atMs:new Date(2026,9,26,13,0).getTime(),
    startsAt, endsAt, windowKey:'2026-10-26|09:00–22:00'};
  const night = new Date(2026,9,26,3,0).getTime();
  const moved = firstWakeSoon({daily:{}}, plan, night);
  assert.equal(moved.atMs, startsAt + 60000);
  assert.equal(moved.action, 'wake');
  assert.equal(moved.firstWake, true);

  const morning = new Date(2026,9,26,10,0).getTime();
  const nowMoved = firstWakeSoon({daily:{}}, plan, morning);
  assert.equal(nowMoved.atMs, morning + 60000);

  assert.equal(firstWakeSoon({daily:{'2026-10-26':1}}, plan, night), plan);
  assert.equal(firstWakeSoon({daily:{}}, {...plan, fired:true}, night).atMs, plan.atMs);
  assert.equal(firstWakeSoon({daily:{}}, {...plan, atMs:night + 30000}, night).atMs, night + 30000);

  const skip = firstWakeSoon({daily:{}}, {...plan, action:'skip', atMs:endsAt - 3600000}, night);
  assert.equal(skip.action, 'wake');
  assert.equal(skip.atMs, startsAt + 60000);
});
test('development activity has its own gap-based cadence', async () => {
  const {developDue} = await import('../integrations/companion/policy.mjs');
  const policy = {...DEFAULT_POLICY, enabled:true, sessionId:'session-test',
    develop:{gapMin:5, idleMinutes:1, dailyRuns:2}};
  const now = new Date(2026,9,26,10,0).getTime();
  assert.equal(developDue(policy, {}, now).due, true);
  assert.equal(developDue({...policy, enabled:false}, {}, now).due, false);
  assert.equal(developDue({...policy, paused:true}, {}, now).due, false);
  assert.equal(developDue({...policy, sessionId:''}, {}, now).due, false);

  const waiting = developDue(policy, {lastDevelopAt: now - 2 * 60000}, now);
  assert.equal(waiting.due, false);
  assert.equal(waiting.reason, 'gap');
  assert.equal(waiting.waitMs, 3 * 60000);

  const ready = developDue(policy, {lastDevelopAt: now - 6 * 60000}, now);
  assert.equal(ready.due, true);
  assert.equal(ready.idleMinutes, 1);

  const capped = developDue(policy, {developDaily:{'2026-10-26':2}}, now);
  assert.equal(capped.due, false);
  assert.equal(capped.reason, 'daily');
});
test('develop cadence can be unlimited', async () => {
  const {developDue, DEFAULT_POLICY, limitsForActivity} = await import('../integrations/companion/policy.mjs');
  const policy = {...DEFAULT_POLICY, enabled:true, sessionId:'session-test',
    develop:{gapMin:0, idleMinutes:0, dailyRuns:0}};
  const now = new Date(2026,9,26,10,0).getTime();
  assert.equal(developDue(policy, {lastDevelopAt: now - 1000, developDaily:{'2026-10-26':500}}, now).due, true);
  const limits = limitsForActivity(DEFAULT_POLICY.budget, 'develop');
  assert.ok(limits.steps >= 1000 && limits.toolCalls >= 5000 && limits.writes >= 500 && limits.seconds >= 36000);
});
test('a cleared plan is normalised before upstream planning', async () => {
  const {planFor, DEFAULT_POLICY} = await import('../integrations/companion/policy.mjs');
  const policy = {...DEFAULT_POLICY, enabled:true, sessionId:'session-test',
    windows:[{start:'09:00',end:'22:00',wakes:2}]};
  const now = new Date(2026,9,26,10,0).getTime();
  const plan = planFor(policy, null, now);
  assert.ok(plan && ['wake','skip'].includes(plan.action));
});
