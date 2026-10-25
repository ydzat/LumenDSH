// Explicit user decision: one supported/runtime version; retain data, not old executables.
import {readFile,realpath,rm,readdir,lstat,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME,prefix=process.env.PREFIX;
const runtime=path.join(home,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime');
const old=path.join(prefix,'opt/dsh-0.1.0-rc.7-termux.1');
const backup=path.resolve(process.argv[2]??'');
assert(backup.startsWith(path.join(home,'.local/state/sliver-wolf-backend/migration-data-')));
assert.equal(await realpath(backup),backup);
assert.equal(await realpath(old),old);
assert.equal(JSON.parse(await readFile(path.join(old,'package.json'),'utf8')).version,'0.1.0-rc.7-termux.1');
assert((await readFile(path.join(prefix,'bin/dsh'),'utf8')).includes('/0.2.1-rc.3/runtime/'));
const connection=JSON.parse(await readFile(path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
const response=await fetch('http://127.0.0.1:3080/api/session/list',{method:'POST',headers:{Cookie:connection.cookie,'Content-Type':'application/json'},
  body:JSON.stringify({type:'client-request',rpcId:'retire-old-preflight',method:'session/list',payload:{args:{_request:{}}}})});
const result=await response.json();assert(result.result.ok);
const originals=result.result.value.items.filter(x=>x.cwd===path.join(home,'agent-workspace'));
assert(originals.length===2&&originals.every(x=>x.formatStatus==='current'),'Expected both migrated original sessions');
let candidateAlive=false;
try{await fetch('http://127.0.0.1:3082',{signal:AbortSignal.timeout(1000)});candidateAlive=true;}catch{}
assert(!candidateAlive,'Stop the isolated candidate before retirement');
// Bound every recursive removal to the exact inspected version or backup directory.
await rm(old,{recursive:true});
async function removeDependencies(dir){
  for(const entry of await readdir(dir,{withFileTypes:true})){
    const target=path.join(dir,entry.name);
    if(entry.name==='node_modules'){
      assert(target.startsWith(backup+'/'));
      assert(!(await lstat(target)).isSymbolicLink(),'Inspect an unexpected dependency symlink before removal');
      await rm(target,{recursive:true});
    }else if(entry.isDirectory())await removeDependencies(target);
  }
}
await removeDependencies(path.join(backup,'profiles'));
try{execFileSync('tmux',['kill-session','-t','=dsh-candidate'],{stdio:'ignore'});}catch{}
await rm(path.join(home,'.local/state/sliver-wolf-backend/connection-3082.json'),{force:true});
await writeFile(path.join(backup,'retirement.json'),JSON.stringify({version:'0.2.1-rc.3',runtime,oldRuntimeRemoved:true,
  preserved:'Session data and profile configuration only; original credential file remains managed by DSH.'},null,2),{mode:0o600});
console.log('OLD_DSH_RUNTIME_AND_PROFILE_DEPENDENCIES_REMOVED');
