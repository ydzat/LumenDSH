// Single-version launcher. DSH owns model credentials; only its browser cookie is paired.
import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { once } from 'node:events';
import path from 'node:path';
import assert from 'node:assert/strict';

const [runtime, home, workspace, portText] = process.argv.slice(2);
const port = Number(portText ?? 3080);
assert(runtime && home && workspace && [3080,3082].includes(port), 'Usage: serve.mjs RUNTIME HOME WORKSPACE PORT');
const require = createRequire(path.resolve(runtime,'package.json'));
assert.equal(require('@deepseek-ai/dsh/package.json').version,'0.2.1-rc.3');
const {WebSocket}=require('ws');
const privateDir=path.join(process.env.HOME,'.local/state/sliver-wolf-backend');
await mkdir(privateDir,{recursive:true,mode:0o700});
const child=spawn('node',['--expose-internals',path.resolve(runtime,'node_modules/@deepseek-ai/dsh/lib/bin.js'),
  'web','--host','127.0.0.1','--port',String(port),'--no-open'],{
  cwd:workspace,env:{...process.env,DSH_HOME:home,DSH_TELEMETRY_DISABLED:'1'},stdio:['ignore','pipe','pipe']});
let boot='',cookie='',paired=false,stopping=false,exited=false;
child.once('exit',()=>{exited=true;});
// Raw output stays in memory. Do not print launch URLs or exception payloads.
const capture=bytes=>{boot=(boot+String(bytes)).slice(-512*1024);};
child.stdout.on('data',capture);child.stderr.on('data',capture);
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>{stopping=true;child.kill('SIGTERM');});
async function pair() {
  const token=(await readFile(path.join(process.env.HOME,'sliver-wolf-build/private/device-token'),'utf8')).trim();
  const socket=new WebSocket('ws://127.0.0.1:3081/device-tools',{headers:{Authorization:`Bearer ${token}`},handshakeTimeout:3000});
  try {
    await once(socket,'open');
    const reply=new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(new Error('Pair timeout')),5000);
      socket.once('message',bytes=>{clearTimeout(timer);const result=JSON.parse(String(bytes));result.result?.paired?resolve():reject(new Error('Pair refused'));});
      socket.once('error',reject);
    });
    socket.send(JSON.stringify({id:'backend-pair',method:'backend/pair',arguments:{protocol:'0.2.1-rc.3',port,cookie}}));
    await reply;
  } finally {socket.terminate();}
}
let ready=false;
for(let attempt=0;attempt<300&&!exited&&!stopping;attempt++) {
  const token=boot.match(/[?&]token=([^\s]+)/)?.[1];
  if(token) {
    const exchange=await fetch(`http://127.0.0.1:${port}/?token=${token}`,{redirect:'manual'});
    cookie=exchange.headers.get('set-cookie')?.split(';')[0]??'';
    if(cookie){ready=true;break;}
  }
  await new Promise(r=>setTimeout(r,200));
}
if(!ready){child.kill('SIGTERM');throw new Error('DSH readiness failed; no active deployment selected');}
// The diagnostic cookie is phone-private and is never a model-provider key.
await writeFile(path.join(privateDir,`connection-${port}.json`),JSON.stringify({port,cookie,protocol:'0.2.1-rc.3'}),{mode:0o600});
console.log(`DSH 0.2.1-rc.3 ready on loopback:${port}`);
while(!exited&&!stopping) {
  if(!paired)try{await pair();paired=true;console.log('Android backend pairing complete');}catch{ /* App may not yet be installed or open. */ }
  await new Promise(r=>setTimeout(r,2000));
}
if(!exited)await once(child,'exit');
process.exitCode=child.exitCode??1;
