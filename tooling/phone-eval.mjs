// Bounded, read-only phone capability evaluation (AndroidWorld-style, minimal).
// Each task runs a few safe adb actions and a deterministic success check.
// Safety: this runner never toggles Wi-Fi, data, Bluetooth, airplane mode,
// developer options or permissions, and always returns to the home screen.
import {execFileSync} from 'node:child_process';
import {readFileSync, writeFileSync, statSync, mkdirSync} from 'node:fs';
import path from 'node:path';

const home = process.env.HOME;
const serial = process.env.PHONE_SERIAL || '127.0.0.1:5555';
const adb = (args, timeout = 30000) => execFileSync('adb', ['-s', serial, ...args],
  {encoding: 'utf8', timeout, maxBuffer: 16 * 1024 * 1024}).toString();
const tasksFile = path.join(path.dirname(new URL(import.meta.url).pathname), 'phone-eval.tasks.json');
const spec = JSON.parse(readFileSync(tasksFile, 'utf8'));
const resultsDir = path.join(home, 'sliver-wolf-companion/behavior');
mkdirSync(resultsDir, {recursive: true});

let passed = 0;
const lines = [];

// Preflight: a disconnected motor bus is not a failed task.
const devices = adb_devices();
if (!devices.some(entry => entry.startsWith(serial + '\t') && entry.includes('device'))) {
  const record = {at: new Date().toISOString(), id: 'preflight', goal: 'adb 设备可用',
    ok: false, error: 'device-unavailable: ' + serial, devices};
  const file = path.join(resultsDir, 'phone-eval-' + new Date().toISOString().slice(0, 10) + '.jsonl');
  writeFileSync(file, JSON.stringify(record) + '\n', {flag: 'a'});
  console.log('SKIP phone-eval: adb 设备不可用 (' + serial + ')');
  console.log('hint: reconnect adb (USB/winpc adb connect, or the app accessibility path)');
  console.log('evidence:', file);
  process.exit(2);
}

function adb_devices() {
  try { return adb(['devices']).trim().split('\n').slice(1).filter(Boolean); }
  catch { return []; }
}

for (const task of spec.tasks) {
  let output = '', screenshotBytes = 0, error = '';
  try {
    for (const action of task.actions) {
      if (action.type === 'key') adb(['shell', 'input', 'keyevent', String(action.code)]);
      else if (action.type === 'start') adb(['shell', 'am', 'start', ...action.args]);
      else if (action.type === 'shell') output += adb(['shell', ...action.args]);
      else if (action.type === 'ui') {
        adb(['shell', 'uiautomator', 'dump', '/sdcard/phone-eval-ui.xml'], 45000);
        output += adb(['shell', 'cat', '/sdcard/phone-eval-ui.xml']);
      } else if (action.type === 'screencap') {
        const target = path.join(home, 'tmp', 'phone-eval-shot.png');
        mkdirSync(path.dirname(target), {recursive: true});
        writeFileSync(target, execFileSync('adb', ['-s', serial, 'exec-out', 'screencap', '-p'],
          {timeout: 45000, maxBuffer: 32 * 1024 * 1024}));
        screenshotBytes = statSync(target).size;
      } else if (action.type === 'bt_toggle') {
        // Low-risk connectivity change (no paired device): toggle through the
        // Settings switch (observe → act → verify), then restore the original state.
        const state = () => adb(['shell', 'settings', 'get', 'global', 'bluetooth_on']).trim();
        const before = state();
        const flip = () => {
          adb(['shell', 'am', 'start', '-a', 'android.settings.BLUETOOTH_SETTINGS']);
          adb(['shell', 'sleep', '1']);
          adb(['shell', 'uiautomator', 'dump', '/sdcard/bt-eval.xml'], 45000);
          const xml = adb(['shell', 'cat', '/sdcard/bt-eval.xml']);
          const tag = xml.split('<node').find(part => part.includes('switch_widget') && part.includes('android.widget.Switch'));
          const bounds = tag && tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
          if (!bounds) throw new Error('bluetooth switch not found');
          const x = Math.round((Number(bounds[1]) + Number(bounds[3])) / 2);
          const y = Math.round((Number(bounds[2]) + Number(bounds[4])) / 2);
          adb(['shell', 'input', 'tap', String(x), String(y)]);
          adb(['shell', 'sleep', '2']);
          return state();
        };
        const after = flip();
        let restored = after;
        if (after !== before) restored = flip();
        if (restored !== before) throw new Error('bluetooth restore failed');
        output += 'bt before=' + before + ' after=' + after + ' restored=' + restored;
      } else if (action.type === 'wait') {
        execFileSync(process.execPath, ['-e', `setTimeout(()=>{},${Number(action.ms) || 1000})`]);
      }
    }
    const check = task.check ?? {};
    if (check.type === 'ui_nonempty') {
      if (output.trim().length < 200) throw new Error('empty UI tree');
    } else if (check.type === 'ui_matches' || check.type === 'output_matches') {
      if (!(check.any ?? []).some(text => output.includes(text))) throw new Error('none of: ' + (check.any ?? []).join(', '));
    } else if (check.type === 'bytes_at_least') {
      if (screenshotBytes < Number(check.value ?? 1)) throw new Error('screenshot too small');
    }
    passed++;
  } catch (failure) { error = String(failure.message ?? failure).split('\n')[0].slice(0, 200); }
  const result = {at: new Date().toISOString(), id: task.id, goal: task.goal,
    ok: !error, error: error || null, screenshotBytes: screenshotBytes || null};
  lines.push(result);
  console.log((result.ok ? 'PASS' : 'FAIL') + ' ' + task.id + ' — ' + task.goal + (error ? ' — ' + error : ''));
}
// Safe-home routine: always end on the home screen.
try { adb(['shell', 'input', 'keyevent', '3']); } catch { /* best effort */ }
const file = path.join(resultsDir, 'phone-eval-' + new Date().toISOString().slice(0, 10) + '.jsonl');
writeFileSync(file, lines.map(line => JSON.stringify(line)).join('\n') + '\n', {flag: 'a'});
console.log(`phone-eval: ${passed}/${lines.length} passed`);
console.log('evidence:', file);
process.exitCode = passed === lines.length ? 0 : 1;
