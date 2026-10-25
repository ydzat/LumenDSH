// Real isolated DSH/CLI checks; never reads model credentials or active conversations.
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=path.join(process.env.HOME,'sliver-wolf-companion');
const runtime=path.join(process.env.HOME,'sliver-wolf-dsh-upgrade/0.2.1-rc.3/runtime');
const child=spawn('node',['--expose-internals',path.join(runtime,'node_modules/@deepseek-ai/dsh/lib/bin.js'),
  'web','--host','127.0.0.1','--port','3082','--no-open'],{
  cwd:path.join(root,'probe-workspace'),env:{...process.env,DSH_HOME:path.join(root,'probe-home'),DSH_TELEMETRY_DISABLED:'1'},
  stdio:['ignore','pipe','pipe']});
let log='';const capture=b=>{log=(log+String(b)).slice(-512*1024);};child.stdout.on('data',capture);child.stderr.on('data',capture);
try {
  let token;
  for(let n=0;n<200 && child.exitCode===null;n++) {
    token=log.match(/[?&]token=([^\s]+)/)?.[1];if(token)break;
    await new Promise(r=>setTimeout(r,200));
  }
  assert(token,'Isolated host failed to start');
  const exchange=await fetch('http://127.0.0.1:3082/?token='+token,{redirect:'manual'});
  const cookie=exchange.headers.get('set-cookie')?.split(';')[0];assert(cookie);
  const method='dshMnemon/read';
  const response=await fetch('http://127.0.0.1:3082/api/'+method,{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:'m7-status',method,payload:{args:{endpoint:'status',payload:{}}}})});
  const result=await response.json();
  assert(result.result.ok,JSON.stringify(result.result.error));
  assert(result.result.value.ok,JSON.stringify(result.result.value.error));
  const value=result.result.value.value;
  const report={version:value.dshMnemonVersion,healthy:value.healthy,commandFound:value.commandFound,
    serving:value.memorySystem?.serving,sources:value.memorySystem?.sources?.map(s=>s.sourceTypeId)};
  console.log(JSON.stringify(report));
  assert.equal(report.version,'0.5.34');assert(report.commandFound && report.serving);
  await writeFile(path.join(root,'probe-receipt.json'),JSON.stringify(report,null,2)+'\n',{mode:0o600});
} catch(error) {
  console.error(String(error));
  console.error(log.replace(/([?&]token=)[^\s]+/g,'$1[redacted]').slice(-6000));
  process.exitCode=1;
} finally {
  if(child.exitCode===null){child.kill('SIGTERM');await once(child,'exit');}
}
