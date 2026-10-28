// Extract the user's messages from this session's compressed transcript.
// Usage: node tooling/session-digest.mjs [date YYYY-MM-DD]
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {zstdDecompressSync} from 'node:zlib';
const home = process.env.HOME;
const dir = path.join(home, '.dsh/sessions/--data-data-com.termux-files-home-agent-workspace--/session-fb13506d-f7dd-4987-a974-553b4b05c982');
const file = path.join(dir, 'session.v4.jsonl.zstd');
const date = process.argv[2] ?? new Date().toISOString().slice(0, 10);
const dayOf = time => new Date(Number(time) + 3600000).toISOString().slice(0, 10);
let text;
try { text = zstdDecompressSync(readFileSync(file)).toString('utf8'); }
catch { text = execFileSync('zstd', ['-dc', file], {maxBuffer: 256 * 1024 * 1024}).toString('utf8'); }
for (const line of text.split('\n')) {
  if (!line.trim()) continue;
  let event; try { event = JSON.parse(line); } catch { continue; }
  const time = event.time ?? event.data?.time;
  if (time && dayOf(time) !== date) continue;
  const type = event.type ?? event.data?.type;
  if (type !== 'user/message') continue;
  const blocks = event.data?.content ?? event.data?.message?.content ?? [];
  const content = blocks.filter(block => block?.type === 'text').map(block => block.text).join(' ').trim();
  if (!content) continue;
  if (/^(Time sampled|MNEMON RUNTIME MEMORY|Contents of USER\.md|<runtime-memory-file)/.test(content)) continue;
  const clock = new Date(Number(time) + 3600000).toISOString().slice(11, 19);
  console.log('--- ' + clock + ' Berlin');
  console.log(content.slice(0, 600));
}
