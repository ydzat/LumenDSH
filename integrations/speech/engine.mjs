// Local speech runtime shared by the app (model download) and the host (file
// transcription). No network except the explicit model download.
import {createHash} from 'node:crypto';
import {createReadStream, createWriteStream, existsSync, mkdirSync, renameSync, statSync} from 'node:fs';
import {spawn} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';

export const ROOT = process.env.LUMENDSH_SPEECH_ROOT ||
  path.join(os.homedir(), '.local/share/sliver-wolf/speech');
export const RUNTIME = path.join(ROOT, 'runtime');
export const MODELS = path.join(ROOT, 'models');

// One entry per downloadable model. `files` are verified before use; `kind`
// selects the CLI arguments, not the engine.
export const CATALOG = {
  'sensevoice-small': {
    label: 'SenseVoice-Small (zh/en/ja/ko/yue, punctuation + ITN)',
    kind: 'sensevoice',
    bytes: 230 * 1024 * 1024,
    url: 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2',
    archive: 'tar.bz2',
    directory: 'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17',
    model: 'model.int8.onnx',
    tokens: 'tokens.txt',
    threads: 2,
    sha256: null // filled once the verified archive is measured; see ensure().
  },
  'paraformer-zh-small': {
    label: 'Paraformer-zh small (light, no punctuation)',
    kind: 'paraformer',
    bytes: 78 * 1024 * 1024,
    url: 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2',
    archive: 'tar.bz2',
    directory: 'sherpa-onnx-paraformer-zh-small-2024-03-09',
    model: 'model.int8.onnx',
    tokens: 'tokens.txt',
    threads: 2,
    sha256: null
  },
  'punct-zh-en': {
    label: 'CT-Transformer punctuation (zh + en)',
    kind: 'punctuation',
    bytes: 64 * 1024 * 1024,
    url: 'https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8.tar.bz2',
    archive: 'tar.bz2',
    directory: 'sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8',
    model: 'model.int8.onnx',
    tokens: null,
    threads: 2,
    sha256: null
  }
};

export function dirs() {
  mkdirSync(ROOT, {recursive: true, mode: 0o700});
  mkdirSync(MODELS, {recursive: true, mode: 0o700});
  return {root: ROOT, runtime: RUNTIME, models: MODELS};
}

export function runtimeReady() {
  return existsSync(path.join(RUNTIME, 'bin/sherpa-onnx-offline')) &&
    existsSync(path.join(RUNTIME, 'lib/libsherpa-onnx-c-api.so'));
}

export function modelDir(id) {
  const entry = CATALOG[id];
  if (!entry) throw new Error('Unknown model: ' + id);
  return path.join(MODELS, entry.directory);
}

export function modelReady(id) {
  const entry = CATALOG[id];
  if (!entry) return false;
  const dir = modelDir(id);
  return existsSync(path.join(dir, entry.model)) && (!entry.tokens || existsSync(path.join(dir, entry.tokens)));
}

export function status() {
  return {
    root: ROOT,
    runtime: {ready: runtimeReady(), path: RUNTIME},
    models: Object.fromEntries(Object.entries(CATALOG).map(([id, entry]) => [id, {
      label: entry.label, ready: modelReady(id), bytes: entry.bytes, directory: entry.directory
    }]))
  };
}

export async function sha256File(file) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    createReadStream(file).on('data', (chunk) => hash.update(chunk))
      .on('error', reject).on('end', () => resolve(hash.digest('hex')));
  });
}

function run(bin, args, {env, timeoutMs = 120000} = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(bin, args, {env: {...process.env, ...env}, stdio: ['ignore', 'pipe', 'pipe']});
    let out = '', err = '';
    const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('Transcription timed out')); }, timeoutMs);
    child.stdout.on('data', (chunk) => { out += chunk; });
    child.stderr.on('data', (chunk) => { err += chunk; });
    child.on('error', (error) => { clearTimeout(timer); reject(error); });
    child.on('exit', (code) => {
      clearTimeout(timer);
      if (code !== 0) reject(new Error('sherpa-onnx exited ' + code + ': ' + err.slice(-400)));
      else resolve(out);
    });
  });
}

/** Decode one 16 kHz mono WAV file and return the recognized text. */
export async function transcribeFile(file, {model = 'sensevoice-small', language = 'zh', threads, timeoutMs} = {}) {
  if (!runtimeReady()) throw new Error('Speech runtime is not installed; run tooling/speech.mjs install-runtime');
  if (!modelReady(model)) throw new Error('Model not prepared: ' + model);
  if (!existsSync(file)) throw new Error('Audio file not found: ' + file);
  const entry = CATALOG[model];
  const dir = modelDir(model);
  const args = ['--tokens=' + path.join(dir, entry.tokens), '--num-threads=' + (threads ?? entry.threads ?? 2)];
  if (entry.kind === 'sensevoice') {
    args.push('--sense-voice-model=' + path.join(dir, entry.model), '--sense-voice-language=' + language,
      '--sense-voice-use-itn=true');
  } else {
    args.push('--paraformer=' + path.join(dir, entry.model));
  }
  args.push(file);
  const started = Date.now();
  const out = await run(path.join(RUNTIME, 'bin/sherpa-onnx-offline'), args,
    {env: {LD_LIBRARY_PATH: path.join(RUNTIME, 'lib')}, timeoutMs: timeoutMs ?? 180000});
  const line = out.split('\n').map((x) => x.trim()).filter((x) => x.startsWith('{')).pop();
  if (!line) throw new Error('No transcription result: ' + out.slice(-400));
  const parsed = JSON.parse(line);
  return {text: String(parsed.text ?? '').trim(), ms: Date.now() - started, model};
}

/** Download and verify a model archive, then extract it into models/. */
export async function prepareModel(id, {onProgress = () => {}} = {}) {
  const entry = CATALOG[id];
  if (!entry) throw new Error('Unknown model: ' + id);
  if (modelReady(id)) return {ready: true, model: id, downloaded: false};
  dirs();
  const archive = path.join(ROOT, entry.directory + '.' + entry.archive);
  const part = archive + '.part';
  if (!existsSync(archive)) {
    const response = await fetch(entry.url, {redirect: 'follow'});
    if (!response.ok) throw new Error('Model download failed: HTTP ' + response.status);
    const total = Number(response.headers.get('content-length') ?? 0);
    let received = 0;
    // Stream to disk: the SenseVoice archive is about 1 GB and must never be
    // buffered whole in the phone's heap.
    const out = createWriteStream(part, {mode: 0o600});
    try {
      for await (const chunk of response.body) {
        received += chunk.length;
        if (!out.write(chunk)) await new Promise((resolve) => out.once('drain', resolve));
        onProgress({received, total});
      }
    } finally {
      await new Promise((resolve, reject) => out.end((error) => error ? reject(error) : resolve()));
    }
    if (entry.sha256 && await sha256File(part) !== entry.sha256) {
      throw new Error('Model archive integrity failed: ' + id);
    }
    renameSync(part, archive);
  }
  await run('tar', ['xjf', archive, '-C', MODELS], {timeoutMs: 600000});
  return {ready: modelReady(id), model: id, downloaded: true};
}

/** Add punctuation to text using the CT-Transformer model. */
export async function punctuate(text, {timeoutMs = 60000} = {}) {
  const entry = CATALOG['punct-zh-en'];
  if (!runtimeReady()) throw new Error('Speech runtime is not installed');
  if (!modelReady('punct-zh-en')) throw new Error('Punctuation model not prepared: punct-zh-en');
  const model = path.join(modelDir('punct-zh-en'), entry.model);
  const out = await run(path.join(RUNTIME, 'bin/sherpa-onnx-offline-punctuation'),
    ['--ct-transformer=' + model, '--num-threads=' + (entry.threads ?? 2), String(text)],
    {env: {LD_LIBRARY_PATH: path.join(RUNTIME, 'lib')}, timeoutMs});
  return out.split('\n').map((x) => x.trim()).filter(Boolean).pop() ?? String(text);
}

export function statFile(file) {
  const info = statSync(file);
  return {bytes: info.size, mtime: info.mtimeMs};
}
