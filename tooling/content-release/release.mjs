#!/usr/bin/env node
import {readFileSync} from 'node:fs';
import {connectDevice} from '../../integrations/device-tools/bridge.mjs';
import {shaBuffer,writeJson} from './receipts.mjs';
const [command,id,payloadFile,receiptFile,component="presentation"]=process.argv.slice(2);
if(!['status','stage','activate','rollback','discard'].includes(command))throw new Error('Usage: release.mjs status | stage ID PAYLOAD_JSON RECEIPT_JSON | activate/rollback/discard ID');
if(command!=='status'&&!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(id??''))throw new Error('Invalid release ID');
if(command==='stage'&&(!payloadFile||!receiptFile))throw new Error('Stage requires payload and receipt paths');
const device=await connectDevice();
const call=(name,args={})=>device.request({method:'call',name,arguments:args});
try {
  if(command==='status')console.log(JSON.stringify(await call('assistant_release_status',id?{componentId:id}:{}),null,2));
  else if(command==='stage'){
    const payload=readFileSync(payloadFile,'utf8');
    if(Buffer.byteLength(payload)>8192)throw new Error('Content payload exceeds 8192 bytes');
    JSON.parse(payload);
    const current=await call('assistant_release_status',{componentId:component});
    const manifest={schemaVersion:1,releaseId:id,componentId:component,baseVersion:current.currentVersion,
      targetVersion:shaBuffer(Buffer.from(payload)),payload,minClientCode:component==='profile'?36:35,activationMode:'reload',
      verificationNote:'Local CLI: JSON parsed and byte limit checked; Android validates the complete component schema.'};
    // Retain the exact candidate before sending; uncertain outcomes can be inspected by ID.
    writeJson(receiptFile,manifest);
    console.log(JSON.stringify(await call('assistant_stage_release',{manifest}),null,2));
  }else console.log(JSON.stringify(await call('assistant_release_action',{releaseId:id,action:command}),null,2));
} finally {device.close();}
