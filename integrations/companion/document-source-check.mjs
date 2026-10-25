// Real native controller + native Mnemon/Qwen integration, without a model reply.
import {mkdtemp} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createSemanticDocumentsSource} from './documents.mjs';
const root=await mkdtemp(path.join(process.env.HOME,'sliver-wolf-companion/document-source-check-'));
const source=createSemanticDocumentsSource().create({sourceInstanceKey:'source:include:mnemon-source-documents',configuration:{dataDir:root}});
const scope={storage:'workspace',workspaceId:root,sessionId:'session-controlled-source'};
async function create(title,content) {
  return (await source.manage({scope,mode:'mutate',operation:'mutate',confirmed:true,input:{action:'create',title,content}})).value.document;
}
const long=process.argv.includes('--long');
const content=(long?'甲乙丙丁戊己庚辛壬癸'.repeat(320):'Routine maintenance log. '.repeat(65))+'\n受控角色阿澈无法消化乳糖，饮用咖啡时须使用燕麦饮代替牛乳。';
if(long) {
  const response=await fetch('http://127.0.0.1:3083/tokenize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({content}),signal:AbortSignal.timeout(30000)});
  assert(response.ok);const tokens=(await response.json()).tokens;assert(tokens.length>2048,'Long fixture must exceed the actual model context');
  console.log('REAL_DOCUMENT_INPUT_TOKENS',tokens.length);
}
const started=Date.now();
const target=await create('Controlled source A',content);
const other=await create('Controlled source B','周末去山里徒步，避免去商场购物。');
const facts=await source.facts({scope,scenario:'turn'});
const projection=await source.project({scope,expectedRevision:facts.revision});
async function query(grant,maxCharacters=300) {
  return source.query({view:{id:'controlled-source-view',scope},grant,route:{id:'search',maxResults:1,maxCharacters},
    input:{query:'阿澈点拿铁时应怎样调整配料？',limit:10},signal:AbortSignal.timeout(180000)});
}
const full=await query(projection.readGrant);
assert.equal(full.items.length,1);assert.equal(full.items[0].id,target.id);
assert(full.items[0].text.includes('燕麦饮'),'Relevant tail passage was replaced by the document head');
assert(full.items[0].text.length<=300);assert.equal(full.items[0].provenance.documentId,target.id);
const narrow={...projection.readGrant,value:{...projection.readGrant.value,documentIds:[other.id]}};
const restricted=await query(narrow);assert(restricted.items.every(x=>x.id===other.id),'Pinned grant leaked a disallowed Document');
const empty=await query({...narrow,value:{...narrow.value,documentIds:[]}});assert.equal(empty.items.length,0);
const tight=await query(projection.readGrant,12);assert(tight.items.reduce((n,x)=>n+x.text.length,0)<=12);
console.log('REAL_NATIVE_DOCUMENT_TAIL_PASSAGE_RANK_LIMIT_GRANT_BUDGET_PASS');
if(long)console.log('REAL_CHUNKED_LONG_DOCUMENT_ELAPSED_MS',Date.now()-started);
