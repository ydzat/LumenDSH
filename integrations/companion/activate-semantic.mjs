import {readFile,writeFile,copyFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const h=process.env.HOME,root=h+'/sliver-wolf-companion';
const auth=JSON.parse(await readFile(h+'/.local/state/sliver-wolf-backend/connection-3080.json','utf8'));
const list=await(await fetch('http://127.0.0.1:3080/api/session/list',{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},body:JSON.stringify({type:'client-request',rpcId:'semantic-idle',method:'session/list',payload:{args:{_request:{}}}})})).json();
assert(list.result.ok&&list.result.value.items.every(s=>!s.running),'Wait until current tasks finish');
// Backfill each existing conversation workspace without exporting any memory text.
const workspaces=[...new Set(list.result.value.items.map(s=>s.cwd).filter(Boolean))];
for(const workspace of workspaces){
 const data=workspace+'/.mnemon/data';let stores;try{stores=await (await import('node:fs/promises')).readdir(data,{withFileTypes:true});}catch(e){if(e.code==='ENOENT')continue;throw e;}
 for(const store of stores.filter(s=>s.isDirectory())){
  const args=['--data-dir',workspace+'/.mnemon','--store',store.name,'embed','--all'];
  execFileSync(root+'/bin/mnemon-termux',args,{stdio:'pipe',timeout:120000});
 }
}
const profile=h+'/.dsh/profiles/web/cordis.patch.yml';const previous=await readFile(profile,'utf8');
const anchor='    embedding:\n      enabled: false\n      endpoint: http://localhost:11434\n      model: nomic-embed-text\n';
assert.equal(previous.split(anchor).length,2,'Profile anchor mismatch');
const next=previous.replace(anchor,'    embedding:\n      enabled: true\n      endpoint: http://127.0.0.1:3083/v1\n      model: sw-qwen3-embedding-0.6b-q8\n      protocol: openai\n');
await copyFile(profile,root+'/pre-semantic-profile.patch.yml');
await writeFile(profile,next,{mode:0o600});
execFileSync('tmux',['send-keys','-t','dsh','C-c']);
for(let n=0;;n++){try{execFileSync('tmux',['has-session','-t','=dsh'],{stdio:'ignore'});}catch{break;}assert(n<100,'DSH shutdown timeout');await new Promise(r=>setTimeout(r,200));}
execFileSync('agent-start',[],{stdio:'inherit'});
let verified=false;
for(let n=0;n<60;n++){
 try{
  const current=JSON.parse(await readFile(h+'/.local/state/sliver-wolf-backend/connection-3080.json','utf8'));
  const response=await(await fetch('http://127.0.0.1:3080/api/session/list',{method:'POST',headers:{Cookie:current.cookie,'Content-Type':'application/json'},body:JSON.stringify({type:'client-request',rpcId:'semantic-ready',method:'session/list',payload:{args:{_request:{}}}}),signal:AbortSignal.timeout(2000)})).json();
  if(response.result?.ok){assert(list.result.value.items.every(old=>response.result.value.items.some(item=>item.sessionId===old.sessionId)),'Session disappeared');verified=true;break;}
 }catch{}
 await new Promise(r=>setTimeout(r,1000));
}
assert(verified,'Semantic activation failed to recover DSH; inspect private startup status');
console.log('SEMANTIC_PROFILE_READY_BACKFILL_COMPLETE');
