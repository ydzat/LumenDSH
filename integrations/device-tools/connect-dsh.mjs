// Initial setup for an empty profile patch layer; never overwrite existing user entries.
import { readFile, writeFile, rename, copyFile, mkdir, access } from 'node:fs/promises';
import { constants } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dependencies, VERSION } from './bridge.mjs';

const [profileFile, releaseDirectory, mode] = process.argv.slice(2);
if (!profileFile || !releaseDirectory || !process.env.SLIVER_WOLF_TOKEN_FILE)
  throw new Error('Usage: connect-dsh.mjs <profile-cordis.patch.yml> <private-release-directory>; set DSH_ROOT and SLIVER_WOLF_TOKEN_FILE');
const { require } = await dependencies();
const yaml = require('yaml');
const original = await readFile(profileFile, 'utf8');
const existing = yaml.parse(original);
const ownedOnly = Array.isArray(existing) && existing.length === 1
  && Object.keys(existing[0]).length === 1 && Array.isArray(existing[0].insert)
  && existing[0].insert.length === 1 && existing[0].insert[0].id === 'sliver-wolf-device-tools'
  && existing[0].insert[0].name === '@deepseek-ai/dsh-mcp-client';
if (!Array.isArray(existing) || (mode === '--update' ? !ownedOnly : existing.length !== 0))
  throw new Error('Expected an empty patch, or --update with only our existing entry; inspect other user entries manually');
await access(process.env.SLIVER_WOLF_TOKEN_FILE);
const target = resolve(releaseDirectory, 'bridge.mjs');
const patch = [{ insert: [{ id: 'sliver-wolf-device-tools', name: '@deepseek-ai/dsh-mcp-client', config: {
  serverName: 'sliver_wolf', transport: 'stdio', command: 'node', args: [target],
  env: { DSH_ROOT: process.env.DSH_ROOT, SLIVER_WOLF_TOKEN_FILE: process.env.SLIVER_WOLF_TOKEN_FILE, SLIVER_WOLF_SHARE_DIR: resolve(releaseDirectory, "../../shared-files") },
  toolCallTimeoutMs: 120000, failOnStartupError: true,
} }] }];
await mkdir(dirname(target), { recursive: true, mode: 0o700 });
await copyFile(fileURLToPath(new URL('./bridge.mjs', import.meta.url)), target, constants.COPYFILE_EXCL);
await copyFile(fileURLToPath(new URL('./upload-artifact.mjs', import.meta.url)), resolve(releaseDirectory, 'upload-artifact.mjs'), constants.COPYFILE_EXCL);
await copyFile(fileURLToPath(new URL('./receive-share.mjs', import.meta.url)), resolve(releaseDirectory, 'receive-share.mjs'), constants.COPYFILE_EXCL);
await writeFile(profileFile + '.before-sliver-wolf-' + VERSION, original, { flag: 'wx', mode: 0o600 });
const candidate = profileFile + '.sliver-wolf-candidate';
await writeFile(candidate, yaml.stringify(patch), { flag: 'wx', mode: 0o600 });
await rename(candidate, profileFile);
console.log('Added isolated MCP entry through profile patch HMR; no DSH restart requested. Verify live tool discovery.');
