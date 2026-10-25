// Narrow Android adaptations for the isolated upstream rc.3 investigation.
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
const root = path.resolve(process.argv[2] ?? '');
if (!root.includes('/sliver-wolf-dsh-upgrade/')) throw new Error('Expected isolated candidate');
const modules = path.join(root, 'runtime/node_modules');
const version = JSON.parse(fs.readFileSync(path.join(modules, '@deepseek-ai/dsh/package.json'))).version;
if (version !== '0.2.1-rc.3') throw new Error(`Unreviewed DSH version: ${version}`);
const hash = value => createHash('sha256').update(value).digest('hex');
function patch(relative, changes) {
  const file = path.join(modules, relative);
  const current = fs.readFileSync(file, 'utf8');
  const backup = file + '.upstream';
  const original = fs.existsSync(backup) ? fs.readFileSync(backup, 'utf8') : current;
  let next = original;
  let known = current === original;
  for (const [before, after] of changes) {
    if (next.split(before).length !== 2) throw new Error(`Unexpected patch target: ${relative}`);
    next = next.replace(before, after);
    known ||= current === next;
  }
  if (!known) throw new Error(`Candidate was independently modified: ${relative}`);
  if (!fs.existsSync(backup)) fs.writeFileSync(backup, original, { flag: 'wx' });
  fs.writeFileSync(file, next);
  console.log(JSON.stringify({ file: relative, before: hash(original), after: hash(next) }));
}
// Reuse the exposed-internals route already supported by upstream HMR/loader.
patch('@deepseek-ai/dsh-app-boot/lib/index.js', [[
  'const addon = createRequire(import.meta.url)("node-addon-require-builtin");',
  'const addon = process.platform === "android" && process.execArgv.includes("--expose-internals")\n\t\t? { requireBuiltin: createRequire(import.meta.url) }\n\t\t: createRequire(import.meta.url)("node-addon-require-builtin");',
]]);
// Reuse the existing Android port's exclusive-copy fallback. This preserves
// no-overwrite behavior but does not promise atomic visibility during copying.
patch('@deepseek-ai/dsh-fs-local/lib/index.js', [
  ['import { createReadStream, realpath } from "node:fs";',
   'import { createReadStream, realpath, constants as androidFsConstants } from "node:fs";\nimport { copyFile as androidCopyFile } from "node:fs/promises";'],
  ['await linkFile(tempPath, absolutePath);',
   'try {\n\t\t\t\tawait linkFile(tempPath, absolutePath);\n\t\t\t} catch (error) {\n\t\t\t\tif (process.platform !== "android" || !["EACCES", "EPERM", "ENOTSUP", "EOPNOTSUPP"].includes(error?.code)) throw error;\n\t\t\t\tawait androidCopyFile(tempPath, absolutePath, androidFsConstants.COPYFILE_EXCL);\n\t\t\t}'],
]);
// Keep upstream's real asynchronous flock implementation; compile its shipped
// C source on the phone, then load that Android binary through this branch.
patch('@deepseek-ai/node-addon-system/lib/flock.js', [[
  'const { platform, arch } = process;',
  'const { platform, arch } = process;\n    if (platform === "android") {\n        binding = createRequire(import.meta.url)("../bin/android-arm64/system.node");\n        return binding;\n    }',
]]);
patch('@deepseek-ai/dsh-session-persistence-jsonl/lib/index.js', [
  ['import { readdirSync } from "node:fs";',
   'import { readdirSync, constants as androidFsConstants } from "node:fs";\nimport { copyFile as androidCopyFile } from "node:fs/promises";'],
  ['await link(tmp, finalPath);',
   'try {\n\t\t\t\tawait link(tmp, finalPath);\n\t\t\t} catch (error) {\n\t\t\t\tif (process.platform !== "android" || !["EACCES", "EPERM", "ENOTSUP", "EOPNOTSUPP"].includes(error?.code)) throw error;\n\t\t\t\tawait androidCopyFile(tmp, finalPath, androidFsConstants.COPYFILE_EXCL);\n\t\t\t\tconst published = await open(finalPath, "r");\n\t\t\t\ttry { await published.sync(); } finally { await published.close(); }\n\t\t\t}'],
  ['await internals.fs.link(staged, currentPath);',
   'try { await internals.fs.link(staged, currentPath); } catch (error) {\n\t\t\tif (process.platform !== "android" || !["EACCES", "EPERM", "ENOTSUP", "EOPNOTSUPP"].includes(error?.code)) throw error;\n\t\t\tawait androidCopyFile(staged, currentPath, androidFsConstants.COPYFILE_EXCL);\n\t\t\tconst published = await internals.fs.open(currentPath, "r");\n\t\t\ttry { await published.sync(); } finally { await published.close(); }\n\t\t}'],
]);
patch('@deepseek-ai/dsh-attachment-local/lib/index.js', [
  ['import { chmod, link, mkdir, open, readFile, rename, rm, unlink, writeFile } from "node:fs/promises";',
   'import { chmod, link, mkdir, open, readFile, rename, rm, unlink, writeFile, copyFile } from "node:fs/promises";\nasync function androidPublishLink(source, target) {\n  try { await link(source, target); } catch (error) {\n    if (process.platform !== "android" || !["EACCES", "EPERM", "ENOTSUP", "EOPNOTSUPP"].includes(error?.code)) throw error;\n    await copyFile(source, target, constants.COPYFILE_EXCL);\n    const published = await open(target, "r");\n    try { await published.sync(); } finally { await published.close(); }\n  }\n}'],
  ['await ensureDurableDirectory(home, parse(home).root);',
   'const androidHome = process.env.HOME && resolve(process.env.HOME);\n\t\tconst boundary = process.platform === "android" && androidHome && home.startsWith(androidHome + "/") ? androidHome : parse(home).root;\n\t\tawait ensureDurableDirectory(home, boundary);'],
  ['await link(source, target);\n\t\t} catch (error)',
   'await androidPublishLink(source, target);\n\t\t} catch (error)'],
  ['await link(staged.path, target);', 'await androidPublishLink(staged.path, target);'],
]);
