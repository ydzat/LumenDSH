// Native compaction over the existing controlled conversation, never the owner log.
import {readFile,writeFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {once} from 'node:events';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME,root=path.join(home,'sliver-wolf-companion');
const auth=JSON.parse(await readFile(path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
const sid=(await readFile(path.join(root,'live-session'),'utf8')).trim();
async function rpc(method,args) {
  const r=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args}}),signal:AbortSignal.timeout(180000)});
  const result=await r.json();assert(result.result.ok,JSON.stringify(result.result.error));return result.result.value;
}
const sessions=await rpc('session/list',{_request:{}});
const selected=sessions.items.find(x=>x.sessionId===sid);
assert(selected&&!selected.running);assert.equal(selected.cwd,path.join(root,'live-workspace'));
const workspaceId=(await rpc('workspace/create',{request:{path:selected.cwd}})).workspace.workspaceId;
async function memory(endpoint,input={}) {
  const result=await rpc('dshMnemon/read',{endpoint,payload:{sessionId:sid,workspaceId,...input}});
  assert(result.ok,JSON.stringify(result.error));return result.value;
}
const before=await memory('documents');assert(before.documents.length>0,'Controlled source document required');
const require=createRequire(path.join(home,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/package.json'));
const {WebSocket}=require('ws');
const socket=new WebSocket('ws://127.0.0.1:3080/api/remote.mux',{headers:{Cookie:auth.cookie}});
const events=[];let ready=false;
socket.on('message',bytes=>{const message=JSON.parse(String(bytes)),frame=message.value??message.payload;
  if(frame?.type==='snapshot'){ready=true;events.push(...frame.records.map(x=>x.event));}
  if(frame?.type==='event')events.push(frame.event.event??frame.event);
});
try {
  await once(socket,'open');socket.send(JSON.stringify({type:'open',streamId:'native-context-check',endpoint:'session/follow',
    payload:{args:{request:{address:{kind:'session',sessionId:sid},maxMessages:100,assistantStream:true}}}}));
  for(let n=0;n<50&&!ready;n++)await new Promise(r=>setTimeout(r,100));assert(ready);
  const offset=events.length;
  const result=await rpc('commands/execute',{agentId:sid,line:'/compact',submittedAttachments:[]});
  assert.equal(result.result.kind,'success',JSON.stringify(result.result));
  assert(Number.isInteger(result.result.sourceEventSeq),'No actual compactable history was processed');
  for(let n=0;n<30&&!events.slice(offset).some(x=>x.type==='compaction/end');n++)await new Promise(r=>setTimeout(r,100));
  const fresh=events.slice(offset);
  assert(fresh.some(x=>x.type==='compaction/summary'),'Native summary event missing');
  assert(fresh.some(x=>x.type==='compaction/end'),'Native completion event missing');
  const after=await memory('documents');assert.equal(after.directory,before.directory);
  for(const doc of before.documents)assert(after.documents.some(x=>x.id===doc.id&&x.contentHash===doc.contentHash),'Compaction altered durable memory');
  const searched=await memory('document-search',{query:'共同经历的受控记忆验收记录',limit:5});
  assert.equal(searched.retrieval,'mnemon-native-semantic');assert(searched.results.length>0);
  const expanded=await memory('document',{id:searched.results[0].id});assert(expanded.content.length>0&&expanded.sessionIds.includes(sid));
  await writeFile(path.join(root,'context-check.json'),JSON.stringify({sessionId:sid,summarySeq:result.result.sourceEventSeq,
    documentsPreserved:before.documents.length,semanticSourceExpanded:true,eventTypes:fresh.map(x=>x.type)},null,2)+'\n',{mode:0o600});
  console.log('REAL_NATIVE_COMPACTION_DURABLE_MEMORY_AND_SEMANTIC_SOURCE_PASS');
} finally {socket.terminate();}
