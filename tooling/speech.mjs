#!/usr/bin/env node
// Local speech module CLI. Keeps the model/runtime setup out of the host so a
// failed download never blocks normal operation.
import {existsSync, mkdirSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {CATALOG, MODELS, ROOT, RUNTIME, prepareModel, runtimeReady, status, transcribeFile} from '../integrations/speech/engine.mjs';

const runtimeUrl = process.env.LUMENDSH_SPEECH_RUNTIME_URL ||
  'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-android-aarch64-termux-shared.tar.bz2';

function usage() {
  process.stdout.write(`Usage: node tooling/speech.mjs <command>
  status                     Show runtime and model readiness
  install-runtime [--url U]  Download and unpack the Termux sherpa-onnx runtime
  prepare <model>            Download, verify and unpack a model
  transcribe <wav> [--model M] [--language L]
`);
}

async function installRuntime(url = runtimeUrl) {
  mkdirSync(ROOT, {recursive: true, mode: 0o700});
  const archive = path.join(ROOT, 'runtime.tar.bz2');
  if (!existsSync(archive)) {
    const response = await fetch(url, {redirect: 'follow'});
    if (!response.ok) throw new Error('Runtime download failed: HTTP ' + response.status);
    const chunks = [];
    for await (const chunk of response.body) chunks.push(chunk);
    writeFileSync(archive + '.part', Buffer.concat(chunks), {mode: 0o600});
    renameSync(archive + '.part', archive);
  }
  const {execFileSync} = await import('node:child_process');
  execFileSync('tar', ['xjf', archive, '-C', ROOT], {stdio: 'inherit'});
  // The archive unpacks into its own directory; normalise it to runtime/.
  const unpacked = path.join(ROOT, path.basename(url).replace(/\.tar\.bz2$/, ''));
  if (!existsSync(RUNTIME) && existsSync(unpacked)) renameSync(unpacked, RUNTIME);
  return runtimeReady();
}

const [command, ...rest] = process.argv.slice(2);
const flag = (name, fallback) => {
  const index = rest.indexOf('--' + name);
  return index >= 0 && rest[index + 1] ? rest[index + 1] : fallback;
};
const positional = rest.filter((value, index) => !value.startsWith('--') && !rest[index - 1]?.startsWith('--'));

try {
  if (command === 'status') process.stdout.write(JSON.stringify(status(), null, 2) + '\n');
  else if (command === 'install-runtime') {
    const ok = await installRuntime(flag('url', runtimeUrl));
    process.stdout.write(JSON.stringify({runtime: {ready: ok, path: RUNTIME}}) + '\n');
  } else if (command === 'prepare') {
    const model = positional[0];
    if (!CATALOG[model]) throw new Error('Unknown model: ' + model);
    process.stdout.write(JSON.stringify(await prepareModel(model)) + '\n');
  } else if (command === 'transcribe') {
    const file = positional[0];
    if (!file) throw new Error('Audio file required');
    const result = await transcribeFile(path.resolve(file), {model: flag('model', 'sensevoice-small'), language: flag('language', 'zh')});
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  } else { usage(); process.exit(command ? 1 : 0); }
} catch (error) {
  process.stderr.write('speech: ' + (error.message ?? error) + '\n');
  process.exit(1);
}
