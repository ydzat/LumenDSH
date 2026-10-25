// Run only with the explicitly shared ShareFixtureProvider normal fixture.
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { receiveShare } from './receive-share.mjs';
const [shareId, sessionId] = process.argv.slice(2);
assert(shareId && sessionId, 'Pass the controlled fixture share/session identifiers');
const first = await receiveShare({ shareId, sessionId });
const second = await receiveShare({ shareId, sessionId });
assert.deepEqual(second, first, 'Repeated reception returns the same verified files');
assert.equal(first.files.length, 1);
const expected = Buffer.from('M45_SHARED_FILE_OK\n');
assert.deepEqual(await readFile(first.files[0].path), expected);
assert.equal(first.files[0].sha256, createHash('sha256').update(expected).digest('hex'));
await assert.rejects(receiveShare({ shareId, sessionId: 'wrong-controlled-session' }), /not authorized/);
console.log('REAL_SHARE_TRANSFER_REPLAY_AND_SESSION_FENCE_PASS');
