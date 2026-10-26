// Test hygiene: stop supervisors, stacks and relays left over from an
// interrupted test run. Only processes whose command line carries a scratch
// entrance port (399xx) are touched; the live 3080 stack is never matched.
import {readFileSync, readdirSync} from 'node:fs';

const scratch = /--port 399\d\d\b/;
const roles = /integrations\/supervisor\/(supervise|stack|relay)\.mjs/;
let killed = 0;
for (const entry of readdirSync('/proc')) {
  if (!/^\d+$/.test(entry)) continue;
  let cmdline = '';
  try { cmdline = readFileSync(`/proc/${entry}/cmdline`, 'utf8').replaceAll('\0', ' '); } catch { continue; }
  if (!roles.test(cmdline) || !scratch.test(cmdline)) continue;
  try { process.kill(Number(entry), 'SIGKILL'); killed++; console.log('killed', entry, cmdline.trim()); }
  catch (error) { console.log('could not kill', entry, error.message); }
}
console.log('scratch processes stopped:', killed);
