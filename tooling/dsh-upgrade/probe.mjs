// Run only against the isolated candidate; never pass an active DSH home.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import path from 'node:path';

const root = path.resolve(process.argv[2] ?? '');
if (!root.includes('/sliver-wolf-dsh-upgrade/')) throw new Error('Expected isolated upgrade root');
const base = 'http://127.0.0.1:3082';
const environment = { ...process.env, DSH_HOME: path.join(root, 'home'), DSH_TELEMETRY_DISABLED: '1' };
delete environment.DEEPSEEK_API_KEY;
const child = spawn('node', ['--expose-internals', path.join(root, 'runtime/node_modules/@deepseek-ai/dsh/lib/bin.js'),
  'web', '--host', '127.0.0.1', '--port', '3082', '--no-open'], {
  cwd: path.join(root, 'workspace'), env: environment, stdio: ['ignore', 'pipe', 'pipe'],
});
let output = '';
child.stdout.on('data', bytes => { output += bytes; });
child.stderr.on('data', bytes => { output += bytes; });
const redact = value => String(value).replace(/([?&]token=)[^\s]+/g, '$1[REDACTED]');
try {
  let token;
  for (let attempt = 0; attempt < 100; attempt++) {
    token = output.match(/[?&]token=([^\s]+)/)?.[1];
    if (token) break;
    if (child.exitCode !== null) throw new Error(redact(output));
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  if (!token) throw new Error('Candidate did not become ready within 20 seconds');
  const unauthenticated = await fetch(base);
  console.log(JSON.stringify({ check: 'unauthenticated-root', status: unauthenticated.status }));
  const exchange = await fetch(`${base}/?token=${token}`, { redirect: 'manual' });
  const cookie = exchange.headers.get('set-cookie')?.split(';')[0];
  if (!cookie) throw new Error(`No browser cookie: HTTP ${exchange.status}`);
  const headers = { Cookie: cookie, 'Content-Type': 'application/json' };
  const page = await fetch(base, { headers });
  console.log(JSON.stringify({ check: 'authenticated-root', status: page.status }));
  async function rpc(method, payload) {
    const response = await fetch(`${base}/api/${method}`, {
      method: 'POST', headers, body: JSON.stringify({ type: 'client-request',
        rpcId: `upgrade-${method}-${Date.now()}`, method, payload: { args: payload } }), signal: AbortSignal.timeout(10000),
    });
    const text = await response.text();
    let result; try { result = JSON.parse(text); } catch { result = text.slice(0, 200); }
    const report = method === 'session/list' && result?.result?.ok
      ? { ok: true, sessions: result.result.value.items.map(item => ({
        title: item.projections?.values?.title, formatStatus: item.formatStatus,
      })) } : result;
    console.log(JSON.stringify({ check: method, status: response.status, result: report }));
    return result;
  }
  await rpc('session/list', { _request: {} });
  const created = process.argv.includes('--read-only') ? null
    : await rpc('session/create', { request: { cwd: path.join(root, 'workspace') } });
  if (created?.result?.ok) await rpc('session/rename', { request: {
    sessionId: created.result.value.sessionId, title: 'Controlled upgrade persistence probe',
  } });
  if (created?.result?.ok && process.argv.includes('--write-probe')) {
    await rpc('session/prompt', { request: {
      sessionId: created.result.value.sessionId, requestId: `upgrade-persistence-${Date.now()}`,
      mode: 'queue', content: [{ type: 'text', text: 'Controlled offline persistence probe. No model is configured.' }],
    } });
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  await rpc('session/list', { _request: {} });
  await rpc('session.list', {});
} finally {
  const stopped = once(child, 'exit');
  if (child.exitCode === null) {
    child.kill('SIGTERM');
    const force = setTimeout(() => child.kill('SIGKILL'), 5000);
    await stopped;
    clearTimeout(force);
  }
  const errors = output.split('\n').filter(line => /error|failed|EACCES|fatal/i.test(line));
  if (errors.length) console.log(redact(errors.slice(0, 12).join('\n')));
}
