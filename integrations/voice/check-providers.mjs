// Real installed-provider operations. Credentials are consumed from a private one-time file.
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {connectDevice} from '../device-tools/bridge.mjs';
const device=await connectDevice();
const call=(name,args={})=>device.request({method:'call',name,arguments:args});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const root=process.env.HOME+'/sliver-wolf-build/private';
const mode=process.argv[2]??'play';
async function activate(engine){
 const before=await call('assistant_release_status',{componentId:'profile'});
 const current=JSON.parse(before.currentPayload),appearance=current.components.appearance.version;
 const prepared=await call('assistant_prepare_profile',{displayName:current.displayName,appearanceVersion:appearance==='builtin-v1'?'':appearance,
  voice:{schemaVersion:1,kind:'voice-profile',engine,language:'zh-CN',rate:1,pitch:1}});
 const releaseId='m8-'+randomUUID();
 await call('assistant_stage_release',{manifest:{schemaVersion:1,releaseId,componentId:'profile',baseVersion:before.currentVersion,targetVersion:prepared.targetVersion,payload:prepared.payload,minClientCode:51,activationMode:'reload',verificationNote:'M8 explicit speech provider selection'}});
 await call('assistant_release_action',{releaseId,action:'activate'});
 if(mode==='stage')return;
 for(let i=0;i<100;i++){const row=(await call('assistant_release_status')).releases.find(x=>x.releaseId===releaseId);if(row.state==='active')return;if(row.state!=='verifying')throw Error('Profile activation: '+row.state);await sleep(100);}
 throw Error('Profile activation timeout');
}
try{
 if(mode==='status'){
  const status=await call('assistant_get_status');console.log(JSON.stringify(status.voice));
  const releases=await call('assistant_release_status');console.log(JSON.stringify(releases.releases.slice(-3).map(x=>({state:x.state,id:x.releaseId,detail:x.detail}))));
 }else if(mode==='pair'){
  const file=root+'/m8-provider.json',args=JSON.parse(await readFile(file,'utf8'));
  const result=await device.request({method:'voice/provider',arguments:args});
  await writeFile(root+'/m8-selection.json',JSON.stringify(result),{mode:0o600});await unlink(file);
  console.log('PROVIDER_PAIRED');
 }else if(mode==='activate'||mode==='stage'){
  const choice=JSON.parse(await readFile(root+'/m8-selection.json','utf8'));await activate(process.argv[3]??choice.engine);console.log(mode==='stage'?'PROVIDER_STAGED':'PROVIDER_ACTIVE');
 }else{
  const key='m8-'+randomUUID();const text=mode==='long'?'你好，这是远程语音服务的实际播放测试。现在正在播放第一句话，同时准备下一句话。我们仍然使用同一个助手和同一个会话。': '你好，这是当前语音提供商的播放测试。';
  const args={operationKey:key,text,language:'zh-CN'};const first=await call('assistant_speak',args);assert.equal((await call('assistant_speak',args)).operationId,first.operationId);
  if(mode==='stop-synthesis'){await sleep(300);await call('assistant_stop_speaking',{operationKey:'stop-'+randomUUID(),operationId:first.operationId});}
  let started=false,done;
  for(let i=0;i<1200;i++){
   const status=(await call('assistant_get_status')).voice;
   if(status.speaking&&!started){started=true;console.log('PLAYBACK_STARTED');if(mode==='stop-playback')await call('assistant_stop_speaking',{operationKey:'stop-'+randomUUID(),operationId:first.operationId});}
   done=await call('assistant_get_operation',{operationKey:key});if(!['pending','running'].includes(done.state))break;await sleep(150);
  }
  assert.equal(done.state,mode.startsWith('stop-')?'cancelled':mode==='unavailable'?'failed':'succeeded',JSON.stringify(done));
  if(mode==='play'||mode==='long')assert.ok(started,'Actual Android playback');
  if(mode.startsWith('stop-')){await sleep(3500);assert.equal((await call('assistant_get_status')).voice.speaking,false);}
  console.log(JSON.stringify({mode,state:done.state,started,duplicateSuppressed:true,detail:done.detail}));
 }
}finally{device.close();}
