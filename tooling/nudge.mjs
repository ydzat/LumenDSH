// Queue a self-continuation prompt into the current conversation, so work
// resumes automatically after a restart even when no goal round is armed.
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';

const home = process.env.HOME;
const connection = JSON.parse(readFileSync(path.join(home, '.local/state/sliver-wolf-backend/connection-3080.json'), 'utf8'));
const base = 'http://127.0.0.1:' + (connection.port || 3080);
const cookie = connection.cookie;

async function rpc(method, args) {
  const response = await fetch(base + '/api/' + method, {
    method: 'POST',
    headers: {Cookie: cookie, 'Content-Type': 'application/json'},
    body: JSON.stringify({type: 'client-request', rpcId: randomUUID(), method, payload: {args}}),
  });
  return response.json();
}

let sessionId = '';
try {
  const status = await fetch(base + '/sliver-wolf/companion/status', {headers: {Cookie: cookie}}).then((r) => r.json());
  // Never interrupt an autonomous run with a synthetic continuation prompt.
  if (status.active) { console.log(JSON.stringify({skipped: 'autonomous-run-active', active: status.active})); process.exit(0); }
  // When autonomy is enabled it wakes itself; a synthetic "continue" message would
  // sit in the queue and make the next reserved run yield to it instead of running.
  if (status.policy?.enabled && !status.policy?.paused) {
    console.log(JSON.stringify({skipped: 'autonomy-enabled', policy: 'enabled'}));
    process.exit(0);
  }
  sessionId = status.policy?.sessionId ?? '';
} catch { /* host may still be starting */ }
if (!sessionId) {
  const list = await rpc('session/list', {_request: {}});
  sessionId = list.result?.value?.items?.[0]?.sessionId ?? '';
}
if (!sessionId) throw new Error('No session to nudge');

const text = process.argv[2] || '【自动继续】M11：先检查当前进度（git status、最近提交、待办），然后继续 M11 剩余项；每步验证后提交并推送 agent/dev。';
const reply = await rpc('session/prompt', {request: {sessionId, requestId: randomUUID(),
  mode: 'queue', content: [{type: 'text', text}]}});
console.log(JSON.stringify({sessionId, ok: reply.result?.ok ?? false, error: reply.result?.error ?? null}));
