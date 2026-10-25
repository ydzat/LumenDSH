// Explicitly authorized companion deployment; retain the existing DSH version and data.
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.HOME,root=path.join(home,'sliver-wolf-companion');
const profile=path.join(home,'.dsh/profiles/web');
const pairFile=path.join(home,'.local/state/sliver-wolf-backend/connection-3080.json');
const auth=JSON.parse(await readFile(pairFile,'utf8'));
assert.equal(auth.protocol,'0.2.1-rc.3');
const method='session/list';
const state=await (await fetch('http://127.0.0.1:3080/api/'+method,{method:'POST',headers:{Cookie:auth.cookie,'Content-Type':'application/json'},
 body:JSON.stringify({type:'client-request',rpcId:'m7-idle',method,payload:{args:{_request:{}}}})})).json();
assert(state.result.ok && state.result.value.items.every(s=>!s.running),'DSH must be idle before companion activation');
const oldPatch=await readFile(path.join(profile,'cordis.patch.yml'),'utf8');
assert(!oldPatch.includes('# Sliver Wolf companion v1'),'Already activated');
const probe=JSON.parse(await readFile(path.join(root,'probe-receipt.json'),'utf8'));
assert(probe.serving && probe.commandFound && probe.version==='0.5.34','Candidate did not pass');
const backup=path.join(root,'profile-backup-'+Date.now());await mkdir(backup,{mode:0o700});
for(const name of ['package.json','cordis.patch.yml'])await copyFile(path.join(profile,name),path.join(backup,name));
execFileSync('tmux',['has-session','-t','=dsh']);
execFileSync('tmux',['send-keys','-t','dsh','C-c']);
for(let n=0;;n++){
  try{execFileSync('tmux',['has-session','-t','=dsh'],{stdio:'ignore'});}catch{break;}
  assert(n<100,'DSH shutdown did not finish');await new Promise(r=>setTimeout(r,200));
}
try {
  execFileSync('dsh',['plugin','--profile','web','add','dsh-mnemon@0.5.34','--ignore-scripts','--save-exact'],{stdio:'inherit'});
  const patch=await readFile(path.join(root,'prepared-v2/companion.patch.yml'),'utf8');
  const persona=await readFile(path.join(home,'sliver-wolf-build/source/integrations/companion/persona.md'),'utf8');
  await writeFile(path.join(profile,'cordis.patch.yml'),oldPatch+'\n# Sliver Wolf companion v1\n'+patch,{mode:0o600});
  // Standard presets shadow the global persona. Existing sessions already load
  // workspace instructions, so add an attributed section without replacing files.
  const instructions=path.join(home,'agent-workspace/AGENTS.local.md');
  let previous='';try{previous=await readFile(instructions,'utf8');}catch(error){if(error.code!=='ENOENT')throw error;}
  if(!previous.includes('<!-- sliver-wolf-persona-v1 -->'))await writeFile(instructions,
    previous+'\n<!-- sliver-wolf-persona-v1 -->\n'+persona+'\n<!-- /sliver-wolf-persona-v1 -->\n',{mode:0o600});
  execFileSync('agent-start',[],{stdio:'inherit'});
  await writeFile(path.join(root,'activation.json'),JSON.stringify({version:'0.5.34',cli:'0.2.12',
    originalSessionCount:state.result.value.items.length,backup,status:'started-awaiting-verification'},null,2)+'\n',{mode:0o600});
}catch(error){
  for(const name of ['package.json','cordis.patch.yml'])await copyFile(path.join(backup,name),path.join(profile,name));
  execFileSync('agent-start',[],{stdio:'inherit'});throw error;
}
