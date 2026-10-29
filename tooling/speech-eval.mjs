#!/usr/bin/env node
// Speech accuracy harness: synthesize the fixed corpus with the local TTS voice,
// decode it with each local model, and report CER plus term accuracy.
//
// Synthesized speech is not the owner's mic, so the absolute CER is optimistic;
// the value of this run is a repeatable, engine-relative baseline. Real recordings
// can be dropped into the corpus directory later with the same JSON shape.
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {CATALOG, transcribeMany} from '../integrations/speech/engine.mjs';

const ROOT = process.env.LUMENDSH_SPEECH_EVAL || path.join(process.env.HOME, '.local/share/sliver-wolf/speech/eval');
const tokenFile = path.join(process.env.HOME, '.local/state/tts-token');
const endpoint = process.env.LUMENDSH_TTS_URL || 'https://dongzeyang.top/lumendsh-voice/v1/audio/speech';
const corpus = JSON.parse(readFileSync(path.resolve('integrations/speech/corpus.json'), 'utf8'));

// Terms whose exact spelling matters to the agent more than ordinary words do.
const TERMS = ['银狼', 'agent/dev', 'LumenDSH', 'ZeroTier', 'GPT-SoVITS', 'magic-context', 'phone_task', 'termux', 'adb', 'Debian', 'github'];

function normalize(text) {
  return text.toLowerCase()
    .replace(/[，。！？、,.!?;；:：\s"'“”‘’()（）\[\]【】-]/g, '')
    .replace(/[０-９]/g, (digit) => String.fromCharCode(digit.charCodeAt(0) - 0xfee0));
}

function editDistance(a, b) {
  const rows = a.length + 1, cols = b.length + 1;
  const dp = Array.from({length: rows}, () => new Array(cols).fill(0));
  for (let i = 0; i < rows; i++) dp[i][0] = i;
  for (let j = 0; j < cols; j++) dp[0][j] = j;
  for (let i = 1; i < rows; i++) {
    for (let j = 1; j < cols; j++) {
      dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
  }
  return dp[rows - 1][cols - 1];
}

function cer(hypothesis, reference) {
  const h = normalize(hypothesis), r = normalize(reference);
  if (!r.length) return {cer: h.length ? 1 : 0, distance: h.length, length: 0};
  const distance = editDistance(h, r);
  return {cer: distance / r.length, distance, length: r.length};
}

async function synthesize(sentence, index) {
  const token = readFileSync(tokenFile, 'utf8').trim();
  const file = path.join(ROOT, 'wav', String(index).padStart(2, '0') + '.wav');
  let lastError;
  // The private TTS service throttles bursts (HTTP 503); wait it out instead of
  // losing the run half way through the corpus.
  for (let attempt = 0; attempt < 8; attempt++) {
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {authorization: 'Bearer ' + token, 'content-type': 'application/json'},
        body: JSON.stringify({model: 'gpt-sovits-v2pro', input: sentence, voice: corpus.voice ?? 'silver-wolf', response_format: 'wav'})
      });
      if (!response.ok) throw new Error('TTS failed: HTTP ' + response.status);
      writeFileSync(file, Buffer.from(await response.arrayBuffer()));
      return file;
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 15000 + 5000 * attempt));
    }
  }
  throw lastError;
}

async function main() {
  const models = process.argv.slice(2).filter((value) => !value.startsWith('--'));
  const selected = models.length ? models : ['sensevoice-small', 'paraformer-zh-small'];
  for (const model of selected) if (!CATALOG[model]) throw new Error('Unknown model: ' + model);
  mkdirSync(path.join(ROOT, 'wav'), {recursive: true});

  const files = [];
  for (let i = 0; i < corpus.sentences.length; i++) {
    const file = path.join(ROOT, 'wav', String(i).padStart(2, '0') + '.wav');
    if (!existsSync(file)) {
      // Space requests out: the private TTS service starts returning 503 when a
      // corpus is synthesized back-to-back.
      if (i > 0) await new Promise((resolve) => setTimeout(resolve, 2500));
      await synthesize(corpus.sentences[i], i);
    }
    files.push(file);
    if (i % 5 === 4) process.stderr.write('synthesized ' + (i + 1) + '/' + corpus.sentences.length + '\n');
  }

  const report = {createdAt: new Date().toISOString(), voice: corpus.voice, sentences: corpus.sentences.length, models: {}};
  for (const model of selected) {
    const started = Date.now();
    const texts = await transcribeMany(files, {model});
    const elapsed = Date.now() - started;
    let distance = 0, length = 0;
    let termHits = 0, termTotal = 0;
    const rows = [];
    corpus.sentences.forEach((reference, index) => {
      const hypothesis = texts[index] ?? '';
      const score = cer(hypothesis, reference);
      distance += score.distance;
      length += score.length;
      for (const term of TERMS) {
        if (!normalize(reference).includes(normalize(term))) continue;
        termTotal++;
        if (normalize(hypothesis).includes(normalize(term))) termHits++;
      }
      rows.push({reference, hypothesis, cer: Number(score.cer.toFixed(3))});
    });
    report.models[model] = {
      cer: Number((distance / Math.max(1, length)).toFixed(4)),
      termAccuracy: Number((termHits / Math.max(1, termTotal)).toFixed(3)),
      termHits, termTotal,
      ms: elapsed,
      rows
    };
    process.stderr.write(model + ': CER ' + report.models[model].cer + ', terms ' + termHits + '/' + termTotal + '\n');
  }
  const file = path.join(ROOT, 'report-' + Date.now() + '.json');
  writeFileSync(file, JSON.stringify(report, null, 1));
  process.stdout.write(JSON.stringify({report: file, summary: Object.fromEntries(Object.entries(report.models).map(([id, value]) => [id, {cer: value.cer, termAccuracy: value.termAccuracy}]))}) + '\n');
}

main().catch((error) => { process.stderr.write('speech-eval: ' + (error.message ?? error) + '\n'); process.exit(1); });
