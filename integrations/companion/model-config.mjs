// rc.3 adapter metadata is authoritative; defaults are not advertised hard limits.
export function outputBudget(request, info, budget) {
  const limits=[budget,request.maxTokens ?? info.defaultMaxTokens];
  const known=limits.filter(x=>Number.isSafeInteger(x)&&x>0);
  if(!known.length)throw new Error('No valid autonomous output budget');
  return Math.min(...known);
}

// Native rc.3 seeds later requests from the previous durable request header.
// Undo only our own matching override, preserving unrelated explicit controls.
export function restoreOutput(request, override) {
  if(!override||request.provider!==override.provider||request.model!==override.model||request.maxTokens!==override.applied)return request;
  const restored={...request};
  if(override.original===null)delete restored.maxTokens;
  else restored.maxTokens=override.original;
  return restored;
}

export async function modelSnapshot(ctx, sessionId, budget) {
  const {agent}=await ctx.sessionController.resolveAgent(sessionId);
  if(!agent)throw new Error('Selected conversation unavailable');
  // Reuse the native selection resolver, including pending selection and the
  // deployment default. The last request alone is stale after a model switch.
  for(let attempt=0;attempt<3;attempt++) {
    const selected={...ctx.sessionController.agents.selectionFor(agent).current};
    const info=await ctx.llm.resolveModelInfo(selected.provider,selected.model,AbortSignal.timeout(10000));
    const current=ctx.sessionController.agents.selectionFor(agent).current;
    if(JSON.stringify(current)!==JSON.stringify(selected))continue;
    const header=agent.session.requestHeader();
    const same=header?.config.provider===selected.provider&&header.config.model===selected.model;
    return {sessionId,source:'dsh-exact-model',observedAt:new Date().toISOString(),
      provider:info.provider,model:info.id,name:info.name,reasoningEffort:selected.reasoningEffort??info.reasoning?.defaultEffort??null,
      contextWindow:info.context?.contextWindow??null,inputModalities:info.inputModalities??null,
      defaultOutputTokens:info.defaultMaxTokens??null,maxOutputTokens:null,
      autonomousOutputCeiling:outputBudget({},info,budget),userOutputBudget:budget,
      lastRequestOutputTokens:same?header.config.maxTokens??null:null,
      contextManagement:'dsh-native'};
  }
  throw new Error('Model selection changed during lookup; refresh required');
}
