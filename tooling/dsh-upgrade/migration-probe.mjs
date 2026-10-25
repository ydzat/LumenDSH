// Exercises upstream migration against a phone-private copy, without model credentials.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { cp, mkdir, readdir, readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = path.resolve(process.argv[2] ?? '');
assert(root.includes('/sliver-wolf-dsh-upgrade/'));
const source = path.join(process.env.HOME, '.dsh/sessions');
const home = path.join(root, 'migration-home');
const require = createRequire(path.join(root, 'runtime/package.json'));
const { WebSocket } = require('ws');
async function digest(directory) {
  const hash = createHash('sha256');
  async function walk(dir) {
    for (const entry of (await readdir(dir, { withFileTypes: true })).sort((a,b) => a.name.localeCompare(b.name))) {
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(file);
      else if (entry.isFile()) { hash.update(path.relative(directory, file)); hash.update(await readFile(file)); }
      else throw new Error('Unexpected session symlink');
    }
  }
  await walk(directory); return hash.digest('hex');
}
const before = await digest(source);
if (!process.argv.includes('--reopen')) {
  const response = await fetch('http://127.0.0.1:3080/api/session.list', { method:'POST', headers:{'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:'migration-preflight',method:'session.list',payload:{}}) });
  const old = await response.json();
  assert(old.result.ok && old.result.value.items.every(item => !item.running), 'Existing tasks must be idle');
  await mkdir(home, { mode:0o700 }); // Refuse overwriting a previous migration experiment.
  await cp(source, path.join(home,'sessions'), {recursive:true,errorOnExist:true,force:false});
  assert.equal(await digest(source), before, 'Active sessions changed during snapshot');
}
const environment = {...process.env, DSH_HOME:home, DSH_TELEMETRY_DISABLED:'1'};
delete environment.DEEPSEEK_API_KEY;
const child = spawn('node',['--expose-internals',path.join(root,'runtime/node_modules/@deepseek-ai/dsh/lib/bin.js'),
  'web','--host','127.0.0.1','--port','3082','--no-open'],{cwd:path.join(root,'workspace'),env:environment,stdio:['ignore','pipe','pipe']});
let output='';
child.stdout.on('data',x=>output+=x); child.stderr.on('data',x=>output+=x);
const sockets=[];
try {
  let token;
  for(let i=0;i<150;i++) {
    token=output.match(/[?&]token=([^\s]+)/)?.[1];
    if(token)break;
    if(child.exitCode!==null)throw new Error('Candidate startup failed; inspect sanitized diagnostics');
    await new Promise(r=>setTimeout(r,200));
  }
  assert(token,'Candidate ready timeout');
  const response=await fetch(`http://127.0.0.1:3082/?token=${token}`,{redirect:'manual'});
  const cookie=response.headers.get('set-cookie')?.split(';')[0]; assert(cookie);
  async function list() {
    const response=await fetch('http://127.0.0.1:3082/api/session/list',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},
      body:JSON.stringify({type:'client-request',rpcId:'migration-list',method:'session/list',payload:{args:{_request:{}}}})});
    const result=await response.json();assert(result.result.ok);return result.result.value.items;
  }
  const items=await list();
  console.log(JSON.stringify({check:'migration-list',count:items.length,formats:items.map(x=>x.formatStatus)}));
  for(const [index,item] of items.entries()) {
    if(process.argv.includes('--materialize')) {
      const response=await fetch('http://127.0.0.1:3082/api/session/rename',{method:'POST',headers:{Cookie:cookie,'Content-Type':'application/json'},
        body:JSON.stringify({type:'client-request',rpcId:`migration-resume-${index}`,method:'session/rename',payload:{args:{request:{sessionId:item.sessionId,title:item.projections?.values?.title ?? 'Migrated session'}}}})});
      const result=await response.json();
      assert(result.result.ok, `Resume refused: ${result.result.error?.code}: ${String(result.result.error?.message).slice(0,600)}`);
      console.log(JSON.stringify({check:'migration-resume',index,accepted:true}));
    }
    const socket=new WebSocket('ws://127.0.0.1:3082/api/remote.mux',{headers:{Cookie:cookie}});sockets.push(socket);
    const snapshot=new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(new Error('Follow snapshot timeout')),15000);
      socket.on('error',reject);
      socket.on('message',bytes=>{
        const frame=JSON.parse(String(bytes));
        if(frame.type==='error'){clearTimeout(timeout);reject(new Error(`Follow refused: ${JSON.stringify(frame.error)}`));}
        if(frame.type==='item'&&frame.value.type==='snapshot'){clearTimeout(timeout);resolve(frame.value);}
      });
    });
    await once(socket,'open');
    socket.send(JSON.stringify({type:'open',streamId:'migration',endpoint:'session/follow',payload:{args:{request:{address:{kind:'session',sessionId:item.sessionId},maxMessages:50,assistantStream:true}}}}));
    socket.send(JSON.stringify({type:'end',streamId:'migration'}));
    const value=await snapshot; assert.equal(value.header.id,item.sessionId);
    console.log(JSON.stringify({check:'migration-follow',index,identityPreserved:true,records:value.records.length,cursor:value.cursor,hasMore:value.hasMore}));
    socket.close();
  }
  console.log(JSON.stringify({check:'migration-after-follow',formats:(await list()).map(x=>x.formatStatus)}));
} finally {
  for(const socket of sockets)socket.terminate();
  if(child.exitCode===null){const exit=once(child,'exit');child.kill('SIGTERM');const force=setTimeout(()=>child.kill('SIGKILL'),5000);await exit;clearTimeout(force);}
  assert.equal(await digest(source),before,'Active session bytes changed');
  console.log(JSON.stringify({check:'original-sessions-unchanged',passed:true}));
  const errors=output.split('\n').filter(x=>/EACCES|ERR_|failed|error/i.test(x));
  // Only expose error categories; candidate logs may contain migrated conversation data.
  console.log(JSON.stringify({check:'candidate-diagnostics',errorLines:errors.length}));
}
