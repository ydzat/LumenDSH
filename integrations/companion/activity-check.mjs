// Exercise actual scheduling in the pre-existing controlled conversation.
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {dayOf} from './upstream/night-plan.mjs';
import {createRequire} from 'node:module';
import {once} from 'node:events';
const root=path.join(process.env.HOME,'sliver-wolf-companion');
const auth=JSON.parse(await readFile(path.join(process.env.HOME,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
const sid=(await readFile(path.join(root,'live-session'),'utf8')).trim();
const base='http://127.0.0.1:3080/sliver-wolf/companion/';
async function request(op,body){const r=await fetch(base+op,{method:body?'POST':'GET',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
  ...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(15000)});const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));return data;}
const before=await request('status');
const mode=process.argv[2]??'complete';
assert(['complete','step-budget','time-budget','handover'].includes(mode));
let socket,handId;const events=[];
if(mode==='handover') {
  const require=createRequire(path.join(process.env.HOME,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/package.json'));
  const {WebSocket}=require('ws');socket=new WebSocket('ws://127.0.0.1:3080/api/remote.mux',{headers:{Cookie:auth.cookie}});
  let opened=false;socket.on('message',bytes=>{const m=JSON.parse(String(bytes)),f=m.value??m.payload;
    if(f?.type==='snapshot'){opened=true;events.push(...f.records.map(x=>x.event));}
    if(f?.type==='event')events.push(f.event.event??f.event);
  });
  await once(socket,'open');socket.send(JSON.stringify({type:'open',streamId:'handover',endpoint:'session/follow',payload:{args:{request:{address:{kind:'session',sessionId:sid},maxMessages:100,assistantStream:true}}}}));
  for(let n=0;n<50&&!opened;n++)await new Promise(r=>setTimeout(r,100));assert(opened);
}
const hm=d=>String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
let result;
try {
  const policy={...before.policy,sessionId:sid,enabled:true,paused:false,skipChance:0,
    windows:[{start:hm(new Date(Date.now()+60000)),end:hm(new Date(Date.now()+180000)),wakes:(before.plan.wakes??0)+1}],
    idleMinutes:1,minGapMin:1,activities:['consolidate'],
    budget:{dailyRuns:(before.daily[dayOf(Date.now())]??0)+1,steps:mode==='step-budget'?1:8,toolCalls:12,writes:mode==='complete'?1:0,outputTokens:2048,seconds:mode==='time-budget'?10:150}};
  await request('policy',{expectedRevision:before.revision,policy});
  console.log('REAL_ACTIVITY_CONFIGURED_EXISTING_CONTROLLED_SESSION');
  let handed=false;
  for(let n=0;n<360;n++) {
    const current=await request('status');
    if(mode==='handover'&&!handed&&current.active&&current.runs.at(-1)?.status==='running') {
      const method='session/prompt';
      handId=crypto.randomUUID();
      const response=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
        body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args:{request:{sessionId:sid,requestId:handId,mode:'steer',content:[{type:'text',text:'这是受控用户接管检查。停止自主整理，实际调用 mnemon_document_search 和 mnemon_recall 各一次查找受控验收记录，不写记忆，不调用 shell，然后简短回复用户任务完成。'}]}}}})});
      const reply=await response.json();assert(reply.result.ok&&reply.result.value.accepted);handed=true;
    }
    if(current.runs.length>before.runs.length&&!current.active){result=current.runs.at(-1);break;}
    await new Promise(r=>setTimeout(r,1000));
  }
  assert(result,'Scheduled activity did not finish');assert.equal(result.sessionId,sid);
  assert(result.steps>0);assert(result.writesAttempted<=policy.budget.writes);assert(result.tools<=12);
  console.log(JSON.stringify({check:'REAL_SAME_SESSION_ACTIVITY',status:result.status,steps:result.steps,
    tools:result.tools,writesAttempted:result.writesAttempted,failureCode:result.failureCode??null}));
  await writeFile(path.join(root,'activity-check-'+mode+'.json'),JSON.stringify(result,null,2)+'\n',{mode:0o600});
  if(mode==='complete')assert(['completed','step-budget'].includes(result.status),'Activity did not complete normally');
  else if(mode==='handover') {
    assert(handed);assert.equal(result.status,'yielded-to-user');
    for(let n=0;n<180;n++) {
      const method='session/list';const r=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
        body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args:{_request:{}}}})});
      const reply=await r.json();assert(reply.result.ok);
      if(n>1&&!reply.result.value.items.find(x=>x.sessionId===sid)?.running)break;
      await new Promise(r=>setTimeout(r,1000));
    }
    const matched=events.filter(e=>e.type==='user/message'&&e.data?.source?.rpcId===handId);
    assert.equal(matched.length,1,'User handover was lost or duplicated');
    const userIndex=events.indexOf(matched[0]),after=events.slice(userIndex);
    for(const name of ['mnemon_document_search','mnemon_recall']) {
      const call=after.find(e=>e.type==='tool/call'&&e.data?.name===name);assert(call,'Missing user tool '+name);
      const resultEvent=after.find(e=>e.type==='tool/result'&&e.data?.message?.source?.callId===call.data.callId);
      assert(resultEvent,'Missing user tool result');
      const text=resultEvent.data.message.content.filter(x=>x.type==='text').map(x=>x.text).join('');
      const value=JSON.parse(text);assert(!value.notRun&&!value.budgetExhausted,'Autonomous work consumed the user retrieval budget');
    }
    console.log('REAL_USER_HANDOVER_EXACTLY_ONCE_NATIVE_MEMORY_BUDGET_PASS');
  }
  else assert.equal(result.status,mode,'Requested native budget branch was not exercised');
} finally {
  socket?.terminate();
  const current=await request('status');
  await request('policy',{expectedRevision:current.revision,policy:before.policy});
  console.log('ORIGINAL_BEHAVIOR_POLICY_RESTORED');
}
