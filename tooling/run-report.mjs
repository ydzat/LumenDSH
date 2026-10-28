// Read-only autonomy report: recent runs, durations and failure clusters.
// Usage: node tooling/run-report.mjs [limit]
import {readFileSync} from 'node:fs';
import path from 'node:path';
const home = process.env.HOME;
const limit = Math.max(1, Math.min(50, Number(process.argv[2] ?? 10) || 10));
const conn = JSON.parse(readFileSync(path.join(home, '.local/state/sliver-wolf-backend/connection-3080.json'), 'utf8'));
const base = 'http://127.0.0.1:' + (conn.port || 3080);
const status = await (await fetch(base + '/sliver-wolf/companion/status', {headers: {cookie: conn.cookie}})).json();
const runs = (status.runs ?? []).slice(-limit).reverse();
const ms = value => (value ? new Date(value).getTime() : 0);
console.log('policy:', status.policy?.enabled ? 'enabled' : 'disabled', '| paused:', Boolean(status.policy?.paused),
  '| period:', status.period?.phase ?? 'unknown');
console.log('lastError:', status.lastError ?? 'none');
console.log('activity:', (status.policy?.activities ?? []).join(','));
for (const run of runs) {
  const start = ms(run.startedAt), end = ms(run.endedAt);
  const seconds = start && end ? Math.round((end - start) / 1000) : null;
  console.log([run.activity, run.status, seconds === null ? '' : seconds + 's',
    'steps=' + (run.steps ?? '-'), 'tools=' + (run.tools ?? '-'), run.startedAt?.slice(11, 19),
    run.source ?? ''].filter(Boolean).join(' · '));
}
const day = Date.now() - 86400000;
const recent = (status.runs ?? []).filter(run => ms(run.startedAt) >= day);
const failed = recent.filter(run => ['failed', 'delivery-uncertain', 'interrupted-unreplayed'].includes(run.status));
const byActivity = {};
for (const run of failed) byActivity[run.activity] = (byActivity[run.activity] ?? 0) + 1;
console.log('last 24h:', recent.length, 'runs,', failed.length, 'failed', Object.keys(byActivity).length ? JSON.stringify(byActivity) : '');
