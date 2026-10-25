import {createRequire} from 'node:module';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {mkdir} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';

// Resolve already-approved installed plugins. No second copy of their runtime or
// storage controller is bundled into the client or this integration.
const require=createRequire(path.join(process.env.DSH_HOME??path.join(os.homedir(),'.dsh'),'profiles/web/package.json'));
const sdk=await import(pathToFileURL(require.resolve('dsh-mnemon/extension-sdk')));
const dependency=createRequire(require.resolve('dsh-mnemon'));
const upstream=await import(pathToFileURL(dependency.resolve('dsh-mnemon-source-documents')));
export const name='sliver-wolf-semantic-documents';
export const inject=['mnemonMemory'];

function worker(payload,signal) {
  return new Promise((resolve,reject)=>{
    const file=path.join(path.dirname(fileURLToPath(import.meta.url)),'document-index-worker.mjs');
    // The Termux launcher may run Node through linker64, which then becomes
    // process.execPath. Invoke the installed Node ELF, never the dynamic linker.
    if(!process.env.PREFIX)return reject(new Error('Termux PREFIX is required for the native index worker'));
    const node=path.join(process.env.PREFIX,'bin/node');
    const child=spawn('flock',['--no-fork','--wait','120',path.join(payload.directory,'.semantic-index.lock'),node,file],
      {stdio:['pipe','pipe','pipe'],signal});
    let output='',diagnostic='',failed=false;
    child.stdout.on('data',chunk=>{output+=chunk;if(output.length>4*1024*1024){failed=true;child.kill();reject(new Error('Document index response too large'));}});
    // Native CLI stderr can include a private query; never relay it into UI/logs.
    child.stderr.on('data',chunk=>{diagnostic=(diagnostic+String(chunk)).slice(-2048);});child.on('error',reject);
    child.on('exit',code=>{if(failed)return;if(code!==0){
      let detail='';try{const info=JSON.parse(diagnostic);detail=' ['+info.stage+' / '+(info.code??info.name)+']';}catch{}
      return reject(new Error('Local semantic Document indexing unavailable (exit '+code+')'+detail+'; no lexical fallback used'));}
      try{resolve(JSON.parse(output));}catch{reject(new Error('Invalid native Document index response'));}});
    child.stdin.on('error',reject);child.stdin.end(JSON.stringify(payload));
  });
}

export function createSemanticDocumentsSource(config={}) {
  const native=upstream.createDocumentsMemorySource(config);
  const source=sdk.defineMemorySource({...native,create(context){
    const original=native.create(context);
    async function search(scope,input,allowedIds,signal) {
      const read=(operation,input)=>original.manage({mode:'read',operation,input,scope,signal});
      const snapshot=(await read('snapshot',null)).value;
      const allowed=allowedIds===undefined?undefined:new Set(allowedIds);
      const records=snapshot.documents.filter(doc=>doc.healthy&&(input.includeArchived===true||doc.status==='active')&&(!allowed||allowed.has(doc.id)));
      if(input.query!==undefined && typeof input.query!=='string')throw new Error('Document query must be text');
      const query=typeof input.query==='string'?input.query.trim():'';
      if(!query) {
        // Empty input is a catalog listing, not a semantic search request.
        const listed=await original.manage({mode:'read',operation:'search',input,scope,signal});
        return {...listed.value,results:listed.value.results.filter(doc=>!allowed||allowed.has(doc.id))};
      }
      if(query.length>2000)throw new Error('Document query exceeds native 2000-character bound');
      const documents=[];for(const record of records)documents.push((await read('document',{id:record.id})).value);
      await mkdir(snapshot.directory,{recursive:true,mode:0o700});
      const ranked=await worker({directory:snapshot.directory,catalog:snapshot.documents,documents,query,
        limit:Math.min(50,Math.max(1,Math.trunc(input.limit??20)))},signal);
      const results=[];
      for(const hit of ranked.results) {
        const document=(await read('document',{id:hit.id})).value;
        const indexed=documents.find(item=>item.id===hit.id);
        if(document.contentHash!==indexed?.contentHash)throw new Error('Document changed during semantic search; retry against the current revision');
        results.push({...document,score:hit.score,excerpt:hit.passage,passage:hit.passage});
      }
      return {query,includeArchived:input.includeArchived===true,total:results.length,generatedAt:new Date().toISOString(),results,retrieval:'mnemon-native-semantic'};
    }
    return {...original,
      async query(request) {
        const input=request.input??{},grant=request.grant.value;
        if(input.query===undefined || typeof input.query==='string' && !input.query.trim())return original.query(request);
        const allowed=[...(grant.documentIds??[]),...(input.includeArchived===true?grant.archivedDocumentIds??[]:[])];
        const limit=Math.min(request.route.maxResults??20,Number.isInteger(input.limit)?Math.max(1,Math.min(20,input.limit)):10);
        const result=await search(request.view.scope,{...input,limit},allowed,request.signal);
        // Reuse native provenance and grant admission per ranked document. Applying
        // its empty-query catalog order first would spend the text budget on the
        // wrong document, and the relevant semantic passage may be far below its head.
        const nativeEvidence=ids=>original.query({...request,input:{...input,query:'',limit:1},grant:{...request.grant,value:{...grant,
          documentIds:ids.filter(id=>(grant.documentIds??[]).includes(id)),
          archivedDocumentIds:ids.filter(id=>(grant.archivedDocumentIds??[]).includes(id))}}});
        const evidence=await nativeEvidence([]),items=[];
        let remaining=request.route.maxCharacters??16000,truncated=false;
        for(const document of result.results) {
          if(remaining<=0){truncated=true;break;}
          const native=await nativeEvidence([document.id]);
          const item=native.items.find(x=>x.id===document.id);
          if(!item)throw new Error('Native Document grant changed during semantic retrieval');
          const text=document.passage.slice(0,Math.min(2600,remaining));remaining-=text.length;
          truncated ||= text!==document.content;items.push({...item,text,score:document.score});
        }
        return {...evidence,truncated,metadata:{...evidence.metadata,query:result.query,total:result.total,retrieval:'mnemon-native-semantic'},items};
      },
      async manage(request) {
        if(request.mode!=='read'||request.operation!=='search')return original.manage(request);
        const result=await search(request.scope,request.input??{},undefined,request.signal);
        const snapshot=await original.manage({...request,operation:'snapshot',input:null});
        return {revision:snapshot.revision,value:result};
      },
    };
  }});
  return source;
}
export function apply(ctx,config={}) {
  const source=createSemanticDocumentsSource(config);
  sdk.installMemory(ctx,{plugin:upstream.memoryPlugin,sources:[source]},
    // Keep the installed starter Source identity: the host binds workspace paths,
    // pinned grants and management revisions to this identity, not the module name.
    {instanceId:'include:mnemon-source-documents',effectiveDigest:sdk.memoryConfigurationDigest({...config,semanticAdapter:1})});
}
