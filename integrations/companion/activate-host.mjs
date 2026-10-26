// Authorized backend integration: immutable local plugin, idle gate and recovery.
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME,root=path.join(home,'sliver-wolf-companion'),source=path.dirname(fileURLToPath(import.meta.url));
const files=['package.json','cordis.patch.yml','host.mjs','policy.mjs','model-config.mjs','documents.mjs','document-index-worker.mjs','situation.mjs','self-notes.mjs','experience.mjs','candidates.mjs','growth.mjs','self-doc.mjs','extend-self.mjs','upstream/night-plan.mjs','upstream/NOTICE.md','upstream/LICENSE-dsh-bili-whale','upstream/LICENSE-dsh-mnemon'];
const hash=createHash('sha256');for(const file of files){hash.update(file);hash.update(await readFile(path.join(source,file)));}
const revision=hash.digest('hex'),candidate=path.join(root,'host-'+revision);
for(const file of files){await mkdir(path.dirname(path.join(candidate,file)),{recursive:true,mode:0o700});await copyFile(path.join(source,file),path.join(candidate,file));}
const auth=JSON.parse(await readFile(path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json'),'utf8'));
async function list(){const method='session/list';const r=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
  body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args:{_request:{}}}}),signal:AbortSignal.timeout(4000)});const v=await r.json();assert(v.result.ok);return v.result.value.items;}
const before=await list();assert(before.every(x=>!x.running),'Wait for existing tasks before activation');
const workspaces=new Map();
for(const session of before) {
  const method='workspace/create';
  assert(session.cwd,'Session workspace is unavailable');
  const response=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args:{request:{path:session.cwd}}}})});
  const result=await response.json();assert(result.result.ok);workspaces.set(session.sessionId,result.result.value.workspace.workspaceId);
}
async function memory(sessionId,endpoint,payload={}) {
  const method='dshMnemon/read';
  const response=await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
    body:JSON.stringify({type:'client-request',rpcId:crypto.randomUUID(),method,payload:{args:{endpoint,payload:{sessionId,workspaceId:workspaces.get(sessionId),...payload}}}}),signal:AbortSignal.timeout(15000)});
  const result=await response.json();assert(result.result.ok);assert(result.result.value.ok,JSON.stringify(result.result.value.error));return result.result.value.value;
}
// The adapter must retain every existing scope and authoritative document. Compare
// metadata in memory; never print or export personal document contents.
const catalogs=[];
for(const session of before) {
  const catalog=await memory(session.sessionId,'source-management-catalog');
  const source=catalog.sources.find(x=>x.sourceTypeId==='documents');
  assert.equal(source?.sourceInstanceKey,'source:include:mnemon-source-documents','Unexpected installed Document identity');
  const snapshot=await memory(session.sessionId,'documents');
  catalogs.push({sessionId:session.sessionId,directory:snapshot.directory,documents:snapshot.documents.map(x=>({id:x.id,contentHash:x.contentHash}))});
}
const profile=path.join(home,'.dsh/profiles/web'),backup=path.join(root,'host-backup-'+Date.now());await mkdir(backup,{mode:0o700});
const retained=['package.json','cordis.patch.yml','pnpm-lock.yaml'];
for(const file of retained)await copyFile(path.join(profile,file),path.join(backup,file));
execFileSync('tmux',['send-keys','-t','dsh','C-c']);
for(let i=0;;i++){try{execFileSync('tmux',['has-session','-t','=dsh'],{stdio:'ignore'});}catch{break;}assert(i<100,'Backend shutdown timeout');await new Promise(r=>setTimeout(r,200));}
try {
  execFileSync('dsh',['plugin','--profile','web','add','link:'+candidate,'--ignore-scripts'],{stdio:'inherit'});
  const patchFile=path.join(profile,'cordis.patch.yml');
  let patch=await readFile(patchFile,'utf8');
  // The measured cold multi-chunk index exceeds the upstream 10-second desktop
  // default. Reuse the native supported 120-second limit; turn cancellation still
  // applies its independent user-configured activity deadline.
  patch=patch.replace('    timeoutMs: 10000\n','    timeoutMs: 120000\n');
  assert(patch.includes('    timeoutMs: 120000\n'),'Unexpected Mnemon source deadline configuration');
  await writeFile(patchFile,patch.replace('- id: mnemon-source-documents\n  name: dsh-mnemon-source-documents',
    '- id: mnemon-source-documents\n  disabled: true\n  name: dsh-mnemon-source-documents'),{mode:0o600});
  execFileSync('agent-start',[],{stdio:'inherit'});
  let after;
  for(let i=0;i<40;i++){try{after=await list();break;}catch{}await new Promise(r=>setTimeout(r,1000));}
  assert(after,'Backend readiness timeout');assert(before.every(x=>after.some(y=>y.sessionId===x.sessionId)),'Session preservation failed');
  const status=await fetch('http://127.0.0.1:3080/sliver-wolf/companion/status',{headers:{Cookie:auth.cookie}});assert.equal(status.status,200);
  for(const prior of catalogs) {
    const current=await memory(prior.sessionId,'documents');
    assert.equal(current.directory,prior.directory,'Document storage scope changed');
    for(const doc of prior.documents)assert(current.documents.some(x=>x.id===doc.id&&x.contentHash===doc.contentHash),'Existing Document changed or disappeared');
  }
  await writeFile(path.join(root,'host-activation.json'),JSON.stringify({revision,backup,sessionsPreserved:before.length,status:'active-policy-disabled-until-configured'},null,2)+'\n',{mode:0o600});
  console.log('COMPANION_HOST_ACTIVATED_SESSIONS_PRESERVED',before.length);
  console.log('DOCUMENT_STORAGE_AND_CONTENT_PRESERVED',catalogs.length);
} catch(error) {
  try{execFileSync('tmux',['send-keys','-t','dsh','C-c']);}catch{}
  for(let i=0;i<100;i++){try{execFileSync('tmux',['has-session','-t','=dsh'],{stdio:'ignore'});}catch{break;}await new Promise(r=>setTimeout(r,200));}
  for(const file of retained)await copyFile(path.join(backup,file),path.join(profile,file));
  execFileSync('agent-start',[],{stdio:'inherit'});throw error;
}
