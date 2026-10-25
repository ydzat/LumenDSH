// Read only the explicitly designated acceptance session. Print structure/counts, not content.
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const sessionId = process.argv[2];
assert.ok(sessionId, 'An explicit test session ID is required');
const requestId = randomUUID();
const response = await fetch('http://127.0.0.1:3080/api/session.history', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ type: 'client-request', rpcId: requestId, method: 'session.history', payload: { sessionId, maxMessages: 100 } }),
  signal: AbortSignal.timeout(10000),
});
assert.equal(response.status, 200);
const reply = await response.json();
assert.equal(reply.rpcId, requestId);
assert.equal(reply.result.ok, true);
const events = reply.result.value.events.map(entry => entry.event);
const counts = {}, sources = {}, requests = new Map();
for (const event of events) {
  counts[event.type] = (counts[event.type] ?? 0) + 1;
  if (event.type === 'user/message') {
    const source = event.data.source;
    sources[source.kind] = (sources[source.kind] ?? 0) + 1;
    if (source.rpcId) requests.set(source.rpcId, (requests.get(source.rpcId) ?? 0) + 1);
  }
}
console.log(JSON.stringify({ counts, sources, distinctUserRequests: requests.size,
  duplicateRequestMessages: [...requests.values()].filter(count => count !== 1).length,
  terminalReasons: events.filter(event => event.type === 'turn/end').map(event => event.data.reason.kind),
  approvalOutcomes: events.filter(event => event.type === 'approval/decided').map(event => event.data.outcome),
  hasMore: reply.result.value.hasMore }));
