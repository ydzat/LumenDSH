// Runs real audible output. No recordings or transcript fixtures are created.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { connectDevice } from './bridge.mjs';
const phase = process.argv[2] ?? 'normal';
if (!['normal', 'prepare-crash', 'recover'].includes(phase)) throw new Error('Expected normal, prepare-crash, or recover');
if (phase !== 'normal' && !process.env.CHECK_STATE_FILE) throw new Error('Set CHECK_STATE_FILE to a private test-state file');
const device = await connectDevice();
const call = (name, args = {}) => device.request({ method: 'call', name, arguments: args });
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
async function terminal(key) {
  for (let i = 0; i < 60; i++) {
    const record = await call('assistant_get_operation', { operationKey: key });
    if (!['pending', 'running'].includes(record.state)) return record;
    await wait(500);
  }
  throw new Error('Speech did not settle within 30 seconds');
}
try {
  let status;
  for (let i = 0; i < 20; i++) {
    status = await call('assistant_get_status');
    if (status.voice.state !== 'initializing') break;
    await wait(500);
  }
  assert.equal(status.voice.state, 'ready', 'Installed TTS must actually initialize');
  if (phase === 'recover') {
    const saved = JSON.parse(await readFile(process.env.CHECK_STATE_FILE, 'utf8'));
    const record = await call('assistant_get_operation', { operationKey: saved.args.operationKey });
    assert.equal(record.state, 'unknown');
    assert.equal('inputDigest' in record, false, 'Storage-only fingerprint must not leak into tool results');
    const repeated = await call('assistant_speak', saved.args);
    assert.equal(repeated.operationId, saved.operationId);
    assert.equal(repeated.state, 'unknown');
    assert.equal((await call('assistant_get_status')).voice.speaking, false);
    console.log('Speech process-loss recovery: unknown retained; repeat did not replay.');
  } else if (phase === 'prepare-crash') {
    const args = { operationKey: 'speech-crash-' + randomUUID(), language: 'zh-CN',
      text: '这是进程恢复测试，稍后将停止客户端。'.repeat(40) };
    const record = await call('assistant_speak', args);
    assert.equal(record.state, 'pending');
    await writeFile(process.env.CHECK_STATE_FILE, JSON.stringify({ args, operationId: record.operationId }), { mode: 0o600 });
    for (let i = 0; i < 30; i++) {
      if ((await call('assistant_get_operation', { operationKey: args.operationKey })).state === 'running') {
        console.log('Speech running; ready for explicit client force-stop.'); break;
      }
      await wait(100);
      if (i === 29) throw new Error('Playback never started');
    }
  } else {
    const args = { operationKey: 'speech-' + randomUUID(), text: '你好，这是银狼客户端的语音播放测试。', language: 'zh-CN' };
    const record = await call('assistant_speak', args);
    assert.equal(record.state, 'pending');
    assert.equal((await terminal(args.operationKey)).state, 'succeeded');
    const repeated = await call('assistant_speak', args);
    assert.equal(repeated.operationId, record.operationId);
    assert.equal(repeated.state, 'succeeded');
    await assert.rejects(call('assistant_speak', { ...args, text: 'Different input' }), /conflict/);
    const long = { operationKey: 'speech-stop-' + randomUUID(), text: 'This is a controlled playback stop test. '.repeat(20), language: 'en-US' };
    await call('assistant_speak', long);
    await wait(800);
    const stopped = await call('assistant_stop_speaking', { operationKey: 'stop-' + randomUUID() });
    assert.equal(stopped.state, 'succeeded');
    assert.equal((await terminal(long.operationKey)).state, 'cancelled');
    console.log(JSON.stringify({ engine: status.voice.engine, completedCallback: true, acceptedBeforeComplete: true,
      retryDidNotReplay: true, changedInputRejected: true, explicitStopCancelled: true }));
  }
} finally { device.close(); }
