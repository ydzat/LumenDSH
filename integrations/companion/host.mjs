import path from 'node:path';
import os from 'node:os';
import {randomUUID} from 'node:crypto';
import {readdirSync} from 'node:fs';
import {PolicyStore, TurnBudget, atomicJson, loadJson, activityPrompt} from './policy.mjs';
import {makePlan, completePlan, dayOf} from './upstream/night-plan.mjs';
import {modelSnapshot, outputBudget, restoreOutput, selectModel} from './model-config.mjs';

export const name = 'sliver-wolf-companion';
export const inject = ['agents', 'sessionController', 'webServer', 'connection', 'llm', 'agentDefaultModel'];

/** DSH owns Agent execution and memory. This plugin only plans and bounds local work. */
export function apply(ctx, config = {}) {
  const root = config.directory ?? path.join(os.homedir(), 'sliver-wolf-companion', 'behavior');
  const store = new PolicyStore(root);
  let policy = store.read();
  const stateFile = path.join(root, 'state.json');
  let state = loadJson(stateFile, {schemaVersion: 1, plan: {}, runs: [], lastActivity: {}, daily: {}});
  if (state.schemaVersion !== 1 || !Array.isArray(state.runs)) throw new Error('Invalid companion state');
  state.outputOverrides??={};
  let disposed = false, ticking = false, active, dueTimer;
  const startedAt=Date.now();
  const observed = new Set();
  const save = () => atomicJson(stateFile, state);
  // Reservations are never replayed after a crash. The session log remains the
  // authority for any work accepted immediately before the process disappeared.
  for (const run of state.runs) if (['reserved', 'running'].includes(run.status)) {
    run.status = 'interrupted-unreplayed'; run.endedAt = new Date().toISOString();
  }
  save();

  function settle(status) {
    if (!active) return;
    const current = active; active = undefined;
    clearTimeout(current.timer);
    Object.assign(current.record, {status, endedAt: new Date().toISOString(),
      steps: current.budget.steps, tools: current.budget.tools, writesAttempted: current.budget.writes});
    save();
  }
  function stop(reason) {
    if (!active) return;
    const agent = active.agent;
    // A pending reservation has not entered a turn; never cancel unrelated work.
    if (active.turn !== undefined) {
      agent.cancel({kind: 'hook', reason}, {keepInbox: true}); settle(reason);
    } else {
      // The queued prompt must still be intercepted when its first step arrives.
      active.cancelReason = reason; clearTimeout(active.timer);
    }
  }
  function attach(agent) {
    if (observed.has(agent)) return;
    observed.add(agent);
    ctx.effect(() => {
      const disposers = [];
      disposers.push(agent.ctx.on('system-prompt/assemble', async (_assembly, context, next) => {
        const assembled = await next();
        if (agent.id !== policy.sessionId) return assembled;
        return {...assembled, sections: [...assembled.sections, {name: 'sliver-wolf-behavior',
          text: `The user's current configured name for you is ${JSON.stringify(policy.agentName)}. ` +
          `This updates your display identity without replacing your existing history or instructions.\nInteraction preferences: ${policy.style}\n` +
          'Keep shared experiences, general knowledge and attributed observations distinguishable. Use actual Mnemon sources for recollection; never fabricate personal history.'}]};
      }));
      disposers.push(agent.ctx.on('agent/pre-step', async (payload, next) => {
        const decision = await next();
        if (decision.kind !== 'enter') return decision;
        const own = active?.agent === agent ? active : undefined;
        const human = decision.messages.some(m => m.source?.kind === 'user' && m.source.rpcId !== own?.record.id);
        if (human) {
          state.lastActivity[agent.id] = Date.now();
          if (own) {
            const id = own.record.id; settle('yielded-to-user');
            // Mnemon's retrieval allowance belongs to the native turn. Transfer
            // claimed input through the native inbox into a fresh turn of this
            // same Agent, preserving request identities and other queued input.
            // cancel-before-followup latches the loop's documented wake-after-abort.
            agent.cancel({kind:'hook',reason:'Autonomous activity yielded to user input'}, {keepInbox:true});
            for(const message of payload.messages)if(message.source?.rpcId!==id)agent.followup(message);
            return {kind:'reject'};
          }
          save();
        }
        if (!own || active !== own) return decision;
        if (own.turn === undefined) {
          if (!decision.messages.some(m => m.source?.rpcId === own.record.id)) return decision;
          if (own.cancelReason) { settle(own.cancelReason); return {kind: 'reject'}; }
          own.turn = payload.turn; own.record.status = 'running'; save();
        }
        if (payload.turn !== own.turn) { settle('completed'); return decision; }
        if (policy.paused || !policy.enabled || !own.budget.step()) {
          settle(policy.paused ? 'paused' : 'step-budget'); return {kind: 'reject'};
        }
        return decision;
      }, {prepend: true}));
      disposers.push(agent.ctx.on('agent/request', async (payload, next) => {
        const proposed=await next();
        const request = restoreOutput(proposed,state.outputOverrides[agent.id]);
        if (active?.agent !== agent || active.turn !== payload.turn) {
          // Keep a restoration receipt until a later native header confirms the
          // cap is gone; cancellation may occur after this hook but before logging.
          if(state.outputOverrides[agent.id] && request===proposed) {delete state.outputOverrides[agent.id];save();}
          return request;
        }
        const own=active;
        const info=await ctx.llm.resolveModelInfo(request.provider,request.model,payload.signal);
        payload.signal.throwIfAborted();
        if(active!==own)return request;
        const maxTokens=outputBudget(request,info,own.record.limits.outputTokens);
        state.outputOverrides[agent.id]={provider:request.provider,model:request.model,
          original:request.maxTokens??null,applied:maxTokens};
        own.record.model={provider:request.provider,model:request.model,
          contextWindow:info.context?.contextWindow??null,defaultOutputTokens:info.defaultMaxTokens??null,maxTokens};
        save();
        return {...request,maxTokens};
      }));
      disposers.push(agent.ctx.tools.guard(execution => {
        if (active?.agent !== agent || active.turn === undefined) return;
        return active.budget.tool(execution.name,execution.arguments);
      }));
      disposers.push(agent.ctx.on('agent/turn-stopping', ({turn}) => {
        if (active?.agent === agent && active.turn === turn) settle('completed');
        state.lastActivity[agent.id] = Date.now(); save();
      }));
      disposers.push(agent.ctx.on('agent/error', ({turn,error}) => {
        if (active?.agent === agent && active.turn === turn) {
          active.record.failureCode = String(error?.code ?? error?.name ?? 'UNKNOWN');
          settle('failed');
        }
      }));
      return () => { for (const dispose of disposers.reverse()) dispose(); observed.delete(agent); };
    });
  }
  for (const agent of ctx.agents.list()) attach(agent);
  ctx.on('agent/created', ({agent}) => attach(agent));

  async function tick() {
    if (disposed || ticking || active || !policy.enabled || policy.paused) return;
    ticking = true;
    try {
      const now = Date.now(), today = dayOf(now);
      if ((state.daily[today] ?? 0) >= policy.budget.dailyRuns) return;
      const previous = state.plan;
      state.plan = makePlan(policy, previous, now);
      if (state.plan !== previous) {
        save(); clearTimeout(dueTimer);
        if (state.plan.atMs > now) dueTimer = setTimeout(() => void tick(), state.plan.atMs - now);
      }
      if (!['wake', 'skip'].includes(state.plan.action) || state.plan.fired || state.plan.atMs > now) return;
      if (state.plan.action === 'skip') { state.plan = completePlan(state.plan, now); save(); return; }
      const result = await ctx.sessionController.resolveAgent(policy.sessionId);
      if (!result.agent) throw new Error('Selected conversation unavailable');
      const agent = result.agent;
      attach(agent);
      // Restart begins a fresh inactivity interval; it cannot immediately wake an
      // old conversation merely because there is no process-local activity cache.
      if (!state.lastActivity[agent.id]) { state.lastActivity[agent.id] = now; save(); return; }
      if (agent.status !== 'idle' || now - Math.max(startedAt,state.lastActivity[agent.id]) < policy.idleMinutes * 60000) return;
      if (disposed || policy.paused || !policy.enabled || policy.sessionId !== agent.id) return;
      const limits = {...policy.budget};
      const record = {id: randomUUID(), sessionId: agent.id, status: 'reserved',
        activity: policy.activities[state.runs.length % policy.activities.length],
        startedAt: new Date(now).toISOString(), limits};
      state.plan = completePlan(state.plan, now);
      state.daily[today] = (state.daily[today] ?? 0) + 1;
      for (const day of Object.keys(state.daily)) if (day < dayOf(now - 7 * 86400000)) delete state.daily[day];
      state.runs.push(record); state.runs = state.runs.slice(-100);
      save(); // Reserve before I/O. An uncertain delivery is not automatically retried.
      active = {record, agent, budget: new TurnBudget(limits)};
      active.timer = setTimeout(() => stop('time-budget'), limits.seconds * 1000);
      try {
        await ctx.sessionController.prompt({requestId: record.id, sessionId: agent.id, mode: 'queue',
          content: [{type: 'text', text: activityPrompt(record.activity, record)}]}, AbortSignal.timeout(15000));
      } catch { settle('delivery-uncertain'); }
    } catch (error) { state.lastError = String(error.message); save(); }
    finally { ticking = false; }
  }

  const snapshot = () => ({schemaVersion: 1, policy, revision: store.revision(policy),
    plan: state.plan, daily: state.daily, runs: state.runs, lastError: state.lastError ?? null,
    active: active?.record.id ?? null, versions: (() => {
      try { return readdirSync(path.join(root, 'versions')).filter(x => /^[0-9a-f]{64}\.json$/.test(x)).map(x => x.slice(0,-5)); }
      catch (e) { if (e.code === 'ENOENT') return []; throw e; }
    })()});
  ctx.effect(() => ctx.webServer.register({kind: 'prefix', path: '/sliver-wolf/companion', handler: async (req, res) => {
    const reply = (status, value) => { res.writeHead(status, {'content-type':'application/json; charset=utf-8','cache-control':'no-store'}); res.end(JSON.stringify(value)); };
    const verdict = ctx.connection.admit(req);
    if (verdict?.rejection !== undefined || verdict?.peer === undefined) return reply(verdict?.rejection ?? 503, {error:'Authentication required'});
    try {
      const route = new URL(req.url, 'http://127.0.0.1').pathname;
      if(req.method==='GET' && route==='/sliver-wolf/companion/model') {
        const sessionId=new URL(req.url,'http://127.0.0.1').searchParams.get('sessionId');
        if(!sessionId || sessionId.length>200)throw new Error('Session ID required');
        return reply(200,await modelSnapshot(ctx,sessionId,policy.budget.outputTokens));
      }
      if (req.method === 'GET' && route === '/sliver-wolf/companion/status') return reply(200, snapshot());
      if (req.method !== 'POST' || !['/sliver-wolf/companion/policy','/sliver-wolf/companion/restore','/sliver-wolf/companion/model-selection'].includes(route)) return reply(404, {error:'Unknown operation'});
      const chunks = []; let bytes = 0;
      for await (const chunk of req) { bytes += chunk.length; if (bytes > 16384) return reply(413, {error:'Policy too large'}); chunks.push(chunk); }
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      if(route.endsWith('/model-selection')) return reply(200,await selectModel(ctx,body));
      if (typeof body.expectedRevision !== 'string') throw new Error('Expected revision required');
      if (route.endsWith('/restore')) policy = store.restore(body.revision, body.expectedRevision);
      else {
        // Resolve before activation; a nonexistent session must never be created.
        if (body.policy?.sessionId && !(await ctx.sessionController.resolveAgent(body.policy.sessionId)).agent) throw new Error('Selected conversation unavailable');
        policy = store.save(body.policy, body.expectedRevision);
      }
      if (policy.sessionId && !state.lastActivity[policy.sessionId]) state.lastActivity[policy.sessionId] = Date.now();
      if (active && (active.record.sessionId !== policy.sessionId || policy.paused || !policy.enabled)) stop('policy-changed');
      // Keep dated counts but discard an unsubmitted random plan when settings change.
      state.plan = {...state.plan, fired: true}; save();
      return reply(200, snapshot());
    } catch (error) { reply(400, {error: String(error.message)}); }
  }}));
  const timer = setInterval(() => void tick(), 15000);
  ctx.effect(() => () => { disposed = true; clearInterval(timer); clearTimeout(dueTimer); stop('plugin-disposed'); });
}
