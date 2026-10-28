// Probe the app device bridge directly (WS) and list the bridge method names the
// client source declares, so a disconnected adb does not hide the app channel.
// Usage: node tooling/bridge-probe.mjs [method] [json-arguments]
import {readFileSync} from 'node:fs';
const home = process.env.HOME;
const token = readFileSync(home + '/agent-workspace/device-token-web', 'utf8').trim();
const port = readFileSync(home + '/agent-workspace/device-port-web', 'utf8').trim();
const wsModule = await import(home + '/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/node_modules/ws/index.js');
const WebSocket = wsModule.WebSocket ?? wsModule.default?.WebSocket ?? wsModule.default;
const method = process.argv[2] ?? 'assistant_get_status';
const args = process.argv[3] ? JSON.parse(process.argv[3]) : {};

const socket = new WebSocket('ws://127.0.0.1:' + port + '/device-tools',
  {headers: {Authorization: 'Bearer ' + token}, handshakeTimeout: 5000});
const reply = await new Promise(resolve => {
  socket.once('open', () => socket.send(JSON.stringify({id: 'probe', method: 'call',
    name: method, arguments: args})));
  socket.on('message', data => { try { const value = JSON.parse(data.toString()); if (value.id === 'probe') resolve(value); } catch {} });
  socket.on('error', error => resolve({error: String(error.message ?? error)}));
  socket.on('close', () => resolve({error: 'closed'}));
  setTimeout(() => resolve({error: 'timeout'}), 15000);
});
socket.close();
console.log('bridge:', reply.error ? 'ERROR ' + reply.error : 'ok', 'method:', method);
if (reply.result) console.log(JSON.stringify(reply.result).slice(0, 1200));
if (method === 'assistant_get_status') {
  const source = readFileSync(home + '/sliver-wolf-dev/apps/android-web/src/local/sliverwolf/web/DeviceBridge.java', 'utf8');
  const names = [...source.matchAll(/"([a-z]+[a-z0-9_\/-]*)"/g)].map(match => match[1]).filter(name => /screen|tap|input|shot|capture|ui|window|swipe|key/.test(name));
  console.log('bridge methods mentioning input/screen:', [...new Set(names)].join(' ') || 'none');
}
