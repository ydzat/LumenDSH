// Summarise the local autonomous-run trace (behavior/trace-<date>.jsonl).
import {readdirSync, readFileSync} from 'node:fs';
import path from 'node:path';
const home = process.env.HOME;
const dir = path.join(home, 'sliver-wolf-companion/behavior');
const files = (() => { try { return readdirSync(dir).filter(name => /^trace-\d{4}-\d{2}-\d{2}\.jsonl$/.test(name)).sort(); }
  catch { return []; } })();
if (!files.length) { console.log('no trace files yet (the host writes them once deployed)'); process.exit(0); }
const runs = new Map();
for (const file of files) for (const line of readFileSync(path.join(dir, file), 'utf8').split('\n')) {
  if (!line.trim()) continue;
  let entry; try { entry = JSON.parse(line); } catch { continue; }
  const run = runs.get(entry.run) ?? {run: entry.run, activity: entry.activity, first: entry.at, last: entry.at, tools: {}};
  run.last = entry.at; run.tools[entry.tool] = (run.tools[entry.tool] ?? 0) + 1;
  runs.set(entry.run, run);
}
for (const run of [...runs.values()].sort((a, b) => a.first.localeCompare(b.first))) {
  const seconds = Math.round((new Date(run.last) - new Date(run.first)) / 1000);
  const tools = Object.entries(run.tools).map(([name, count]) => name + '×' + count).join(' ');
  console.log([run.activity, run.run.slice(0, 8), seconds + 's', tools].join(' | '));
}
