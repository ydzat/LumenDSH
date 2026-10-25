// Real Android/MCP checks. Requires a visible paired client; posts one test notification.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { dependencies, connectDevice } from './bridge.mjs';
import { fileURLToPath } from 'node:url';

const { load } = await dependencies();
const { Client } = await load('@modelcontextprotocol/sdk/client/index.js');
const { StdioClientTransport } = await load('@modelcontextprotocol/sdk/client/stdio.js');
const client = new Client({ name: 'sliver-wolf-device-check', version: '1' });
// Termux's Node launcher may report the Android linker as process.execPath.
const transport = new StdioClientTransport({ command: 'node',
  args: [fileURLToPath(new URL('./bridge.mjs', import.meta.url))], env: process.env });
try {
  await assert.rejects(connectDevice('A'.repeat(43) + '='), /authentication refused/);
  await assert.rejects(connectDevice(undefined, { Authorization: '' }), /authentication refused/);
  await assert.rejects(connectDevice(undefined, { Origin: 'https://untrusted.invalid' }), /authentication refused/);
  await client.connect(transport);
  const names = (await client.listTools()).tools.map(t => t.name);
  assert.deepEqual(names, ['assistant_get_status', 'assistant_show', 'assistant_get_operation', 'assistant_speak', 'assistant_stop_speaking']);
  const call = async (name, args) => {
    const reply = await client.callTool({ name, arguments: args });
    assert.ok(!reply.isError, 'Actual device tool returned an error');
    return reply.structuredContent;
  };
  const status = await call('assistant_get_status', {});
  assert.equal(status.appVersion, '0.2.1-m2');
  assert.equal(status.microphone, 'not-implemented');
  const operationKey = 'acceptance-' + randomUUID();
  const first = await call('assistant_show', { surface: 'notification', operationKey });
  assert.equal(first.state, 'succeeded');
  const repeated = await call('assistant_show', { surface: 'notification', operationKey });
  assert.equal(repeated.operationId, first.operationId);
  assert.deepEqual(repeated, first);
  const lookup = await call('assistant_get_operation', { operationKey });
  assert.deepEqual(lookup, first);
  const invalid = await client.callTool({ name: 'assistant_show', arguments: { surface: 'overlay', operationKey } });
  assert.equal(invalid.isError, true);
  console.log(JSON.stringify({ authenticationRejected: true, foreignOriginRejected: true,
    discoveredTools: names, notificationState: first.state, retrySameOperation: true,
    invalidSurfaceRejected: true, uiVisible: status.uiVisible }));
} finally { await client.close(); }
