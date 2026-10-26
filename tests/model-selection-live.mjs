import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const home=process.env.HOME;
const {cookie}=JSON.parse(await readFile(home+'/.local/state/sliver-wolf-backend/connection-3080.json','utf8'));
async function rpc(method,args={}){const r=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},body:JSON.stringify({type:'client-request',rpcId:randomUUID(),method,payload:{args}})});const j=await r.json();if(!j.result?.ok)throw Error(JSON.stringify(j));return j.result.value;}

import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const root='http://127.0.0.1:3080/sliver-wolf/companion/';
async function select(body){const r=await fetch(root+'model-selection',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},body:JSON.stringify(body)});const j=await r.json();assert.equal(r.status,200,JSON.stringify(j));return j;}
async function model(sid){const r=await fetch(root+'model?sessionId='+encodeURIComponent(sid),{headers:{Cookie:cookie}});assert.equal(r.status,200);return r.json();}
const sid=(await readFile(home+'/sliver-wolf-companion/live-session','utf8')).trim();
const before=await model(sid),catalog=await rpc('session/modelCatalog');
const user=process.argv[2];assert(user,'Pass an existing user session ID');
const userBefore=await model(user);
const list=await rpc('session/list',{_request:{}});assert(list.items.every(x=>!x.running));
assert.equal((await fetch(root+'model-selection',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({scope:'default',...catalog.default})})).status,401);
const alt=catalog.groups[0].models.find(m=>m.id!==catalog.default.model);assert(alt);
try {
await select({scope:'session',sessionId:sid,provider:catalog.groups[0].id,model:alt.id});
assert.equal((await model(sid)).model,alt.id);assert.deepEqual((await rpc('session/modelCatalog')).default,catalog.default);
console.log('PASS session selection preserves default');
await select({scope:'default',provider:catalog.groups[0].id,model:alt.id});
assert.equal((await model(user)).model,userBefore.model);
console.log('PASS default selection preserves existing user session');
await select({scope:'default',...catalog.default});
await select({scope:'session',sessionId:sid,useDefault:true});
assert.equal((await model(sid)).model,catalog.default.model);
console.log('PASS use current default in existing session');
const bad=await fetch(root+'model-selection',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},body:JSON.stringify({scope:'session',sessionId:sid,provider:catalog.groups[0].id,model:'not-a-real-model'})});assert.equal(bad.status,400);assert.equal((await model(sid)).model,catalog.default.model);
console.log('PASS unknown model rejected; unauthorized write rejected');
await select({scope:'session',sessionId:sid,provider:catalog.groups[0].id,model:alt.id,reasoningEffort:'off'});
const require=createRequire(home+'/sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/package.json');const {WebSocket}=require('ws');
let header,completed=false;
const ws=new WebSocket('ws://127.0.0.1:3080/api/remote.mux',{headers:{Cookie:cookie}});
await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject);});
await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Snapshot timeout')),15000);ws.on('message',b=>{const v=JSON.parse(String(b)).value;if(v?.type==='snapshot'){clearTimeout(timer);resolve();}if(v?.type==='event'){if(v.event.type==='request/header')header=v.event.data;if(v.event.type==='turn/end')completed=true;}});ws.send(JSON.stringify({type:'open',streamId:'test',endpoint:'session/follow',payload:{args:{request:{address:{kind:'session',sessionId:sid},maxMessages:1}}}}));ws.send(JSON.stringify({type:'end',streamId:'test'}));});
await rpc('session/prompt',{request:{sessionId:sid,requestId:randomUUID(),mode:'queue',content:[{type:'text',text:'Controlled model routing check. Reply only MODEL_ROUTE_OK. Do not call tools or write memory.'}]}});
for(let i=0;i<120&&!completed;i++)await new Promise(r=>setTimeout(r,1000));ws.close();assert(completed,'Real turn did not finish');
const latest=await model(sid);console.log('HEADER_KEYS',Object.keys(header??{}));
assert.equal(header?.header?.config?.model,alt.id);
await select({scope:'session',sessionId:sid,useDefault:true});
assert((await model(sid)).inputModalities.includes('image'));console.log('PASS real request header follows selection; DSH advertises image input');
console.log('USER_OLD_MODEL',userBefore.model,'DEFAULT',catalog.default.model);

} finally {
  await select({scope:'default',...catalog.default});
  await select({scope:'session',sessionId:sid,provider:before.provider,model:before.model,reasoningEffort:before.reasoningEffort});
}
