// Real rc.3 plugin admission/configuration check in the existing isolated probe host.
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {PolicyStore} from './policy.mjs';
import {watch,readFileSync} from 'node:fs';
import {dayOf} from './upstream/night-plan.mjs';
const home=process.env.HOME,root=path.join(home,'sliver-wolf-companion');
let crashObserved,crashWatcher;
const patchFile=path.join(root,'probe-home/profiles/web/cordis.patch.yml');
let patch=await readFile(patchFile,'utf8');
if(!patch.includes('# companion probe directory')) {
  patch+='\n# companion probe directory\n- id: sliver-wolf-companion\n  config:\n    directory: '+JSON.stringify(path.join(root,'probe-behavior'))+'\n';
  await writeFile(patchFile,patch,{mode:0o600});
}
if(process.argv.includes('--documents') && !patch.includes('# authenticated document probe')) {
  patch+='\n# authenticated document probe\n- id: mnemon\n  config:\n    remoteAccess: trusted-host\n';
  await writeFile(patchFile,patch,{mode:0o600});
}
if(process.argv.includes('--documents')) {
  if(!patch.includes('# workspace-scoped document probe'))patch+='\n# workspace-scoped document probe\n- id: mnemon\n  config:\n    storageScope: workspace\n';
  patch=patch.replace(/# workspace-scoped document probe\n- id: mnemon\n  config:\n(?:    [^\n]*\n)*/,
    '# workspace-scoped document probe\n- id: mnemon\n  config:\n    storageScope: workspace\n    remoteAccess: trusted-host\n    timeoutMs: 120000\n');
  patch=patch.replace('- id: mnemon-source-documents\n  name: dsh-mnemon-source-documents',
    '- id: mnemon-source-documents\n  disabled: true\n  name: dsh-mnemon-source-documents');
  patch=patch.replace('- id: mnemon-source-documents\n  name: sliver-wolf-companion/documents',
    '- id: mnemon-source-documents\n  disabled: true\n  name: dsh-mnemon-source-documents');
  await writeFile(patchFile,patch,{mode:0o600});
}
const child=spawn('node',['--expose-internals',path.join(home,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime/node_modules/@deepseek-ai/dsh/lib/bin.js'),
  'web','--host','127.0.0.1','--port','3082','--no-open'],{cwd:path.join(root,'probe-workspace'),
  env:{...process.env,DSH_HOME:path.join(root,'probe-home'),DSH_TELEMETRY_DISABLED:'1'},stdio:['ignore','pipe','pipe']});
let log='';child.stdout.on('data',b=>log=(log+String(b)).slice(-131072));child.stderr.on('data',b=>log=(log+String(b)).slice(-131072));
try {
  let token;
  for(let n=0;n<200 && child.exitCode===null;n++) {
    token=log.match(/[?&]token=([^\s]+)/)?.[1];if(token)break;
    await new Promise(r=>setTimeout(r,200));
  }
  assert(token,'Isolated host did not start');
  const exchange=await fetch('http://127.0.0.1:3082/?token='+token,{redirect:'manual'});
  const cookie=exchange.headers.get('set-cookie')?.split(';')[0];assert(cookie);
  const base='http://127.0.0.1:3082/sliver-wolf/companion/';
  const unauth=await fetch(base+'status');assert.equal(unauth.status,401);
  const read=async()=> {const r=await fetch(base+'status',{headers:{Cookie:cookie}});assert.equal(r.status,200);return r.json();};
  const post=async(endpoint,body)=>fetch(base+endpoint,{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},body:JSON.stringify(body)});
  async function rpc(method,args) {
    const r=await fetch('http://127.0.0.1:3082/api/'+method,{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},
      body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args}}),signal:AbortSignal.timeout(180000)});
    const v=await r.json();assert(v.result.ok,JSON.stringify(v.result.error));return v.result.value;
  }
  const first=await read();assert.equal(first.policy.schemaVersion,1);
  if(process.argv.includes('--recovery')) {
    const receipt=JSON.parse(await readFile(path.join(root,'probe-crash-receipt.json'),'utf8'));
    const recovered=first.runs.find(x=>x.id===receipt.id);
    assert.equal(recovered?.status,'interrupted-unreplayed');assert.equal(first.active,null);
    assert.equal(first.daily[receipt.day],receipt.daily,'Reservation budget was reset on restart');
    await new Promise(r=>setTimeout(r,16000));const later=await read();
    assert.equal(later.runs.length,first.runs.length,'Interrupted delivery was replayed');
    console.log('REAL_DSH_KILLED_RESERVATION_RESTART_NO_REPLAY_PASS');
  }
  const changed=await post('policy',{expectedRevision:first.revision,policy:{...first.policy,agentName:'Probe',enabled:false}});
  assert.equal(changed.status,200);const second=await changed.json();assert.equal(second.policy.agentName,'Probe');
  const stale=await post('policy',{expectedRevision:first.revision,policy:first.policy});assert.equal(stale.status,400);
  const restored=await post('restore',{expectedRevision:second.revision,revision:first.revision});assert.equal(restored.status,200);
  assert.equal((await restored.json()).revision,first.revision);
  console.log('REAL_DSH_COMPANION_AUTH_POLICY_HISTORY_PASS');
  if(process.argv.includes('--documents')) {
    const workspace=path.join(root,'probe-documents-'+Date.now());await mkdir(workspace,{mode:0o700});
    const workspaceId=(await rpc('workspace/create',{request:{path:workspace}})).workspace.workspaceId;
    const sid=(await rpc('session/create',{request:{workspaceId}})).sessionId;
    async function memory(mode,endpoint,input={}) {
      const result=await rpc('dshMnemon/'+mode,{endpoint,payload:{sessionId:sid,workspaceId,...input}});
      assert(result.ok,JSON.stringify(result.error));return result.value;
    }
    const document=await memory('write','document',{action:'create',title:'Controlled dietary notes '+Date.now(),
      content:(process.argv.includes('--long')?'甲乙丙丁戊己庚辛壬癸'.repeat(320)+'\n':'')+'受控角色阿澈无法消化乳糖，饮用咖啡时须使用燕麦饮代替牛乳。',sessionIds:[sid]});
    const id=document.document.id;
    await memory('write','document',{action:'create',title:'Controlled weekend notes '+Date.now(),content:'周末去山里徒步，避免去商场购物。',sessionIds:[sid]});
    const found=await memory('read','document-search',{query:'阿澈点拿铁时应怎样调整配料？',limit:3});
    assert.equal(found.retrieval,'mnemon-native-semantic','Document adapter was not selected');
    assert.equal(found.results[0]?.id,id,'Semantic Document paraphrase did not rank first');
    assert(found.results[0].excerpt.includes('燕麦饮'),'Relevant semantic passage missing');
    const repeated=await memory('read','document-search',{query:'阿澈点拿铁时应怎样调整配料？',limit:3});assert.equal(repeated.results[0]?.id,id);
    await memory('write','document',{action:'update',id,content:'受控角色阿澈现在改喝豆乳，燕麦饮已经不合适。',sessionIds:[sid]});
    const revised=await memory('read','document-search',{query:'阿澈的奶咖现在要使用什么替代品？',limit:3});
    assert.equal(revised.results[0]?.id,id);assert(revised.results[0].content.includes('豆乳'));
    assert(!revised.results.some(x=>x.id===id&&x.content.includes('无法消化乳糖')),'Stale document revision was returned');
    const unrelated=path.join(workspace,'separate-scope');await mkdir(unrelated,{mode:0o700});
    const unrelatedId=(await rpc('workspace/create',{request:{path:unrelated}})).workspace.workspaceId;
    const unrelatedSession=(await rpc('session/create',{request:{workspaceId:unrelatedId}})).sessionId;
    const isolated=await memory('read','document-search',{sessionId:unrelatedSession,workspaceId:unrelatedId,query:'阿澈点拿铁时应怎样调整配料？'});
    assert.equal(isolated.results.length,0,'Semantic Documents crossed workspace boundaries');
    console.log('REAL_DSH_DOCUMENT_SEMANTIC_PARAPHRASE_CACHE_REVISION_PASS');
    console.log('REAL_DSH_DOCUMENT_WORKSPACE_ISOLATION_PASS');
  }
  if(process.argv.includes('--execution')||process.argv.includes('--crash')) {
    const crash=process.argv.includes('--crash');
    const created=await rpc('session/create',{request:{cwd:path.join(root,'probe-workspace')}});
    const sid=created.sessionId;assert(sid);
    const current=await read();
    const now=new Date(Date.now()+60000),later=new Date(Date.now()+180000);
    const hm=d=>String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
    const policy={...current.policy,sessionId:sid,enabled:true,paused:false,skipChance:0,
      windows:[{start:hm(now),end:hm(later),wakes:(current.plan.wakes??0)+1}],idleMinutes:1,minGapMin:1,
      budget:{dailyRuns:(current.daily[dayOf(Date.now())]??0)+1,steps:2,toolCalls:2,writes:0,outputTokens:256,seconds:30}};
    let exited;
    if(crash) {
      exited=once(child,'exit');
      crashWatcher=watch(path.join(root,'probe-behavior'),(_event,file)=>{
        if(String(file)!=='state.json'||crashObserved)return;
        const state=JSON.parse(readFileSync(path.join(root,'probe-behavior/state.json'),'utf8'));
        const run=state.runs.at(-1);
        if(run?.sessionId!==sid||!['reserved','running'].includes(run.status))return;
        crashObserved={id:run.id,day:dayOf(Date.now()),daily:state.daily[dayOf(Date.now())]};
        child.kill('SIGKILL');
      });
    }
    const configured=await post('policy',{expectedRevision:current.revision,policy});assert.equal(configured.status,200);
    if(crash) {
      const timeout=setTimeout(()=>child.kill('SIGTERM'),240000);
      try {await exited;}finally {clearTimeout(timeout);crashWatcher.close();}
      assert(crashObserved,'No actual scheduled reservation was interrupted');
      await writeFile(path.join(root,'probe-crash-receipt.json'),JSON.stringify(crashObserved),{mode:0o600});
      console.log('REAL_DSH_SCHEDULED_RESERVATION_PROCESS_KILLED');
    } else {
    const baseline=current.runs.length;
    let observed;
    for(let n=0;n<240;n++) {
      await new Promise(r=>setTimeout(r,1000));const actual=await read();
      if(actual.runs.length>baseline && !actual.active){observed=actual.runs.at(-1);break;}
    }
    assert(observed,'Scheduled native turn did not settle');
    assert.equal(observed.sessionId,sid);assert(observed.steps>0,'Native pre-step hook did not run');
    assert(observed.writesAttempted===0,'Zero-write budget failed');
    console.log(JSON.stringify({check:'REAL_DSH_SCHEDULED_SCOPED_TURN',status:observed.status,steps:observed.steps,tools:observed.tools,writesAttempted:observed.writesAttempted}));
    const latest=await read();
    assert.equal((await post('policy',{expectedRevision:latest.revision,policy:{...latest.policy,paused:true}})).status,200);
    const count=(await read()).runs.length;await new Promise(r=>setTimeout(r,16000));assert.equal((await read()).runs.length,count);
    console.log('REAL_DSH_GLOBAL_PAUSE_PASS');
    }
  }
} catch(error) {
  console.error(String(error));
  console.error(log.replace(/([?&]token=)[^\s]+/g,'$1[redacted]').slice(-5000));process.exitCode=1;
} finally {
  crashWatcher?.close();
  if(!crashObserved) {const store=new PolicyStore(path.join(root,'probe-behavior'));store.save({...store.read(),enabled:false,paused:true});}
  if(child.exitCode===null&&child.signalCode===null){child.kill('SIGTERM');await once(child,'exit');}
}
