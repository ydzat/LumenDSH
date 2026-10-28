// Demonstrate observe → act → verify on the Bluetooth switch using the shared
// ui-loop primitives. Toggles once and restores the original state.
import {execFileSync} from 'node:child_process';
import {screenSignature, act, requireElement} from '../integrations/phone/ui-loop.mjs';

const serial = process.env.PHONE_SERIAL || '127.0.0.1:5555';
const adb = (args, timeout = 30000) => execFileSync('adb', ['-s', serial, ...args],
  {encoding: 'utf8', timeout, maxBuffer: 16 * 1024 * 1024}).toString();

const selector = {resourceId: 'com.android.settings:id/switch_widget', clickable: true};
const stateOf = elements => requireElement(elements, selector).checked;

adb(['shell', 'am', 'start', '-a', 'android.settings.BLUETOOTH_SETTINGS']);
const initial = await screenSignature(adb);
const before = stateOf(initial.elements);

const first = await act(adb, selector, {verify: elements => {
  const after = stateOf(elements);
  return {ok: after !== before, detail: 'checked ' + before + ' -> ' + after};
}});
console.log('first act:', first.ok ? 'ok' : 'FAILED', '|', first.detail, '| changed:', first.changed);

const second = await act(adb, selector, {verify: elements => {
  const after = stateOf(elements);
  return {ok: after === before, detail: 'restored ' + after};
}});
console.log('restore act:', second.ok ? 'ok' : 'FAILED', '|', second.detail);
adb(['shell', 'input', 'keyevent', '3']);
process.exitCode = first.ok && second.ok ? 0 : 1;
