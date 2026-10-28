// Summarise the local autonomous-run trace (behavior/trace-<date>.jsonl).
import {readdirSync, readFileSync} from 'node:fs';
import path from 'node:path';
const home = process.env.HOME;
const dir = path.join(home, 'sliver-wolf-companion/behavior');
const files = (() => { try { return readdirSync(dir).filter(name => /^trace-\d{4}-\d{2}-\d{2}\.jsonl$/.test(name)).sort(); }
  catch { return []; } })();
if (!files.length) { console.log('no trace files yet'); process.exit(0); }
const runs = new Map();
for (const file of files) for (const line of readFileSync(path.join(dir, file), 'utf8').split('\n')) {
  if (!line.trim()) continue;
  let entry; try { entry = JSON.parse(line); } catch { continue; }
  const run = runs.get(entry.run) ?? {run: entry.run, activity: entry.activity, first: entry.at,
    last: entry.at, tools: {}, start: null, end: null, status: null, steps: null};
  run.last = entry.at;
  if (entry.type === 'tool') run.tools[entry.tool] = (run.tools[entry.tool] ?? 0) + 1;
  if (entry.type === 'start') { run.start = entry.at; run.first = entry.at; }
  if (entry.type === 'end') { run.end = entry.at; run.status = entry.status; run.steps = entry.steps; }
  runs.set(entry.run, run);
}
for (const run of [...runs.values()].sort((a, b) => a.first.localeCompare(b.first))) {
  const seconds = run.start && run.end ? Math.round((new Date(run.end) - new Date(run.start)) / 1000) + 's' : '-';
  const tools = Object.entries(run.tools).map(([name, count]) => name + '×' + count).join(' ');
  console.log([run.activity, run.run.slice(0, 8), run.status ?? 'running', seconds,
    'steps=' + (run.steps ?? '-'), tools].filter(Boolean).join(' | '));
}
