import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID, createHash } from 'node:crypto';
import { connectDevice } from './bridge.mjs';
if (!['prepare', 'recover'].includes(process.argv[2]) || !process.env.CHECK_STATE_FILE)
  throw new Error('Use prepare/recover with CHECK_STATE_FILE set to a private test-state file');
const device = await connectDevice();
const request = (method, operationKey, args = {}) => device.request({ method, arguments: { operationKey, ...args } });
try {
  if (process.argv[2] === 'prepare') {
    const operationKey = 'interrupted-artifact-' + randomUUID();
    const bytes = Buffer.from('Controlled interrupted transfer');
    const params = { size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
    const record = await request('artifact/begin', operationKey, params);
    await request('artifact/chunk', operationKey, { offset: 0, base64: bytes.subarray(0, 5).toString('base64') });
    await writeFile(process.env.CHECK_STATE_FILE, JSON.stringify({ operationKey, params, artifactId: record.artifactId }), { mode: 0o600 });
    console.log('Incomplete artifact stored; ready for explicit client force-stop.');
  } else {
    const saved = JSON.parse(await readFile(process.env.CHECK_STATE_FILE, 'utf8'));
    const result = await request('artifact/begin', saved.operationKey, saved.params);
    assert.equal(result.artifactId, saved.artifactId); assert.equal(result.state, 'failed');
    await assert.rejects(request('artifact/finish', saved.operationKey), /invalid-state/);
    await request('artifact/discard', saved.operationKey);
    console.log('Interrupted artifact failed after restart; no final artifact or automatic retry.');
  }
} finally { device.close(); }
