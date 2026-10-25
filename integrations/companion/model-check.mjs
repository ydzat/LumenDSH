// Real selected-model changes in the existing controlled workspace, no inference.
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME;
const auth=JSON.parse(await readFile(path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
const sid=(await readFile(path.join(home,'sliver-wolf-companion/live-session'),'utf8')).trim();
const headers={Cookie:auth.cookie,'Content-Type':'application/json'};
const patchFile=path.join(home,'.dsh/profiles/web/cordis.patch.yml');
const blocks=text=>text.split(/(?=^- )/m);
const defaultBlock=text=>blocks(text).find(b=>/^- id: agent-default-model\s*$/m.test(b));
const originalDefault=defaultBlock(await readFile(patchFile,'utf8'));
async function rpc(method,args) {
  const r=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers,
    body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args}})});
  const v=await r.json();assert(v.result.ok);return v.result.value;
}
const endpoint='http://127.0.0.1:3080/sliver-wolf/companion/model?sessionId='+encodeURIComponent(sid);
async function read(){const r=await fetch(endpoint,{headers});assert.equal(r.status,200);return r.json();}
const before=await read();
const catalog=await rpc('session/modelCatalog',{});
assert.equal(catalog.default.provider,before.provider);assert.equal(catalog.default.model,before.model);
const sessions=await rpc('session/list',{_request:{}});
assert(sessions.items.every(s=>!s.running),'Wait for all current work before controlled selection');
assert.equal(sessions.items.find(s=>s.sessionId===sid)?.cwd,path.join(home,'sliver-wolf-companion/live-workspace'));
const group=catalog.groups.find(g=>g.id===before.provider);assert(group.models.length>=2);
assert.equal((await fetch(endpoint)).status,401);
try {
  for(const entry of [...group.models].reverse()) {
    await rpc('session/selectModel',{request:{sessionId:sid,provider:group.id,model:entry.id}});
    const current=await read();assert.equal(current.model,entry.id);assert.equal(current.sessionId,sid);
    assert(current.contextWindow>0);assert(current.defaultOutputTokens>0);
    assert.equal(current.maxOutputTokens,null);
    assert.equal(current.autonomousOutputCeiling,Math.min(current.defaultOutputTokens,current.userOutputBudget));
    console.log('REAL_MODEL_SELECTED',current.model,current.contextWindow,current.defaultOutputTokens);
    // Allow the on-screen client to consume its native model projection event.
    await new Promise(r=>setTimeout(r,3000));
  }
  console.log('REAL_DSH_MODEL_SWITCH_METADATA_AUTH_PASS');
} finally {
  await rpc('session/selectModel',{request:{sessionId:sid,...catalog.default}});
  for(let i=0;i<30;i++) {
    const current=await rpc('session/modelCatalog',{});
    if(current.default.provider===catalog.default.provider&&current.default.model===catalog.default.model)break;
    assert(i<29,'Default model restoration did not settle');await new Promise(r=>setTimeout(r,100));
  }
  assert.equal((await read()).model,before.model);
  // Native selection persists normalized reasoning defaults. Restore the exact
  // original override block, including absence, without rewriting other config.
  const current=await readFile(patchFile,'utf8');
  const retained=blocks(current).filter(b=>!/^\- id: agent-default-model\s*$/m.test(b)).join('');
  await writeFile(patchFile,retained+(originalDefault??''),{mode:0o600});
  console.log('ORIGINAL_MODEL_RESTORED_DEFAULT_OVERRIDE_RESTORED_RELOAD_REQUIRED');
}
