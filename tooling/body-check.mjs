// Proprioception probe: report the body's reachable channels even when adb is
// down, so an autonomous run can tell "the motor bus is disconnected" apart from
// "the task failed". Read-only: the only side effect is an idempotent
// `adb connect 127.0.0.1:5555` attempt, which never changes device settings.
import {execFileSync} from 'node:child_process';
import net from 'node:net';
import {readFileSync} from 'node:fs';
import path from 'node:path';

const home = process.env.HOME;
const run = (command, args, timeout = 15000) => {
  try { return execFileSync(command, args, {encoding: 'utf8', timeout}).trim(); }
  catch (error) { return 'ERR: ' + String(error.stderr ?? error.message).trim().split('\n')[0]; }
};

// 1. Motor bus (adb).
let adbDevices = run('adb', ['devices']);
if (!adbDevices.includes('127.0.0.1:5555\tdevice')) {
  run('adb', ['connect', '127.0.0.1:5555'], 8000);
  adbDevices = run('adb', ['devices']);
}
const adbOnline = adbDevices.includes('127.0.0.1:5555\tdevice');

// 2. Device bridge (app-side channel, independent of adb).
const portOpen = port => new Promise(resolve => {
  const socket = net.connect({host: '127.0.0.1', port, timeout: 2000});
  socket.once('connect', () => { socket.destroy(); resolve(true); });
  socket.once('error', () => resolve(false));
  socket.once('timeout', () => { socket.destroy(); resolve(false); });
});
const bridge = await portOpen(3084);

// 3. Host health.
let host = false;
try {
  const response = await fetch('http://127.0.0.1:3080/', {signal: AbortSignal.timeout(3000)});
  host = response.status > 0;
} catch { host = false; }

// 4. Network interfaces (what the body can still hear through).
const interfaces = run('ip', ['-4', '-o', 'addr', 'show', 'scope', 'global']);

// 5. Battery (termux-api; independent of adb).
const battery = run('termux-battery-status');

const report = {at: new Date().toISOString(), adbOnline, adbDevices: adbDevices.split('\n').filter(Boolean),
  bridge, host, interfaces: interfaces.split('\n').filter(Boolean), battery};
console.log('adb bus:', adbOnline ? 'online' : 'DISCONNECTED');
console.log('bridge 3084:', bridge ? 'open' : 'closed');
console.log('host 3080:', host ? 'reachable' : 'unreachable');
for (const line of report.interfaces) console.log('net:', line);
if (!battery.startsWith('ERR')) {
  try {
    const parsed = JSON.parse(battery);
    console.log('battery:', parsed.percentage + '%', parsed.plugged);
  } catch { console.log('battery: unreadable'); }
}
const reachable = adbOnline || bridge;
console.log('body:', reachable ? 'reachable' : 'stranded (no adb, no bridge)');
console.log('report:', JSON.stringify(report));
process.exitCode = reachable ? 0 : 2;
