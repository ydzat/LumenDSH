import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { stat } from 'node:fs/promises';
import { uploadArtifact } from './upload-artifact.mjs';
import { connectDevice } from './bridge.mjs';
const file = process.argv[2];
if (!file) throw new Error('Provide an explicit non-private test file');
const key = 'upload-helper-' + randomUUID();
try {
  const first = await uploadArtifact(file, key);
  const retry = await uploadArtifact(file, key);
  assert.equal(first.state, 'succeeded');
  assert.equal(first.received, (await stat(file)).size);
  assert.deepEqual(retry, first);
  console.log('File helper streamed explicit Termux file; repeated request retained the same verified artifact.');
} finally {
  const device = await connectDevice();
  try { await device.request({ method: 'artifact/discard', arguments: { operationKey: key } }); }
  finally { device.close(); }
}
