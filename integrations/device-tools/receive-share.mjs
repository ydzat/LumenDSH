// Reverse the existing authenticated artifact transfer pattern for explicitly reviewed shares.
import { mkdir, mkdtemp, open, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, join, isAbsolute } from 'node:path';
import { connectDevice } from './bridge.mjs';

async function digest(file) {
  const hash = createHash('sha256');
  for await (const bytes of createReadStream(file)) hash.update(bytes);
  return hash.digest('hex');
}
export async function receiveShare(args) {
  const { shareId, sessionId } = args;
  if (Object.keys(args).some(key => !['shareId', 'sessionId'].includes(key))
      || typeof shareId !== 'string' || !/^[a-f0-9-]{36}$/.test(shareId)
      || typeof sessionId !== 'string' || !sessionId || sessionId.length > 200)
    throw new Error('Invalid share or session identifier');
  if (!process.env.SLIVER_WOLF_SHARE_DIR || !isAbsolute(process.env.SLIVER_WOLF_SHARE_DIR))
    throw new Error('SLIVER_WOLF_SHARE_DIR must be an explicit private absolute directory');
  const root = resolve(process.env.SLIVER_WOLF_SHARE_DIR);
  const destination = join(root, shareId);
  const device = await connectDevice();
  const request = (method, extra = {}) => device.request({ method, arguments: { shareId, sessionId, ...extra } });
  let staging;
  try {
    const manifest = await request('share/manifest');
    if (manifest.id !== shareId || manifest.sessionId !== sessionId || !Array.isArray(manifest.files)
        || manifest.files.length > 16) throw new Error('Share manifest mismatch');
    let total = 0;
    for (const [index, file] of manifest.files.entries()) {
      if (file.index !== index || !Number.isSafeInteger(file.bytes) || file.bytes < 0 || file.bytes > 16 * 1024 * 1024
          || !/^[a-f0-9]{64}$/.test(file.sha256) || typeof file.name !== 'string' || file.name.length > 160)
        throw new Error('Invalid attachment metadata');
      total += file.bytes;
    }
    if (total > 32 * 1024 * 1024) throw new Error('Share batch exceeds 32 MiB');
    await mkdir(root, { recursive: true, mode: 0o700 });
    let exists = false;
    try { await stat(destination); exists = true; } catch (error) { if (error.code !== 'ENOENT') throw error; }
    if (!exists) {
      staging = await mkdtemp(join(root, `${shareId}.partial-`));
      for (const file of manifest.files) {
        const output = await open(join(staging, `file-${file.index}`), 'wx', 0o600);
        const hash = createHash('sha256');
        try {
          let offset = 0;
          while (offset < file.bytes) {
            const chunk = await request('share/chunk', { index: file.index, offset });
            if (typeof chunk.data !== 'string') throw new Error('Missing attachment bytes');
            const bytes = Buffer.from(chunk.data, 'base64');
            if (!bytes.length || bytes.length > 32768 || offset + bytes.length > file.bytes)
              throw new Error('Attachment length mismatch');
            await output.writeFile(bytes); hash.update(bytes); offset += bytes.length;
          }
          if (hash.digest('hex') !== file.sha256) throw new Error('Attachment digest mismatch');
          await output.sync();
        } finally { await output.close(); }
      }
      await writeFile(join(staging, 'receipt.json'), JSON.stringify(manifest), { flag: 'wx', mode: 0o600 });
      try { await rename(staging, destination); staging = undefined; }
      catch (error) { if (!['EEXIST', 'ENOTEMPTY'].includes(error.code)) throw error; }
    }
    // Repeated calls verify an existing batch rather than overwrite it or submit another task.
    const receipt = JSON.parse(await readFile(join(destination, 'receipt.json'), 'utf8'));
    if (JSON.stringify(receipt) !== JSON.stringify(manifest)) throw new Error('Existing share receipt differs');
    const files = [];
    for (const file of manifest.files) {
      const path = join(destination, `file-${file.index}`);
      if ((await stat(path)).size !== file.bytes || await digest(path) !== file.sha256)
        throw new Error('Stored attachment differs from receipt');
      files.push({ ...file, path });
    }
    await request('share/received', { sha256: manifest.files.map(file => file.sha256) });
    return { received: true, shareId, sessionId, files };
  } finally {
    device.close();
    // Only this invocation's mkdtemp directory is removed; committed batches remain private.
    if (staging) await rm(staging, { recursive: true });
  }
}
