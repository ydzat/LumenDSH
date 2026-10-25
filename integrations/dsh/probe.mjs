// Run inside Termux. Only summaries leave the device; no session content is printed.
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import http from 'node:http';

const base = 'http://127.0.0.1:3080';
async function request(method, payload = {}, headers = {}) {
  const rpcId = randomUUID();
  const response = await fetch(`${base}/api/${method}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ type: 'client-request', rpcId, method, payload }),
    signal: AbortSignal.timeout(10000),
  });
  if (response.status !== 200) return { status: response.status };
  const body = await response.json();
  assert.equal(body.type, 'server-response');
  assert.equal(body.rpcId, rpcId);
  return { status: response.status, result: body.result };
}
const description = await request('host.describe');
assert.equal(description.result?.ok, true);
console.log(JSON.stringify({ check: 'host.describe', status: 200,
  version: description.result.value.version, authentication: 'none supplied' }));
const sessions = await request('session.list');
assert.equal(sessions.result?.ok, true);
console.log(JSON.stringify({ check: 'session.list', count: sessions.result.value.items.length }));
const forbidden = await request('host.describe', {}, { Origin: 'http://untrusted.invalid' });
assert.equal(forbidden.status, 403);
console.log(JSON.stringify({ check: 'cross-origin', status: forbidden.status }));
// Node fetch normalizes Host; use the raw HTTP client to exercise this fence.
const invalidHostStatus = await new Promise((resolve, reject) => {
  const req = http.request(`${base}/api/host.describe`, {
    method: 'POST', headers: { Host: 'untrusted.invalid', 'Content-Type': 'application/json' },
  }, response => { response.resume(); resolve(response.statusCode); });
  req.setTimeout(5000, () => req.destroy(new Error('HTTP timeout')));
  req.on('error', reject);
  req.end(JSON.stringify({ type: 'client-request', rpcId: randomUUID(), method: 'host.describe', payload: {} }));
});
assert.equal(invalidHostStatus, 403);
console.log(JSON.stringify({ check: 'untrusted-host', status: invalidHostStatus }));
const absentSession = `sliver-wolf-m0-absent-${randomUUID()}`;
for (const method of ['session.history', 'session.cancel']) {
  const response = await request(method, { sessionId: absentSession });
  assert.equal(response.result?.ok, false);
  assert.equal(response.result.error.code, 'session-not-found');
  console.log(JSON.stringify({ check: method, absentSession: 'session-not-found' }));
}
const reply = await fetch(`${base}/api/respond`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ type: 'client-response', rpcId: randomUUID(), result: { ok: true, value: {} } }),
  signal: AbortSignal.timeout(5000),
});
assert.equal(reply.status, 200);
assert.deepEqual(await reply.json(), { accepted: false, reason: 'not-pending' });
console.log(JSON.stringify({ check: 'unmatched-decision', result: 'not-pending' }));
for (const channel of ['events.mux', 'events.host']) {
  const response = await fetch(`${base}/api/${channel}`, { signal: AbortSignal.timeout(5000) });
  assert.equal(response.status, 426);
  const counts = {};
  await new Promise((resolve, reject) => {
    const socket = new WebSocket(`${base.replace('http:', 'ws:')}/api/${channel}`);
    const timeout = setTimeout(() => { socket.close(); reject(new Error('WebSocket open timeout')); }, 5000);
    socket.onerror = () => { clearTimeout(timeout); reject(new Error('WebSocket failure')); };
    socket.onopen = () => {
      clearTimeout(timeout);
      setTimeout(() => { socket.close(); resolve(); }, 500);
    };
    socket.onmessage = ({ data }) => {
      const frame = JSON.parse(data);
      const kind = frame.method ?? frame.payload?.type ?? frame.type;
      counts[kind] = (counts[kind] ?? 0) + 1;
    };
  });
  console.log(JSON.stringify({ check: channel, getStatus: 426, websocket: 'opened', frameTypes: counts }));
}
