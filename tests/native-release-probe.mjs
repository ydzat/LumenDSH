// Real paired-device rejection checks; requires an existing completed artifact upload.
import {readFileSync} from 'node:fs';
import {connectDevice} from '../integrations/device-tools/bridge.mjs';
const [operationKey,receiptFile]=process.argv.slice(2),receipt=JSON.parse(readFileSync(receiptFile,'utf8'));
const d=await connectDevice(),call=(name,args={})=>d.request({method:'call',name,arguments:args});
try{
 const before=await call('assistant_native_status');
 for(const [label,changes,expected] of [
  ['stale',{baseCode:before.installedCode-1},'Stale installed base'],
  ['hash',{baseCode:before.installedCode,apkSha256:'0'.repeat(64)},'APK hash mismatch'],
  ['version',{baseCode:before.installedCode},'Invalid package or non-increasing version']]){
  let rejected=false;try{await call('assistant_stage_apk',{operationKey,receipt:{...receipt,...changes,releaseId:'reject-'+label+'-'+Date.now()}});}catch(error){if(!error.message.includes(expected))throw error;rejected=true;}
  if(!rejected)throw new Error('Invalid candidate accepted: '+label);
 }
 const after=await call('assistant_native_status');
 if(after.releaseId!==before.releaseId||after.state!==before.state||after.installedCode!==before.installedCode)throw new Error('Rejected candidate changed native release state');
 console.log('NATIVE_STALE_HASH_VERSION_REJECTIONS_PASS');
}finally{d.close();}
