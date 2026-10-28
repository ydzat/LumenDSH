import path from 'node:path';
import os from 'node:os';
import {randomUUID} from 'node:crypto';
import {appendFileSync, existsSync, readdirSync, renameSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {execFileSync, spawn} from 'node:child_process';
import {PolicyStore, TurnBudget, atomicJson, loadJson, activityPrompt, limitsForActivity, recoveryTarget, recoveryPlan, firstWakeSoon, developDue, planFor} from './policy.mjs';
import {buildSituation} from './situation.mjs';
import {appendExperience, readExperiences, recentExperiences} from './experience.mjs';
import {candidateSituation, deriveCandidates, pickCandidate} from './candidates.mjs';
import {emptyGrowth, updateGrowth, addStance, growthSummary, bottleneckMet} from './growth.mjs';
import {buildSelfDoc} from './self-doc.mjs';
import {listExtensions, markExtension, scaffoldExtension} from './extend-self.mjs';
import {appendNote, openNotes, readNotes, updateNote} from './self-notes.mjs';
import {makePlan, completePlan, dayOf} from './upstream/night-plan.mjs';
import {modelSnapshot, outputBudget, restoreOutput, selectModel} from './model-config.mjs';

export const name = 'sliver-wolf-companion';
export const inject = ['agents', 'sessionController', 'webServer', 'connection', 'llm', 'agentDefaultModel', 'tools'];

/** DSH owns Agent execution and memory. This plugin only plans and bounds local work. */
export function apply(ctx, config = {}) {
  const root = config.directory ?? path.join(os.homedir(), 'sliver-wolf-companion', 'behavior');
  const store = new PolicyStore(root);
  let policy = store.read();
  const stateFile = path.join(root, 'state.json');
  const experienceFile = path.join(root, 'experience.jsonl');
  const notesFile = path.join(root, 'self-notes.jsonl');
  const selfDocFile = path.join(root, 'self-doc.md');
  const extensionsRoot = process.env.SLIVER_WOLF_EXTENSIONS_DIR ??
    path.join(os.homedir(), 'sliver-wolf-dev', 'integrations', 'extensions');
  let state = loadJson(stateFile, {schemaVersion: 1, plan: {}, runs: [], lastActivity: {}, daily: {}});
  if (state.schemaVersion !== 1 || !Array.isArray(state.runs)) throw new Error('Invalid companion state');
  state.outputOverrides??={};
  state.growth ??= emptyGrowth();
  const readAllNotes = () => readNotes(notesFile).notes;
  const readAllExperiences = () => readExperiences(experienceFile).experiences;
  function writeSelfDoc(growth = state.growth, notes = readAllNotes(), experiences = readAllExperiences()) {
    const text = buildSelfDoc({growth, notes, experiences: experiences.slice(-10), policy});
    const temp = selfDocFile + '.tmp';
    writeFileSync(temp, text, {mode: 0o600});
    renameSync(temp, selfDocFile);
  }
  function refreshGrowth(now = Date.now()) {
    const experiences = readAllExperiences();
    const notes = readAllNotes();
    const {growth, changed} = updateGrowth(state.growth, {experiences, notes, runs: state.runs, now});
    state.growth = growth;
    if (changed) { save(); writeSelfDoc(growth, notes, experiences); }
    return {growth, notes, experiences};
  }
  function recordExperience(experience) {
    const record = appendExperience(experienceFile, experience);
    refreshGrowth();
    return record;
  }

  // Agent-facing tools. They are plain JSON Schema specs registered directly on
  // the host registry; no dependency on @deepseek-ai/dsh-tools is required.
  const output = (properties) => ({schema: {type: 'object', additionalProperties: false,
    properties, required: Object.keys(properties)}, render: (_args, value) => [{type: 'text',
    text: String(value?.text ?? JSON.stringify(value))}]});
  const requireOwn = (exec) => {
    if (exec?.agent?.id !== policy.sessionId) throw new Error('Only the selected conversation may record self-development state');
  };
  function runPhone(...args) {
    try { return String(execFileSync('bash', [phoneScript, ...args], {encoding: 'utf8', timeout: 60000})).trim(); }
    catch (error) { return 'phone command failed: ' + String(error.message); }
  }
  const phoneTool = (name, description, properties, args, text) => ctx.tools.register({
    name, description,
    parameters: {type: 'object', additionalProperties: false, properties},
    output: output({text: {type: 'string'}}),
    async execute(parameters, exec) {
      const value = runPhone(...args(parameters));
      return {text: value || text};
    }
  });
  phoneTool('phone_shot', 'Capture the phone screen through adb; returns the PNG path to read with read_image.',
    {}, () => ['shot'], 'screen captured');
  phoneTool('phone_tap', 'Tap phone screen coordinates through adb.',
    {x: {type: 'number'}, y: {type: 'number'}}, (p) => ['tap', String(p.x), String(p.y)], 'tapped');
  phoneTool('phone_text', 'Type text into the focused field through adb.',
    {text: {type: 'string'}}, (p) => ['text', String(p.text)], 'typed');
  phoneTool('phone_key', 'Send an adb keyevent (e.g. 3 home, 4 back, 26 power).',
    {code: {type: 'integer'}}, (p) => ['key', String(p.code)], 'key sent');
  phoneTool('phone_start', 'Start an Android component, e.g. local.sliverwolf.web/.MainActivity.',
    {component: {type: 'string'}}, (p) => ['start', String(p.component)], 'started');
  phoneTool('phone_install', 'Install an APK from a file path through adb without user interaction.',
    {apk: {type: 'string'}}, (p) => ['install', String(p.apk)], 'installed');
  phoneTool('phone_ui', 'Dump the visible UI hierarchy of the phone through adb (uiautomator).',
    {}, () => ['ui'], 'ui dumped');
  const REA_COMMANDS = new Set(['inspect-apk-signature','inspect-android-package',
    'inspect-android-class','inspect-android-method','analyze','decompile',
    'analyze-javascript-application','analyze-web-bundle']);
  const REA_ROOTS = ['/storage/emulated/0/Download/','/data/data/com.termux/files/home/tmp/',
    '/data/data/com.termux/files/home/sliver-wolf-dev/artifacts'];
  ctx.tools.register({
    name: 'rea',
    description: 'Run one read-only REA reverse-engineering command on the analysis server. The file is copied to the server first. Commands: inspect-apk-signature, inspect-android-package, inspect-android-class (className), inspect-android-method (className, methodName), analyze, decompile (procedure), analyze-javascript-application, analyze-web-bundle.',
    parameters: {type: 'object', additionalProperties: false, required: ['command', 'path'],
      properties: {command: {type: 'string', enum: [...REA_COMMANDS]}, path: {type: 'string'},
        className: {type: 'string'}, methodName: {type: 'string'}, procedure: {type: 'string'}}},
    output: output({text: {type: 'string'}}),
    async execute(parameters, exec) {
      const command = String(parameters.command ?? '');
      const source = String(parameters.path ?? '');
      if (!REA_COMMANDS.has(command)) throw new Error('REA command is not allowed');
      if (!REA_ROOTS.some(prefix => source.startsWith(prefix))) throw new Error('REA path must be under an allowed root');
      const remote = '/tmp/rea-input-' + String(source.split('/').pop() ?? 'artifact').replace(/[^A-Za-z0-9._-]/g, '_');
      try {
        execFileSync('scp', ['-q', source, 'server:' + remote], {timeout: 120000});
      } catch (error) {
        throw new Error('REA upload failed: ' + String(error.message));
      }
      const args = [command, remote];
      if (parameters.className) args.push('--class', String(parameters.className));
      if (parameters.methodName) args.push('--method', String(parameters.methodName));
      if (parameters.procedure) args.push('--procedure', String(parameters.procedure));
      let text;
      try {
        text = execFileSync('ssh', ['-o', 'BatchMode=yes', 'server', 'rea', ...args],
          {encoding: 'utf8', timeout: 600000, maxBuffer: 32 * 1024 * 1024});
      } catch (error) {
        text = 'REA failed: ' + String(error.stderr ?? error.message ?? error);
      }
      try {
        const log = path.join(root, 'activity-audit.jsonl');
        let size = 0; try { size = statSync(log).size; } catch { size = 0; }
        if (size < 1024 * 1024) appendFileSync(log, JSON.stringify({at: new Date().toISOString(),
          tool: 'rea', command, source, activity: active?.record.activity ?? null}) + '\n', {mode: 0o600});
      } catch { /* audit must not break the call */ }
      requireOwn(exec);
      return {text: String(text).slice(0, 30000)};
    }
  });
  ctx.tools.register({
    name: 'self_experience',
    description: 'Record a real experience (failure, correction, friction, observation, capability-gap, extension, review) into the append-only stream that derives future development candidates.',
    parameters: {type: 'object', additionalProperties: false, properties: {
      kind: {type: 'string', enum: ['failure', 'correction', 'friction', 'result', 'observation', 'capability-gap', 'extension', 'review']},
      summary: {type: 'string', description: 'One sentence, 1..500 characters'},
      detail: {type: 'string'},
      severity: {type: 'integer', description: '1..5'},
      tags: {type: 'array', items: {type: 'string'}},
      refs: {type: 'array', items: {type: 'string'}}}, required: ['kind', 'summary']},
    output: output({id: {type: 'string'}, kind: {type: 'string'}, at: {type: 'string'}, text: {type: 'string'}}),
    async execute(args, exec) {
      requireOwn(exec);
      const record = recordExperience({...args, source: 'agent'});
      return {id: record.id, kind: record.kind, at: record.at, text: `recorded ${record.kind} ${record.id}`};
    }
  });
  ctx.tools.register({
    name: 'self_note',
    description: 'Append a need, curiosity, friction, idea or result to the self-notes ledger.',
    parameters: {type: 'object', additionalProperties: false, properties: {
      kind: {type: 'string', enum: ['need', 'curiosity', 'friction', 'idea', 'result']},
      text: {type: 'string'}, tags: {type: 'array', items: {type: 'string'}}}, required: ['kind', 'text']},
    output: output({id: {type: 'string'}, kind: {type: 'string'}, text: {type: 'string'}}),
    async execute(args, exec) {
      requireOwn(exec);
      const note = appendNote(notesFile, {kind: args.kind, text: args.text, tags: args.tags});
      return {id: note.id, kind: note.kind, text: `recorded note ${note.id}`};
    }
  });
  ctx.tools.register({
    name: 'self_close',
    description: 'Close, drop or reopen a self-note with an optional detail.',
    parameters: {type: 'object', additionalProperties: false, properties: {
      id: {type: 'string'}, status: {type: 'string', enum: ['open', 'done', 'dropped']}, detail: {type: 'string'}},
      required: ['id', 'status']},
    output: output({id: {type: 'string'}, status: {type: 'string'}, text: {type: 'string'}}),
    async execute(args, exec) {
      requireOwn(exec);
      updateNote(notesFile, args.id, args.status, args.detail ?? '');
      refreshGrowth();
      return {id: args.id, status: args.status, text: `note ${args.id} is ${args.status}`};
    }
  });
  ctx.tools.register({
    name: 'self_stance',
    description: 'From stage 3 onwards, record a durable stance backed by evidence ids (experience or note ids).',
    parameters: {type: 'object', additionalProperties: false, properties: {
      text: {type: 'string'}, evidence: {type: 'array', items: {type: 'string'}}}, required: ['text', 'evidence']},
    output: output({id: {type: 'string'}, text: {type: 'string'}}),
    async execute(args, exec) {
      requireOwn(exec);
      const stance = addStance(state.growth, {text: args.text, evidence: args.evidence});
      save();
      writeSelfDoc();
      return {id: stance.id, text: `stance: ${stance.text}`};
    }
  });
  ctx.tools.register({
    name: 'extend_self',
    description: 'Scaffold a new module for a capability that is missing: an independent, testable starting point with README, entry point, contract test and registry entry. Implement and verify it with normal tools afterwards.',
    parameters: {type: 'object', additionalProperties: false, properties: {
      name: {type: 'string', description: 'kebab-case module name'},
      requirement: {type: 'string', description: 'What the capability must do, 1..500 characters'}},
      required: ['name', 'requirement']},
    output: output({name: {type: 'string'}, path: {type: 'string'}, files: {type: 'array', items: {type: 'string'}},
      text: {type: 'string'}}),
    async execute(args, exec) {
      requireOwn(exec);
      const scaffold = scaffoldExtension({name: args.name, requirement: args.requirement, root: extensionsRoot});
      recordExperience({kind: 'extension', summary: `scaffolded extension ${args.name}: ${args.requirement}`,
        severity: 2, tags: [args.name], refs: [args.name], source: 'agent'});
      return {name: scaffold.name, path: scaffold.path, files: scaffold.files,
        text: `scaffolded ${scaffold.name} at ${scaffold.path}`};
    }
  });
  let disposed = false, ticking = false, tickingDev = false, active, dueTimer;
  const startedAt=Date.now();
  const observed = new Set();
  const save = () => atomicJson(stateFile, state);
  // Audible cue for the user: a finished human turn rings once; autonomous
  // activity stays silent. Disable with SLIVER_WOLF_TURN_TONE=0.
  const toneScript = path.join(os.homedir(), 'agent-workspace', 'tone.sh');
  const nudgeFlag = path.join(os.homedir(), '.local/state/sliver-wolf-m9/nudge-on-start');
  function scheduleStartupNudge() {
    if (!existsSync(nudgeFlag)) return;
    let attempts = 0;
    const timer = setInterval(async () => {
      attempts += 1;
      const sessionId = policy.sessionId;
      if (!sessionId) { if (attempts > 8) { clearInterval(timer); } return; }
      try {
        await ctx.sessionController.prompt({requestId: randomUUID(), sessionId, mode: 'queue',
          content: [{type: 'text', text: '【自动继续】M10：先检查进度（git status、最近提交、待办），然后继续开发剩余项；每步验证后提交并推送 agent/dev，不需要等我。'}]}, AbortSignal.timeout(15000));
        rmSync(nudgeFlag, {force: true});
        clearInterval(timer);
      } catch { if (attempts > 8) clearInterval(timer); }
    }, 5000);
    setTimeout(() => clearInterval(timer), 120000);
  }
  scheduleStartupNudge();
  const phoneScript = path.join(os.homedir(), 'agent-workspace', 'phone.sh');
  let lastToneAt = 0;
  function playTone(kind) {
    if (process.env.SLIVER_WOLF_TURN_TONE === '0') return;
    const now = Date.now();
    if (now - lastToneAt < 2000) return;
    lastToneAt = now;
    try { spawn('bash', [toneScript, kind], {detached: true, stdio: 'ignore'}).unref(); }
    catch { /* the cue must never affect the turn */ }
  }
  // Reservations are never replayed after a crash. The session log remains the
  // authority for any work accepted immediately before the process disappeared.
  for (const run of state.runs) if (['reserved', 'running'].includes(run.status)) {
    run.status = 'interrupted-unreplayed'; run.endedAt = new Date().toISOString();
  }
  save();
  // A run interrupted by a restart is never replayed (at-most-once side effects).
  // Instead, plan a fresh recovery wake so the next turn can inspect the actual
  // repository state and continue the work.
  const recovery = recoveryTarget(policy, state);
  if (recovery) { state.recovery = recovery; save(); }
  // Before the first run of a day, do not keep a stale random plan across a
  // restart or a policy change: the next plan is re-rolled and moved to the
  // earliest allowed moment by firstWakeSoon.
  if (!state.recovery && (state.daily[dayOf(Date.now())] ?? 0) === 0 && state.plan) {
    delete state.plan; save();
  }

  function settle(status) {
    if (!active) return;
    const current = active; active = undefined;
    clearTimeout(current.timer);
    Object.assign(current.record, {status, endedAt: new Date().toISOString(),
      steps: current.budget.steps, tools: current.budget.tools, writesAttempted: current.budget.writes});
    const failed = ['failed', 'delivery-uncertain'].includes(status);
    recordExperience({kind: failed ? 'failure' : 'result',
      summary: `${current.record.activity} ${status}`,
      detail: current.record.failureCode ? `failureCode=${current.record.failureCode}` : undefined,
      severity: failed ? 4 : 1, tags: [current.record.activity],
      refs: [current.record.id], source: 'host'});
    if (current.record.activity === 'develop') {
      state.lastDevelopAt = Date.now();
      setTimeout(() => void devTick(), 2000);
    }
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
        let selfDoc = '';
        try { selfDoc = buildSelfDoc({growth: state.growth, notes: readAllNotes(),
          experiences: readAllExperiences().slice(-8), policy}); } catch { /* identity is advisory */ }
        return {...assembled, sections: [...assembled.sections, {name: 'sliver-wolf-behavior',
          text: `The user's current configured name for you is ${JSON.stringify(policy.agentName)}. ` +
          `This updates your display identity without replacing your existing history or instructions.\nInteraction preferences: ${policy.style}\n` +
          'Keep shared experiences, general knowledge and attributed observations distinguishable. Use actual Mnemon sources for recollection; never fabricate personal history.'},
          ...(selfDoc ? [{name: 'sliver-wolf-self-doc', text: selfDoc}] : [])]};
      }));
      disposers.push(agent.ctx.on('session/event', (session, event) => {
        const id = session?.header?.id ?? session?.id;
        if (id !== agent.id) return;
        // Audit autonomous web and phone actions for later review; user turns are
        // already visible in the conversation and this log only covers runs.
        if (event?.type === 'tool/call' && active?.agent === agent) {
          const name = String(event?.data?.name ?? '');
          if (['web_search','web_fetch','phone_shot','phone_tap','phone_click','phone_text','phone_key','phone_start','phone_install','phone_ui'].includes(name)) {
            try {
              const file = path.join(root, 'activity-audit.jsonl');
              let size = 0; try { size = statSync(file).size; } catch { size = 0; }
              if (size < 1024 * 1024) appendFileSync(file, JSON.stringify({at: new Date().toISOString(),
                session: agent.id, activity: active.record.activity, tool: name,
                input: JSON.stringify(event?.data?.input ?? event?.data?.arguments ?? {}).slice(0, 500)}) + '\n', {mode: 0o600});
            } catch { /* audit must never break the turn */ }
          }
        }
        if (event?.type !== 'turn/end') return;
        if (active) return; // an autonomous activity must not ring on every run
        playTone('done');
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

  // A host restart can briefly expose the session index before the selected
  // conversation is resolvable; retry instead of failing the run immediately.
  async function resolveSelected(attempts = 3) {
    let last;
    for (let i = 0; i < attempts; i++) {
      last = await ctx.sessionController.resolveAgent(policy.sessionId);
      if (last?.agent) return last;
      if (i < attempts - 1) await new Promise(resolve => setTimeout(resolve, 1000));
    }
    throw new Error('Selected conversation unavailable: ' + policy.sessionId);
  }

  async function tick() {
    if (disposed || ticking || active || !policy.enabled || policy.paused) return;
    ticking = true;
    try {
      const now = Date.now(), today = dayOf(now);
      if ((state.daily[today] ?? 0) >= policy.budget.dailyRuns) return;
      if (state.recovery) {
        if (now < state.recovery.atMs) return;
        const plan = recoveryPlan(policy, state, now);
        if (!plan) {
          const moved = recoveryTarget({...policy, enabled: true}, state, now);
          if (moved) { state.recovery = moved; } else { delete state.recovery; }
          save();
          return;
        }
        state.plan = plan;
        delete state.recovery;
        save();
      }
      const previous = state.plan;
      state.plan = planFor(policy, previous, now);
      if (state.plan !== previous) {
        state.plan = firstWakeSoon(state, state.plan, now);
        save(); clearTimeout(dueTimer);
        if (state.plan.atMs > now) dueTimer = setTimeout(() => void tick(), state.plan.atMs - now);
      }
      if (!['wake', 'skip'].includes(state.plan.action) || state.plan.fired || state.plan.atMs > now) return;
      if (state.plan.action === 'skip') { state.plan = completePlan(state.plan, now); save(); return; }
      const agent = (await resolveSelected()).agent;
      attach(agent);
      // Restart begins a fresh inactivity interval; it cannot immediately wake an
      // old conversation merely because there is no process-local activity cache.
      if (!state.lastActivity[agent.id]) { state.lastActivity[agent.id] = now; save(); return; }
      if (agent.status !== 'idle' || now - Math.max(startedAt,state.lastActivity[agent.id]) < policy.idleMinutes * 60000) return;
      if (disposed || policy.paused || !policy.enabled || policy.sessionId !== agent.id) return;
      const maintenance = policy.activities.filter(candidate => candidate !== 'develop');
      if (!maintenance.length) return;
      const maintenanceRuns = state.runs.filter(run => run.activity !== 'develop').length;
      const activity = maintenance[maintenanceRuns % maintenance.length];
      const limits = limitsForActivity(policy.budget, activity);
      const record = {id: randomUUID(), sessionId: agent.id, status: 'reserved',
        activity, startedAt: new Date(now).toISOString(), limits};
      state.plan = completePlan(state.plan, now);
      state.daily[today] = (state.daily[today] ?? 0) + 1;
      for (const day of Object.keys(state.daily)) if (day < dayOf(now - 7 * 86400000)) delete state.daily[day];
      state.runs.push(record); state.runs = state.runs.slice(-100);
      save(); // Reserve before I/O. An uncertain delivery is not automatically retried.
      active = {record, agent, budget: new TurnBudget(limits, record.activity)};
      active.timer = setTimeout(() => stop('time-budget'), limits.seconds * 1000);
      try {
        await ctx.sessionController.prompt({requestId: record.id, sessionId: agent.id, mode: 'queue',
          content: [{type: 'text', text: activityPrompt(record.activity, record)}]}, AbortSignal.timeout(15000));
      } catch { settle('delivery-uncertain'); }
    } catch (error) { state.lastError = String(error.message); save(); }
    finally { ticking = false; }
  }

  async function manualRun(activity = 'develop') {
    if (active) return {ok: false, reason: 'busy'};
    if (!policy.enabled || !policy.sessionId) return {ok: false, reason: 'disabled'};
    let agent;
    try { agent = (await resolveSelected()).agent; }
    catch { return {ok: false, reason: 'session-unavailable'}; }
    attach(agent);
    const now = Date.now();
    const limits = limitsForActivity(policy.budget, activity);
    const record = {id: randomUUID(), sessionId: agent.id, status: 'reserved', activity,
      startedAt: new Date(now).toISOString(), limits, source: 'manual-verification'};
    state.runs.push(record); state.runs = state.runs.slice(-100); save();
    active = {record, agent, budget: new TurnBudget(limits, activity)};
    active.timer = setTimeout(() => stop('time-budget'), limits.seconds * 1000);
    const situation = buildSituation({notesFile, runs: state.runs, lastError: state.lastError ?? null})
      + '\n\nThis is a manually triggered verification run.';
    try {
      await ctx.sessionController.prompt({requestId: record.id, sessionId: agent.id, mode: 'queue',
        content: [{type: 'text', text: activityPrompt(activity, record, situation)}]}, AbortSignal.timeout(15000));
      return {ok: true, active: record.id};
    } catch (error) {
      settle('delivery-uncertain');
      return {ok: false, reason: 'delivery-uncertain'};
    }
  }

  async function devTick(force = false) {
    if (disposed || tickingDev || active || !policy.enabled || policy.paused || !policy.sessionId) return;
    tickingDev = true;
    try {
      const now = Date.now();
      const decision = force ? {due: true, idleMinutes: 0} : developDue(policy, state, now);
      if (!decision.due) return;
      const candidates = deriveCandidates({experiences: recentExperiences(experienceFile, {sinceMs: 30 * 86400000}),
        notes: readAllNotes(), runs: state.runs, now});
      const candidate = pickCandidate(candidates, {runs: state.runs}) ??
        (force ? {id: 'manual', kind: 'manual'} : undefined);
      if (!candidate) { state.lastQuietAt = new Date(now).toISOString(); save(); return; }
      const agent = (await resolveSelected()).agent;
      attach(agent);
      if (agent.status !== 'idle') return;
      if (!state.lastActivity[agent.id]) state.lastActivity[agent.id] = now;
      if (now - Math.max(startedAt, state.lastActivity[agent.id]) < decision.idleMinutes * 60000) return;
      if (disposed || policy.paused || !policy.enabled) return;
      const activity = 'develop';
      const limits = limitsForActivity(policy.budget, activity);
      const record = {id: randomUUID(), sessionId: agent.id, status: 'reserved', activity,
        startedAt: new Date(now).toISOString(), limits, source: 'develop-schedule',
        candidateId: candidate.id, candidateKind: candidate.kind};
      const today = dayOf(now);
      state.developDaily = {...state.developDaily, [today]: (state.developDaily?.[today] ?? 0) + 1};
      state.runs.push(record); state.runs = state.runs.slice(-100);
      save();
      active = {record, agent, budget: new TurnBudget(limits, activity)};
      active.timer = setTimeout(() => stop('time-budget'), limits.seconds * 1000);
      const situation = buildSituation({notesFile, runs: state.runs,
        lastError: state.lastError ?? null}) + '\n\n' + candidateSituation(candidate, now);
      try {
        await ctx.sessionController.prompt({requestId: record.id, sessionId: agent.id, mode: 'queue',
          content: [{type: 'text', text: activityPrompt(activity, record, situation)}]}, AbortSignal.timeout(15000));
      } catch { settle('delivery-uncertain'); }
    } catch (error) { state.lastError = String(error.message); save(); }
    finally { tickingDev = false; }
  }

  let composerSeq = 0, composerPending = null;
  const snapshot = () => ({schemaVersion: 1, policy, revision: store.revision(policy),
    plan: state.plan, daily: state.daily, runs: state.runs, lastError: state.lastError ?? null,
    active: active?.record.id ?? null, period: state.period ?? null, versions: (() => {
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
      if (req.method === 'GET' && route === '/sliver-wolf/companion/period') return reply(200, state.period ?? {phase: null});
      if (req.method === 'GET' && route === '/sliver-wolf/companion/composer/pending') {
        const pending = composerPending; composerPending = null;
        return reply(200, pending ?? {seq: 0, text: ''});
      }
      if (req.method === 'POST' && route === '/sliver-wolf/companion/screen') {
        const chunks = []; let bytes = 0;
        for await (const chunk of req) { bytes += chunk.length; if (bytes > 8 * 1024 * 1024) return reply(413, {error: 'Screen capture too large'}); chunks.push(chunk); }
        const file = path.join(root, 'screen.png');
        writeFileSync(file, Buffer.concat(chunks), {mode: 0o600});
        return reply(200, {ok: true, path: file, bytes});
      }
      if (req.method !== 'POST' || !['/sliver-wolf/companion/policy','/sliver-wolf/companion/restore','/sliver-wolf/companion/model-selection','/sliver-wolf/companion/period','/sliver-wolf/companion/log','/sliver-wolf/companion/composer','/sliver-wolf/companion/run'].includes(route)) return reply(404, {error:'Unknown operation'});
      const chunks = []; let bytes = 0;
      for await (const chunk of req) { bytes += chunk.length; if (bytes > 16384) return reply(413, {error:'Policy too large'}); chunks.push(chunk); }
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      if(route.endsWith('/model-selection')) return reply(200,await selectModel(ctx,body));
      if (route.endsWith('/log')) {
        const message = String(body?.message ?? '').slice(0, 8000);
        if (!message) throw new Error('Log message required');
        const file = path.join(root, 'app.log');
        let size = 0;
        try { size = statSync(file).size; } catch { size = 0; }
        if (size < 512 * 1024) appendFileSync(file, new Date().toISOString() + ' ' + message + '\n', {mode: 0o600});
        return reply(200, {ok: true});
      }
      if (route.endsWith('/run')) {
        const activity = String(body?.activity ?? 'develop');
        if (!['develop', 'browse'].includes(activity)) throw new Error('Only develop and browse runs can be triggered manually');
        if (active) return reply(409, {ok: false, reason: 'busy'});
        return reply(200, await manualRun(activity));
      }
      if (route.endsWith('/composer')) {
        const text = String(body?.text ?? '').slice(0, 8192);
        if (!text) throw new Error('Composer text required');
        composerPending = {seq: ++composerSeq, text};
        return reply(200, {ok: true, seq: composerSeq});
      }
      if (route.endsWith('/period')) {
        // Written by the Android client from the tide plugin's own client state
        // (which includes holiday handling). Peak means no develop run.
        const phase = body?.phase;
        if (phase !== 'peak' && phase !== 'valley') throw new Error('Period phase must be peak or valley');
        const flipAt = Number.isFinite(body?.flipAt) ? Number(body.flipAt) : null;
        state.period = {phase, flipAt, reportedAt: new Date().toISOString(), source: 'plugin'};
        save();
        return reply(200, state.period);
      }
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
  try { refreshGrowth(); writeSelfDoc(); } catch { /* advisory identity */ }
  const timer = setInterval(() => void tick(), 15000);
  const devTimer = setInterval(() => void devTick(), 60000);
  const devKick = setTimeout(() => void devTick(), 3000);
  ctx.effect(() => () => { disposed = true; clearInterval(timer); clearInterval(devTimer); clearTimeout(devKick); clearTimeout(dueTimer); stop('plugin-disposed'); });
}
