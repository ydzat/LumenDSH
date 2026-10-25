// Transfer explicit local bytes; the app never reads a Termux path or remote URL.
import { open, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { connectDevice } from './bridge.mjs';

export async function uploadArtifact(file, operationKey, purpose) {
  const info = await stat(file);
  if (!info.isFile() || info.size < 1 || info.size > (purpose === "native-apk" ? 256 : 8) * 1024 * 1024) throw new Error('Expected a regular file of 1 byte to 8 MiB');
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  const sha256 = hash.digest('hex');
  const device = await connectDevice();
  const request = (method, args) => device.request({ method, arguments: { operationKey, ...args } });
  try {
    let result = await request('artifact/begin', { size: info.size, sha256, ...(purpose ? {purpose} : {}) });
    if (result.state === 'succeeded') return result;
    if (result.state !== 'pending') throw new Error('Previous upload is terminal; choose a new operationKey');
    const input = await open(file, 'r');
    try {
      let offset = result.received;
      while (offset < info.size) {
        const buffer = Buffer.alloc(Math.min(32768, info.size - offset));
        const { bytesRead } = await input.read(buffer, 0, buffer.length, offset);
        if (!bytesRead) throw new Error('Input file changed or ended during upload');
        result = await request('artifact/chunk', { offset, base64: buffer.subarray(0, bytesRead).toString('base64') });
        if (result.received <= offset) throw new Error('Device did not advance upload offset');
        offset = result.received;
      }
    } finally { await input.close(); }
    return await request('artifact/finish', {});
  } finally { device.close(); }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [file, key] = process.argv.slice(2);
  if (!file || !key) throw new Error('Usage: upload-artifact.mjs <local-file> <operation-key>');
  console.log(JSON.stringify(await uploadArtifact(file, key)));
}
