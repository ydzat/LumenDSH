// Run one declarative phone task JSON file and write its evidence.
// Usage: node tooling/phone-task.mjs tooling/tasks/open-wifi-and-read.json
import {execFileSync} from 'node:child_process';
import {appendFileSync, readFileSync} from 'node:fs';
import path from 'node:path';
import {runTask} from '../integrations/phone/task-runner.mjs';

const home = process.env.HOME;
const serial = process.env.PHONE_SERIAL || '127.0.0.1:5555';
const adb = (args, timeout = 30000) => execFileSync('adb', ['-s', serial, ...args],
  {encoding: 'utf8', timeout, maxBuffer: 32 * 1024 * 1024}).toString();
const file = process.argv[2];
if (!file) { console.error('usage: node tooling/phone-task.mjs <task.json>'); process.exit(2); }
const task = JSON.parse(readFileSync(file, 'utf8'));

const result = await runTask(adb, task.steps, {
  onStep: entry => console.log('step:', entry.step, JSON.stringify(entry).slice(0, 200)),
});

const evidence = path.join(home, 'sliver-wolf-companion/behavior',
  'phone-task-' + new Date().toISOString().slice(0, 10) + '.jsonl');
appendFileSync(evidence, JSON.stringify({at: new Date().toISOString(), task: path.basename(file),
  ok: result.ok, error: result.error ?? null, steps: result.steps}) + '\n');
console.log(result.ok ? 'TASK OK' : 'TASK FAILED: ' + result.error);
console.log('evidence:', evidence);
process.exitCode = result.ok ? 0 : 1;
