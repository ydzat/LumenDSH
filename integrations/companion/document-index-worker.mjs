// Derived Document index. Retrieval, embeddings and persistence belong to Mnemon.
// Invoked under native flock by documents.mjs; no conversation text is logged.
import {readFileSync,mkdirSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
import {atomicJson,loadJson} from './policy.mjs';

let stage='input';
process.on('uncaughtException',error=>{
  const native=String(error.stderr??'');
  console.error(JSON.stringify({stage,code:error.code??null,name:error.name,
    hints:['permission denied','not found','no record written','unexpected e_type','timeout','too large','no such file'].filter(x=>native.toLowerCase().includes(x)),
    shapeError:error instanceof TypeError?error.message:undefined}));
  process.exit(1);
});
const input=JSON.parse(readFileSync(0,'utf8'));
assert(path.isAbsolute(input.directory));
const directory=path.join(input.directory,'.semantic-index');mkdirSync(directory,{recursive:true,mode:0o700});
const manifestFile=path.join(directory,'index.json');
const manifest=loadJson(manifestFile,{schemaVersion:1,model:'sw-qwen3-embedding-0.6b-q8',documents:{}});
assert.equal(manifest.schemaVersion,1);assert.equal(manifest.model,'sw-qwen3-embedding-0.6b-q8');
const environment={...process.env,MNEMON_DATA_DIR:path.join(directory,'data'),MNEMON_STORE:'default'};
const wrapper=path.join(process.env.HOME,'sliver-wolf-companion/bin/mnemon-termux');
function run(args) {
  stage='native-'+args[0];
  return JSON.parse(execFileSync('bash',[wrapper,...args],{env:environment,encoding:'utf8',timeout:120000,maxBuffer:16*1024*1024,stdio:['ignore','pipe','pipe']}));
}
const digest=text=>createHash('sha256').update(text).digest('hex');
// Byte-bounded chunks fit the phone context even for non-Latin text. Original
// documents remain authoritative; chunks are derived retrieval evidence only.
function chunks(text) {
  const result=[];let chunk='',bytes=0;
  for(const character of text) {
    // Keep paragraph boundaries when a useful chunk is already available. A tail
    // fact must not disappear behind an unrelated prefix in the evidence budget.
    if(character==='\n'&&bytes>=200){result.push(chunk);chunk='';bytes=0;continue;}
    const size=Buffer.byteLength(character);
    if(bytes+size>1000){result.push(chunk);chunk='';bytes=0;}
    chunk+=character;bytes+=size;
  }
  if(chunk)result.push(chunk);return result;
}
if(manifest.chunkingVersion!==2) {
  for(const entry of Object.values(manifest.documents))for(const chunk of entry.chunks)run(['forget',chunk.id]);
  manifest.documents={};manifest.chunkingVersion=2;atomicJson(manifestFile,manifest);
}
const current=new Map(input.documents.map(doc=>[doc.id,doc]));
// The complete native catalog, not one View's subset, controls stale-index removal.
for(const [id,entry] of Object.entries(manifest.documents)) {
  const live=input.catalog.find(doc=>doc.id===id && doc.healthy);
  if(!live || live.contentHash!==entry.contentHash) {
    for(const chunk of entry.chunks)run(['forget',chunk.id]);
    delete manifest.documents[id];atomicJson(manifestFile,manifest);
  }
}
for(const doc of input.documents) {
  stage='document-shape';
  if(manifest.documents[doc.id]?.contentHash===doc.contentHash && manifest.documents[doc.id].complete===true)continue;
  const entry=manifest.documents[doc.id]??{contentHash:doc.contentHash,revision:doc.revision,chunks:[],complete:false};
  manifest.documents[doc.id]=entry;atomicJson(manifestFile,manifest);
  for(const [offset,content] of chunks(doc.content).entries()) {
    if(entry.chunks[offset]?.offset===offset)continue;
    const key=digest(doc.id+'\0'+doc.contentHash+'\0'+offset+'\0'+content);
    const marker='SWDOC:'+key;
    // Exact-key recovery is bookkeeping, never a fallback for user retrieval.
    const prior=run(['recall',marker,'--basic','--limit','100']);
    const rows=Array.isArray(prior)?prior:prior?.results??[];
    let found=rows.map(row=>row.insight??row).find(row=>row.content?.includes(marker));
    if(!found) {
      found=run(['remember',doc.title+'\n'+content+'\n'+marker,'--cat','context','--source','external','--no-diff']);
      assert.equal(found.embedded,true,'Document chunk did not receive a real vector');
    }
    assert(typeof found.id==='string');entry.chunks.push({id:found.id,offset});
    atomicJson(manifestFile,manifest);
  }
  entry.complete=true;
  atomicJson(manifestFile,manifest);
}
const byChunk=new Map();
for(const [id,entry] of Object.entries(manifest.documents))if(current.has(id))
  for(const chunk of entry.chunks)byChunk.set(chunk.id,{id,offset:chunk.offset});
if(byChunk.size===0){console.log(JSON.stringify({results:[],indexedDocuments:0,indexedChunks:0}));process.exit(0);}
// Keep Mnemon's vector/keyword/graph hybrid ranking and explicit semantic failures.
const recall=run(['recall',input.query,'--verbose','--limit',String(Math.max(20,byChunk.size))]);
const seen=new Set(),results=[];
for(const row of recall.results) {
  const chunk=byChunk.get(row.insight?.id??row.id),id=chunk?.id;
  if(!id||seen.has(id))continue;seen.add(id);
  results.push({id,score:row.score,chunkId:row.insight?.id??row.id,passage:chunks(current.get(id).content)[chunk.offset]});
  if(results.length>=input.limit)break;
}
console.log(JSON.stringify({results,indexedDocuments:current.size,indexedChunks:byChunk.size}));
