// Explicit one-time upgrade, not an Android-client side effect. Run after isolated checks.
import {readFile,writeFile,mkdir,cp,rename,lstat,unlink,copyFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=path.resolve(process.argv[2]??'');
assert(root===path.join(process.env.HOME,'sliver-wolf-dsh-upgrade/0.2.1-rc.3'));
const runtime=path.join(root,'runtime'),home=path.join(process.env.HOME,'.dsh');
assert.equal(JSON.parse(await readFile(path.join(runtime,'node_modules/@deepseek-ai/dsh/package.json'),'utf8')).version,'0.2.1-rc.3');
const response=await fetch('http://127.0.0.1:3080/api/session.list',{method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify({type:'client-request',rpcId:'upgrade-cutover',method:'session.list',payload:{}})});
const state=await response.json();assert(state.result.ok&&state.result.value.items.every(x=>!x.running),'Old deployment must be idle');
const backup=path.join(process.env.HOME,'.local/state/sliver-wolf-backend',`migration-data-${Date.now()}`);
await mkdir(backup,{mode:0o700});
// No credential file is copied, exported, or interpreted by this script.
execFileSync('tmux',['send-keys','-t','dsh','C-c']);
let stopped=false;
for(let i=0;i<50;i++){
  try{await fetch('http://127.0.0.1:3080',{signal:AbortSignal.timeout(1000)});}
  catch{stopped=true;break;}
  await new Promise(r=>setTimeout(r,200));
}
assert(stopped,'Old listener did not stop; configuration unchanged');
for(let i=0;i<50;i++){
  try { execFileSync('tmux',['has-session','-t','=dsh'],{stdio:'ignore'}); }
  catch { break; }
  if(i===49)throw new Error('Old tmux process has not finished shutting down');
  await new Promise(r=>setTimeout(r,200));
}
await cp(path.join(home,'sessions'),path.join(backup,'sessions'),{recursive:true,errorOnExist:true,force:false});
await rename(path.join(home,'profiles'),path.join(backup,'profiles'));
await cp(path.join(root,'migration-home/profiles'),path.join(home,'profiles'),{recursive:true,errorOnExist:true,force:false,verbatimSymlinks:true});
const entry=path.join(process.env.PREFIX,'bin/dsh');
assert((await lstat(entry)).isSymbolicLink(),'Expected the inspected old CLI symlink');
await unlink(entry);
await writeFile(entry,`#!/data/data/com.termux/files/usr/bin/bash\nexec node --expose-internals "$HOME/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/node_modules/@deepseek-ai/dsh/lib/bin.js" "$@"\n`,{flag:'wx',mode:0o700});
const launcher=path.join(runtime,'serve.mjs');
await copyFile(path.join(process.env.HOME,'sliver-wolf-build/source/tooling/dsh-upgrade/serve.mjs'),launcher);
await writeFile(path.join(process.env.PREFIX,'bin/agent-start'),`#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
mkdir -p "$HOME/agent-workspace" "$HOME/.local/state/sliver-wolf-backend"
if ! tmux has-session -t =dsh 2>/dev/null; then
  tmux new-session -d -s dsh -c "$HOME/agent-workspace" 'exec node "${launcher}" "${runtime}" "$HOME/.dsh" "$HOME/agent-workspace" 3080 >> "$HOME/.local/state/sliver-wolf-backend/status.log" 2>&1'
fi
printf 'DSH 0.2.1-rc.3: http://127.0.0.1:3080\\n'
`,{mode:0o700});
await writeFile(path.join(backup,'receipt.json'),JSON.stringify({version:'0.2.1-rc.3',sessionCount:state.result.value.items.length,credentialHandling:'Original file left in place for official provider migration',runtime,stage:'starting'},null,2),{mode:0o600});
execFileSync('agent-start',[],{stdio:'inherit'});
console.log(JSON.stringify({check:'cutover-started',dataBackup:backup,oldRuntimeRemoved:false}));
