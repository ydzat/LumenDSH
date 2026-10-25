import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync,writeFileSync} from 'node:fs';
import path from 'node:path';
const root=path.join(process.env.HOME,'sliver-wolf-companion');
const directory=mkdtempSync(path.join(root,'semantic-check-'));
const binary=path.join(root,'bin/mnemon-semantic-candidate');
const environment={...process.env,MNEMON_DATA_DIR:directory,MNEMON_STORE:'default',MNEMON_EMBED_ENDPOINT:'http://127.0.0.1:3083/v1',MNEMON_EMBED_MODEL:'sw-qwen3-embedding-0.6b-q8',MNEMON_REQUIRE_SEMANTIC:'1',MNEMON_QUERY_INSTRUCTION:'Given a question about the user, retrieve relevant personal preferences, facts and shared experiences.'};
delete environment.LD_PRELOAD;
function run(args,extra={}) {return JSON.parse(execFileSync('/system/bin/sh',['-c','exec "$@"','mnemon',binary,...args],{env:{...environment,...extra},encoding:'utf8',timeout:120000}));}
const cases=[
 {fact:'她不能摄入乳糖，喝拿铁需要把牛乳换成燕麦饮。',query:'如果要给她点奶咖，配料应该怎样调整？',needle:'燕麦'},
 {fact:'他每逢周末喜欢去山间徒步，不喜欢挤在商场里购物。',query:'休息日安排哪种户外活动会让他开心？',needle:'徒步'},
 {fact:'她睡眠很浅，晚上十一点后希望设备保持安静，不要播放声音。',query:'夜深了还能向她播报提醒吗？',needle:'十一点'},
 {fact:'重要文件在每周五复制到家里的移动硬盘上，防止电脑损坏后丢失。',query:'资料的备份频率和保存地点是什么？',needle:'硬盘'},
];
const started=Date.now();
for(const test of cases){const result=run(['remember',test.fact,'--cat','fact']);assert.equal(result.embedded,true,JSON.stringify(result));}
const status=run(['embed','--status']);assert.equal(status.embedded,4);assert.equal(status.embedding_available,true);
const report=[];let firstId;
for(const test of cases){const result=run(['recall',test.query,'--limit','3','--verbose']);writeFileSync(path.join(directory,'last-recall.json'),JSON.stringify(result));const records=result.results;assert(Array.isArray(records),JSON.stringify(result));firstId??=records[0]?.insight?.id;const first=records[0]?.insight?.content??records[0]?.content;assert(first?.includes(test.needle),JSON.stringify(result));report.push({query:test.query,top1:first});}
let rejected=false;try{run(['recall','无服务时不能假装语义查询成功'],{MNEMON_EMBED_ENDPOINT:'http://127.0.0.1:9/v1'});}catch(error){rejected=String(error.stderr).includes('semantic recall unavailable');}assert(rejected,'Unavailable embedding silently degraded');
let writeRejected=false;try{run(['remember','该写入因向量服务离线应当失败'],{MNEMON_EMBED_ENDPOINT:'http://127.0.0.1:9/v1'});}catch(error){writeRejected=String(error.stderr).includes('no record written');}assert(writeRejected);
assert.equal(run(['embed','--status']).total_insights,4,'Failed write created a record');
assert(firstId);assert.equal(run(['forget',firstId]).status,'deleted');
const afterForget=run(['recall',cases[0].query,'--verbose']);assert(!afterForget.results.some(r=>r.insight?.id===firstId),'Deleted memory returned by semantic recall');
console.log(JSON.stringify({status,report,elapsedMs:Date.now()-started,unavailableRejected:rejected,writeRejected,forgottenExcluded:true}));
writeFileSync(path.join(root,'semantic-receipt.json'),JSON.stringify({directory,status,report,unavailableRejected:rejected,writeRejected,forgottenExcluded:true},null,2));
