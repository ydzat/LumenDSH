// Read-only diagnosis: which session does the companion policy select, does the
// current host still know it, and what does the session store contain?
import {readFileSync, readdirSync} from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
const home = process.env.HOME;
const conn = JSON.parse(readFileSync(path.join(home, '.local/state/sliver-wolf-backend/connection-3080.json'), 'utf8'));
const base = 'http://127.0.0.1:' + (conn.port || 3080);
const headers = {cookie: conn.cookie, 'content-type': 'application/json'};
const status = await (await fetch(base + '/sliver-wolf/companion/status', {headers})).json();
console.log('policy.sessionId =', status.policy?.sessionId);
console.log('lastError =', status.lastError ?? null);
for (const run of (status.runs ?? []).slice(-6)) {
  console.log('run', run.activity, run.status, run.startedAt?.slice(11, 19), '->', run.endedAt?.slice(11, 19));
}
const rpc = async (method, args) => (await (await fetch(base + '/api/' + method, {method: 'POST', headers,
  body: JSON.stringify({type: 'client-request', rpcId: randomUUID(), method, payload: {args}})})).json());
const list = await rpc('session/list', {_request: {}});
const items = list.result?.value?.items ?? [];
console.log('host sessions:', items.length);
for (const item of items.slice(0, 8)) console.log(' -', item.sessionId, item.title?.slice(0, 40) ?? '');
const dir = path.join(home, '.dsh/sessions');
try {
  const files = readdirSync(dir).filter(name => name.endsWith('.jsonl') || name.endsWith('.json'));
  console.log('session files:', files.length, 'match:', files.filter(f => f.includes('fb13506d')).length);
} catch (error) { console.log('session dir error:', error.message); }
