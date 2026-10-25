// Keep MCP transport/validation in the installed official SDK, outside the APK.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';
import { receiveShare } from './receive-share.mjs';
export const VERSION = '0.2.2';

export async function dependencies() {
  if (!process.env.DSH_ROOT) throw new Error('DSH_ROOT must identify the existing installation');
  const require = createRequire(resolve(process.env.DSH_ROOT, 'package.json'));
  const sdk = JSON.parse(await readFile(resolve(dirname(require.resolve('@modelcontextprotocol/sdk/server/index.js')), '../../../package.json'), 'utf8'));
  if (sdk.version !== '1.30.0') throw new Error('Expected inspected MCP SDK 1.30.0');
  return { require, load: name => import(pathToFileURL(require.resolve(name)).href) };
}

export async function connectDevice(tokenOverride, extraHeaders = {}) {
  const { require } = await dependencies();
  const WebSocket = require('ws');
  if (!tokenOverride && !process.env.SLIVER_WOLF_TOKEN_FILE) throw new Error('SLIVER_WOLF_TOKEN_FILE is required');
  const token = tokenOverride ?? (await readFile(process.env.SLIVER_WOLF_TOKEN_FILE, 'utf8')).trim();
  if (!/^[A-Za-z0-9+/]{43}=$/.test(token)) throw new Error('Invalid pairing token file');
  const socket = new WebSocket('ws://127.0.0.1:3081/device-tools', {
    headers: { Authorization: `Bearer ${token}`, ...extraHeaders },
    handshakeTimeout: 5000, maxPayload: 65536,
  });
  await new Promise((accept, reject) => {
    socket.once('open', accept);
    socket.once('error', () => reject(new Error('Device bridge unavailable or authentication refused')));
  });
  const pending = new Map();
  const fail = () => {
    for (const [id, entry] of pending) { clearTimeout(entry.timer); entry.reject(new Error('Device bridge disconnected; operation outcome may be unknown')); pending.delete(id); }
  };
  socket.on('error', fail); socket.on('close', fail);
  socket.on('message', data => {
    let reply;
    try { reply = JSON.parse(data.toString()); } catch { socket.close(1002); fail(); return; }
    const entry = pending.get(reply.id);
    if (!entry) return;
    pending.delete(reply.id); clearTimeout(entry.timer);
    if (reply.error) entry.reject(new Error(reply.error)); else entry.resolve(reply.result);
  });
  return {
    close: () => socket.close(),
    request: payload => new Promise((resolve, reject) => {
      if (socket.readyState !== WebSocket.OPEN) { reject(new Error('Device bridge disconnected')); return; }
      const id = randomUUID();
      const timer = setTimeout(() => { pending.delete(id); reject(new Error('Device response timed out; do not repeat side effects with a new operationKey')); }, 10000);
      pending.set(id, { resolve, reject, timer });
      socket.send(JSON.stringify({ id, ...payload }), error => {
        if (error) { pending.delete(id); clearTimeout(timer); reject(new Error('Device send failed')); }
      });
    }),
  };
}

async function main() {
  const { load } = await dependencies();
  const { Server } = await load('@modelcontextprotocol/sdk/server/index.js');
  const { StdioServerTransport } = await load('@modelcontextprotocol/sdk/server/stdio.js');
  const { ListToolsRequestSchema, CallToolRequestSchema } = await load('@modelcontextprotocol/sdk/types.js');
  const active = new Set();
  // Each request reconnects independently; no tool call is automatically replayed.
  async function request(payload) {
    const device = await connectDevice(); active.add(device);
    try { return await device.request(payload); } finally { device.close(); active.delete(device); }
  }
  const server = new Server({ name: 'sliver-wolf-device-tools', version: VERSION }, { capabilities: { tools: {} } });
  server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: await request({ method: 'list' }) }));
  server.setRequestHandler(CallToolRequestSchema, async ({ params }) => {
    try {
      const result = params.name === 'assistant_receive_share'
        ? await receiveShare(params.arguments ?? {})
        : await request({ method: 'call', name: params.name, arguments: params.arguments ?? {} });
      return { content: [{ type: 'text', text: JSON.stringify(result) }], structuredContent: result };
    } catch (error) { return { content: [{ type: 'text', text: error.message }], isError: true }; }
  });
  const shutdown = async () => { for (const device of active) device.close(); await server.close(); };
  process.once('SIGTERM', shutdown); process.once('SIGINT', shutdown);
  await server.connect(new StdioServerTransport());
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
