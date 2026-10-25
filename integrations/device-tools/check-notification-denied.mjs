// Invoke only while this app's notification AppOp is temporarily denied by the test driver.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { connectDevice } from './bridge.mjs';
const device = await connectDevice();
const call = (name, args = {}) => device.request({ method: 'call', name, arguments: args });
try {
  assert.equal((await call('assistant_get_status')).notificationsEnabled, false);
  const operationKey = 'permission-check-' + randomUUID();
  await assert.rejects(call('assistant_show', { operationKey, surface: 'notification' }), /permission required/);
  assert.equal((await call('assistant_get_operation', { operationKey })).state, 'unknown');
  console.log('Notification permission denial: explicit refusal; no operation reserved.');
} finally { device.close(); }
