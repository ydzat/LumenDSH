#!/usr/bin/env node
import {readFileSync} from 'node:fs';
import {connectDevice} from '../../integrations/device-tools/bridge.mjs';
import {uploadArtifact} from '../../integrations/device-tools/upload-artifact.mjs';
const [command,file,receiptFile]=process.argv.slice(2);
if(!['status','stage','install'].includes(command))throw new Error('Usage: native.mjs status | stage APK RECEIPT | install');
let receipt;
if(command==='stage'){receipt=JSON.parse(readFileSync(receiptFile,'utf8'));await uploadArtifact(file,receipt.releaseId,'native-apk');}
const d=await connectDevice();try{console.log(JSON.stringify(await d.request({method:'call',name:command==='status'?'assistant_native_status':command==='stage'?'assistant_stage_apk':'assistant_install_apk',arguments:command==='stage'?{operationKey:receipt.releaseId,receipt}:{}}),null,2));}finally{d.close();}
