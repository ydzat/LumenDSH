// Extracted from guzhou079-arch/deepseek-harness-android at a5af7b105b7fdb006e3fc3be2a2179d83907c2cb.
// Copyright (c) 2026 zhou-gu24; MIT, see LICENSE.upstream.
// Only pure hashing and atomic JSON receipt helpers; no upstream runtime or publisher.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
const insist=(ok,message)=>{if(!ok)throw new Error(message);};
export const shaBuffer = b => crypto.createHash('sha256').update(b).digest('hex');
export function readJson(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
export function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const absolute = path.resolve(file), tmp = absolute + '.tmp-' + process.pid;
  insist(path.dirname(tmp) === path.dirname(absolute), 'Invalid receipt target');
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
  fs.renameSync(tmp, absolute);
  insist(JSON.stringify(readJson(absolute)) === JSON.stringify(value), 'Receipt readback mismatch');
}
