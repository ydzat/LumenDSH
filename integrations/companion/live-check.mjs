import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {once} from 'node:events';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME,root=path.join(home,'sliver-wolf-companion');
const auth=JSON.parse(await readFile(path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
const require=createRequire(path.join(home,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/package.json'));
const {WebSocket}=require('ws');
async function rpc(method,args){
  const id=crypto.randomUUID();
  const response=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:id,method,payload:{args}}),signal:AbortSignal.timeout(30000)});
  const reply=await response.json();assert.equal(reply.rpcId,id);assert(reply.result.ok,JSON.stringify(reply.result.error));return reply.result.value;
}
await mkdir(path.join(root,'live-workspace'),{recursive:true});
const mode=process.argv[2]??'remember';
const session=mode==='remember'?(await rpc('session/create',{request:{cwd:path.join(root,'live-workspace')}})).sessionId:
  (await readFile(path.join(root,'live-session'),'utf8')).trim();
await rpc('session/rename',{request:{sessionId:session,title:'Controlled M7 memory verification'}});
await writeFile(path.join(root,'live-session'),session,{mode:0o600});
const socket=new WebSocket('ws://127.0.0.1:3080/api/remote.mux',{headers:{Cookie:auth.cookie}});
const records=[];let snapshot=false;
socket.on('message',bytes=>{
 const m=JSON.parse(String(bytes));
 const value=m.value??m.payload;
 if(value?.type==='snapshot'){snapshot=true;records.push(...value.records.map(r=>r.event));}
 if(value?.type==='event') records.push(value.event.event??value.event);
});
try{
 await once(socket,'open');socket.send(JSON.stringify({type:'open',streamId:'memory-check',endpoint:'session/follow',payload:{args:{request:{address:{kind:'session',sessionId:session},maxMessages:50,assistantStream:true}}}}));
 for(let i=0;i<50&&!snapshot;i++)await new Promise(r=>setTimeout(r,100));
 assert(snapshot,'Follow snapshot missing');
 const prompts={
  unrelated:'这是隔离工作区的无关查询与用户任务验收。请实际调用 mnemon_recall 和 mnemon_document_search 各一次，查找“用户的火星飞船注册编号”。不要写入记忆，不调用 shell，不推测或编造编号。如果两个来源都没有这个事实，只回复 NO_SUPPORTED_MEMORY。',
  semanticRemember:'这是隔离工作区的受控语义检索验收。请实际调用 mnemon_remember，将“受控角色阿澈无法消化乳糖，咖啡需要使用燕麦饮代替牛乳。”写入当前 M7 controlled memory 长期记忆空间。不要仅写 MEMORY.md，不调用 shell。',
  semanticRecall:'请实际调用 mnemon_recall，语义查找受控角色阿澈点拿铁时该如何调整配料。不要传入 keyword 或 basic 模式，不更新记忆，不调用 shell。只根据检索结果回答。',

  remember:'这是隔离工作区中的真实记忆验收，不是真实用户偏好。请先简短说明你的名字，然后使用记忆工具保存一条受控测试事实：M7_TEST_LANTERN 的颜色是紫色。请实际调用工具，并在成功后回复“MEMORY_WRITE_DONE”。不要调用 shell、不创建定时任务、不询问用户。',
  recall:'请读取当前工作区的人格指引，然后用一句中文说你的名字，并根据持久记忆回答 M7_TEST_LANTERN 的颜色。不创建新记忆、不调用 shell。',
  space:'请列出当前可用的长期记忆空间和记忆工具，不写入、不调用 shell。简短说明能否创建空间。',
  durable:'请使用记忆工具，把受控测试事实“M7_ARCHIVE_STAR 的颜色是琥珀色”保存到当前隔离工作区中的 M7 controlled memory 长期记忆空间。请实际写入该空间，不要仅写入 MEMORY.md，不调用 shell。',
  durableRecall:'请使用 mnemon_recall 从长期记忆空间检索 M7_ARCHIVE_STAR 的颜色。只读，不更新记忆、不调用 shell。',
  correct:'请使用记忆工具更正受控测试事实：M7_TEST_LANTERN 的颜色改为青色。这不是用户偏好。修改已有记录，不新增重复条目、不调用 shell。',
  forget:'请使用记忆工具删除 M7_TEST_LANTERN 这条受控测试事实，不再保留它。不要调用 shell。',
 };
 const prompt=prompts[mode];assert(prompt,'Unknown check mode');
 const start=records.length;
 const accepted=await rpc('session/prompt',{request:{sessionId:session,requestId:crypto.randomUUID(),mode:'queue',content:[{type:'text',text:prompt}]}});assert(accepted.accepted);
 let idle=0;
 for(let i=0;i<180;i++){
  await new Promise(r=>setTimeout(r,1000));
  const list=await rpc('session/list',{_request:{}});const item=list.items.find(x=>x.sessionId===session);
  if(i>3&&!item.running){if(++idle>=2)break;}else idle=0;
 }
 await writeFile(path.join(root,'controlled-events.json'),JSON.stringify(records,null,2),{mode:0o600});
 const fresh=records.slice(start);
 if(mode==='unrelated') {
  const calls=fresh.filter(x=>x.type==='tool/call').map(x=>x.data?.name??x.data?.toolName);
  assert(calls.includes('mnemon_recall')&&calls.includes('mnemon_document_search'),'User retrieval tools were not actually called');
  const text=fresh.filter(x=>x.type==='assistant/message').flatMap(x=>x.data?.message?.content??[]).filter(x=>x.type==='text').map(x=>x.text).join('\n');
  assert(text.includes('NO_SUPPORTED_MEMORY'),'Unsupported personal fact was not explicitly rejected');
  console.log('REAL_USER_MULTI_TOOL_UNRELATED_MEMORY_REJECTION_PASS');
 }
 console.log(JSON.stringify({session,events:fresh.length,tools:fresh.filter(x=>x.type==='tool/call').map(x=>x.data?.name??x.data?.toolName),
   replies:fresh.filter(x=>x.type==='assistant/message').flatMap(x=>x.data?.message?.content??[]).filter(x=>x.type==='text').map(x=>x.text)}));
 const workspace=(await rpc('workspace/create',{request:{path:path.join(root,'live-workspace')}})).workspace.workspaceId;
 const memory=await rpc('dshMnemon/read',{endpoint:'runtime-memory',payload:{sessionId:session,workspaceId:workspace}});
 assert(memory.ok,JSON.stringify(memory.error));
 console.log(JSON.stringify({memoryEntries:memory.value.entries.map(e=>({target:e.target,content:e.content}))}));
 if(mode==='remember')assert(memory.value.entries.some(e=>e.content.includes('M7_TEST_LANTERN')),'Actual memory write missing');
 if(mode==='correct')assert(memory.value.entries.some(e=>e.content.includes('M7_TEST_LANTERN')&&e.content.includes('青色')),'Correction missing');
 if(mode==='forget')assert(!memory.value.entries.some(e=>e.content.includes('M7_TEST_LANTERN')),'Forget did not remove record');
 console.log('M7_REAL_MEMORY_CHECK_OK '+mode);
}finally{socket.terminate();}
