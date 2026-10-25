// Pair with the test APK's acquire-focus action after PLAYBACK_RUNNING appears.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { connectDevice } from './bridge.mjs';
const device = await connectDevice();
const call = (name, args = {}) => device.request({method: 'call', name, arguments: args});
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const key = 'focus-check-' + randomUUID();
const mode = process.argv[2] ?? 'focus';
if (!['focus', 'barge-in'].includes(mode)) throw new Error('Expected focus or barge-in');
try {
  const args = {operationKey: key, language: 'zh-CN',
    text: '这是音频冲突测试，另一程序取得音频焦点时，助手应停止播放。'.repeat(20)};
  await call('assistant_speak', args);
  let started = false;
  for (let i = 0; i < 100; i++) {
    const record = await call('assistant_get_operation', {operationKey: key});
    if (record.state === 'running' && !started) { started = true; console.log('PLAYBACK_RUNNING'); }
    if (record.state === 'cancelled') {
      assert.equal(started, true);
      assert.equal(record.detail, mode === 'focus' ? 'Audio focus lost' : 'Stopped from client control');
      if (mode === 'barge-in') {
        assert.equal((await call('assistant_get_status')).microphoneActive, true);
        const repeated = await call('assistant_speak', args);
        assert.equal(repeated.operationId, record.operationId);
        assert.equal(repeated.state, 'cancelled');
        await assert.rejects(call('assistant_speak', {...args, operationKey: 'new-' + key}), /busy: microphone/);
        console.log('MICROPHONE_ACTIVE_AFTER_PLAYBACK_STOP');
      }
      await wait(2500);
      assert.equal((await call('assistant_get_status')).voice.speaking, false);
      if (mode === 'barge-in') assert.equal((await call('assistant_get_status')).microphoneActive, false);
      console.log('PASS: ' + mode + ' cancelled playback; no automatic replay');
      process.exitCode = 0;
      break;
    }
    if (i === 99) throw new Error('No audio-focus cancellation observed within 20 seconds');
    await wait(200);
  }
} finally {
  await call('assistant_stop_speaking', {operationKey: 'cleanup-' + key});
  device.close();
}
