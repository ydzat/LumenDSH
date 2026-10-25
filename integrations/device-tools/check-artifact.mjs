import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import { connectDevice } from './bridge.mjs';
const device = await connectDevice();
const active = [];
const request = (method, operationKey, args = {}) => device.request({ method, arguments: { operationKey, ...args } });
try {
  const bytes = Buffer.from('Sliver Wolf real Android artifact transfer.\n'.repeat(2000));
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  const key = 'artifact-' + randomUUID(); active.push(key);
  const begin = await request('artifact/begin', key, { size: bytes.length, sha256 });
  assert.equal(begin.state, 'pending');
  for (let offset = 0; offset < bytes.length; offset += 32768) {
    const chunk = { offset, base64: bytes.subarray(offset, offset + 32768).toString('base64') };
    const first = await request('artifact/chunk', key, chunk);
    const repeated = await request('artifact/chunk', key, chunk);
    assert.equal(repeated.received, first.received);
  }
  const done = await request('artifact/finish', key);
  assert.equal(done.state, 'succeeded'); assert.equal(done.sha256, sha256); assert.equal(done.received, bytes.length);
  assert.deepEqual(await request('artifact/finish', key), done);
  await assert.rejects(request('artifact/begin', key, { size: bytes.length + 1, sha256 }), /conflict/);
  await assert.rejects(request('artifact/begin', 'oversize-' + randomUUID(), { size: 8 * 1024 * 1024 + 1, sha256 }), /invalid-input/);
  await assert.rejects(request('artifact/begin', '../escape', { size: 3, sha256 }), /invalid-input/);
  const bad = 'bad-digest-' + randomUUID(); active.push(bad);
  await request('artifact/begin', bad, { size: 3, sha256: '0'.repeat(64) });
  await request('artifact/chunk', bad, { offset: 0, base64: 'YWJj' });
  await assert.rejects(request('artifact/finish', bad), /integrity-failed/);
  console.log(JSON.stringify({ transferredBytes: bytes.length, finalizedDigestMatched: true,
    repeatChunkSafe: true, repeatFinishSafe: true, changedMetadataRejected: true,
    oversizeRejected: true, traversalKeyRejected: true, wrongDigestRejected: true }));
} finally {
  for (const key of active) await request('artifact/discard', key);
  device.close();
}
