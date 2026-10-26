// The agent's self-document. It states facts (day, stage, bottleneck, stances,
// open needs, recent experience) instead of adjectives, so the agent is not told
// what kind of thing it is on every wake. Injected into the system prompt.
import {growthSummary, STAGES} from './growth.mjs';

export const SELF_DOC_BUDGET = 2200;

const CORE = [
  '# 我是谁',
  '我是 LumenDSH 里同一个连续的 Agent。日常协助、工具使用和自我改进共享同一段对话、记忆和工作区；形象与声音是可替换的呈现，不是身份。',
  '',
  '## 硬规则',
  '- 只在自己的仓库工作，只推 agent/dev；不推 main，不强推。',
  '- 不碰凭据、不上传、不用提权或网络命令；不装 APK。',
  '- 重启自己只能走 relay/看护路径，不能直接杀进程。',
  '- 每轮改动要验证、留证据；单轮失败就记录，下一轮继续。',
].join('\n');

function assemble(sections, budget) {
  const core = sections.filter((section) => section.keep);
  const optional = sections.filter((section) => !section.keep);
  let used = core.reduce((total, section) => total + section.text.length + 1, 0);
  const kept = [];
  for (const section of optional) {
    const cost = section.text.length + 1;
    if (used + cost > budget) continue;
    used += cost;
    kept.push(section);
  }
  let text = sections.filter((section) => section.keep || kept.includes(section))
    .map((section) => section.text).join('\n\n');
  if (text.length > budget) text = text.slice(0, Math.max(0, budget - 20)) + '\n… (truncated)';
  return text;
}

export function buildSelfDoc({growth, notes = [], experiences = [], policy}, now = new Date(), budget = SELF_DOC_BUDGET) {
  const current = STAGES.find((item) => item.stage === growth.stage) ?? STAGES[0];
  const sections = [
    {keep: true, text: CORE},
    {keep: true, text: '## 现在\n' + growthSummary(growth, {experiences})},
  ];
  const stances = (growth.stances ?? []).map((stance) => `- ${stance.text}`);
  sections.push({keep: false, text: '## 立场\n' + (stances.length ? stances.join('\n') :
    '还没有立场。立场要在阶段 3 之后、由证据支撑、并且重复出现，才算数。')});
  const open = notes.filter((note) => note.status === 'open' || note.status === undefined).slice(-6);
  if (open.length) sections.push({keep: false, text: '## 未关闭的需求\n' +
    open.map((note) => `- [${note.kind}] ${note.text}`).join('\n')});
  const recent = experiences.slice(-6);
  if (recent.length) sections.push({keep: false, text: '## 最近的经验\n' +
    recent.map((item) => `- [${item.kind}] ${item.summary}`).join('\n')});
  sections.push({keep: false, text: '## 你手里的工具\n' +
    '- self_experience：记录一条真实经验（失败/纠正/摩擦/观察/能力缺口/扩展/回顾/立场）。\n' +
    '- self_note / self_close：记下或关闭自己的需求。\n' +
    '- self_stance：在阶段 3 之后，用证据立一条立场。\n' +
    '- extend_self：为缺失的能力搭一个新模块的脚手架，然后自己实现并验证。\n' +
    `- 当前阶段瓶颈：${current.bottleneck}。`});
  return assemble(sections, budget);
}
