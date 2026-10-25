// Run beside the existing device-tools adapter; credentials remain phone-local.
import {readFile} from 'node:fs/promises';
import {homedir} from 'node:os';
import {connectDevice} from '../device-tools/bridge.mjs';
const token=(await readFile(`${homedir()}/lumendsh-voice/token`,'utf8')).trim();
const health=await fetch('http://127.0.0.1:3086/health',{headers:{Authorization:`Bearer ${token}`}});
if(!health.ok||(await health.json()).engine!=='lumendsh.silver_wolf.local')throw Error('Voice runtime unavailable');
const device=await connectDevice();
try {
    const result=await device.request({method:'voice/pair',arguments:{token}});
    if(!result.paired)throw Error('Voice pairing failed');
    console.log('CHARACTER_VOICE_PAIRED');
}finally{device.close();}
