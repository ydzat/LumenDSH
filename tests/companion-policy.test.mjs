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
