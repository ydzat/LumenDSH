import test from 'node:test';
import assert from 'node:assert/strict';
import {outputBudget,modelSnapshot,restoreOutput} from '../integrations/companion/model-config.mjs';

test('autonomous output respects explicit DSH controls and model defaults without inventing a hard limit',()=>{
  assert.equal(outputBudget({}, {defaultMaxTokens:1024},4096),1024);
  assert.equal(outputBudget({maxTokens:512},{defaultMaxTokens:8192},4096),512);
  assert.equal(outputBudget({maxTokens:6000},{defaultMaxTokens:1024},4096),4096);
  assert.equal(outputBudget({}, {},4096),4096);
});

test('selection changing during metadata lookup is re-resolved and old request controls do not leak',async()=>{
  let selection={provider:'test',model:'old'};
  const agent={session:{requestHeader:()=>({config:{provider:'test',model:'old',maxTokens:9999}})}};
  const ctx={sessionController:{resolveAgent:async()=>({agent}),agents:{selectionFor:()=>({current:selection})}},
    llm:{resolveModelInfo:async(provider,id)=>{selection={provider,model:'new'};return {provider,id,name:id,defaultMaxTokens:2048};}}};
  const result=await modelSnapshot(ctx,'controlled',4096);
  assert.equal(result.model,'new');assert.equal(result.lastRequestOutputTokens,null);
  assert.equal(result.contextWindow,null);assert.equal(result.maxOutputTokens,null);
  assert.equal(result.autonomousOutputCeiling,2048);
});

test('autonomous header limit is removed from later user requests without erasing explicit overrides',()=>{
  const own={provider:'p',model:'m',original:null,applied:2048};
  const request={provider:'p',model:'m',maxTokens:2048,reasoningEffort:'high'};
  assert.deepEqual(restoreOutput(request,own),{provider:'p',model:'m',reasoningEffort:'high'});
  assert.equal(restoreOutput(request,{...own,original:1024}).maxTokens,1024);
  assert.equal(restoreOutput({...request,maxTokens:8000},own).maxTokens,8000);
  assert.equal(restoreOutput({...request,model:'different'},own).maxTokens,2048);
});
