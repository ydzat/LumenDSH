// Real installed-app checks; audible controlled text only, no owner conversation.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {connectDevice} from '../device-tools/bridge.mjs';
const device=await connectDevice();
const call=(name,arguments_={})=>device.request({method:'call',name,arguments:arguments_});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const mode=process.argv[2]??'play';
async function settle(key){
    for(let n=0;n<600;n++){
        const op=await call('assistant_get_operation',{operationKey:key});
        if(!['pending','running'].includes(op.state))return op;
        await sleep(200);
    }
    throw Error('Speech did not settle');
}
try {
    if(mode==='activate'){
        const before=await call('assistant_release_status',{componentId:'profile'});
        const current=JSON.parse(before.currentPayload);
        const appearance=current.components.appearance.version;
        const prepared=await call('assistant_prepare_profile',{displayName:current.displayName,
            appearanceVersion:appearance==='builtin-v1'?'':appearance,
            voice:{schemaVersion:1,kind:'voice-profile',engine:'lumendsh.silver_wolf.local',language:'zh-CN',rate:1,pitch:1}});
        const releaseId='character-voice-'+Date.now();
        await call('assistant_stage_release',{manifest:{schemaVersion:1,releaseId,componentId:'profile',
            baseVersion:before.currentVersion,targetVersion:prepared.targetVersion,payload:prepared.payload,
            minClientCode:48,activationMode:'reload',verificationNote:'User-approved Silver Wolf voice integration'}});
        await call('assistant_release_action',{releaseId,action:'activate'});
        for(let i=0;i<100;i++){
            const record=(await call('assistant_release_status')).releases.find(x=>x.releaseId===releaseId);
            if(record.state==='active')break;
            if(record.state!=='verifying')throw Error('Voice activation failed: '+record.state);
            await sleep(100);
        }
        assert.equal((await call('assistant_get_status')).voice.engine,'lumendsh.silver_wolf.local');
        console.log('CHARACTER_PROFILE_ACTIVE');
    } else {
        assert.equal((await call('assistant_get_status')).voice.engine,'lumendsh.silver_wolf.local');
        const key='character-check-'+randomUUID();
        const args={operationKey:key,text:['stop-playback','demo'].includes(mode)?'你好，我是银狼。现在用我的声音为你朗读。今天想先完成什么任务？':'你好，我是银狼。这是手机应用中的语音。',language:'zh-CN'};
        const first=await call('assistant_speak',args);
        const duplicate=await call('assistant_speak',args);
        assert.equal(duplicate.operationId,first.operationId);
        if(mode==='stop-synthesis'){
            await sleep(600);
            await call('assistant_stop_speaking',{operationKey:'character-stop-'+randomUUID(),operationId:first.operationId});
        }else if(['stop-playback','demo'].includes(mode)){
            let started=false;
            for(let n=0;n<600;n++){
                if((await call('assistant_get_status')).voice.speaking){started=true;console.log('PLAYBACK_STARTED');break;}
                const op=await call('assistant_get_operation',{operationKey:key});
                if(!['pending','running'].includes(op.state))throw Error('Speech ended before playback: '+JSON.stringify(op));
                await sleep(100);
            }
            assert.ok(started);
            if(mode==='stop-playback')await call('assistant_stop_speaking',{operationKey:'character-stop-'+randomUUID(),operationId:first.operationId});
        }
        const result=await settle(key);
        assert.equal(result.state,mode.startsWith('stop-')?'cancelled':mode==='unavailable'?'failed':'succeeded',JSON.stringify(result));
        assert.equal((await call('assistant_get_status')).voice.speaking,false);
        console.log(JSON.stringify({mode,state:result.state,duplicateSuppressed:true,detail:result.detail}));
    }
}finally{device.close();}
