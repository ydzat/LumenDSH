// Host side of the local speech module.
//
// The app owns realtime recognition (it already ships the sherpa-onnx JNI
// runtime and the CAM++ speaker model). This plugin owns the pieces that should
// exist once and be shared:
//   · the speech runtime and model files under ~/.local/share/sliver-wolf/speech
//   · a loopback route the app uses to fetch a verified model
//   · file transcription for the agent
//   · (later) dictionary sync and the training-service client
import {createReadStream, existsSync, readdirSync, statSync} from 'node:fs';
import path from 'node:path';
import {CATALOG, ROOT, modelDir, modelReady, prepareModel, runtimeReady, status, transcribeFile} from './engine.mjs';

export const name = 'sliver-wolf-speech';
// cordis defers apply() until these services exist; accessing webServer at apply
// time without declaring it fails the plugin (as the first restart proved).
export const inject = ['webServer', 'connection', 'tools'];

export function apply(ctx, config = {}) {
  const prefix = '/sliver-wolf/speech';
  const reply = (res, code, value) => {
    res.writeHead(code, {'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store'});
    res.end(JSON.stringify(value));
  };

  ctx.effect(() => ctx.webServer.register({kind: 'prefix', path: prefix, handler: async (req, res) => {
    const verdict = ctx.connection?.admit?.(req);
    if (verdict && (verdict.rejection !== undefined || verdict.peer === undefined)) {
      return reply(res, verdict.rejection ?? 503, {error: 'Authentication required'});
    }
    const url = new URL(req.url, 'http://127.0.0.1');
    const route = url.pathname;
    try {
      if (req.method === 'GET' && route === prefix + '/status') return reply(res, 200, status());

      // The app downloads a verified model file through this route, so it never
      // needs a second copy of the download/verification logic.
      const fileMatch = route.match(new RegExp('^' + prefix + '/models/([a-z0-9-]+)/([A-Za-z0-9._-]+)$'));
      if (req.method === 'GET' && fileMatch) {
        const [, model, file] = fileMatch;
        const dir = modelDir(model);
        const target = path.resolve(dir, file);
        if (!target.startsWith(path.resolve(dir) + path.sep) || !existsSync(target)) {
          return reply(res, 404, {error: 'Model file not found'});
        }
        const info = statSync(target);
        res.writeHead(200, {'content-type': 'application/octet-stream',
          'content-length': info.size, 'cache-control': 'no-store'});
        createReadStream(target).pipe(res);
        return;
      }

      if (req.method === 'POST' && route === prefix + '/transcribe') {
        const chunks = [];
        for await (const chunk of req) chunks.push(chunk);
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
        const file = String(body?.file ?? '');
        if (!file || !existsSync(file)) return reply(res, 400, {error: 'Audio file required'});
        return reply(res, 200, await transcribeFile(file, {model: String(body?.model ?? 'sensevoice-small')}));
      }

      if (req.method === 'POST' && route === prefix + '/prepare') {
        const chunks = [];
        for await (const chunk of req) chunks.push(chunk);
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
        const model = String(body?.model ?? 'sensevoice-small');
        if (!CATALOG[model]) return reply(res, 400, {error: 'Unknown model'});
        return reply(res, 200, await prepareModel(model));
      }

      return reply(res, 404, {error: 'Unknown operation'});
    } catch (error) {
      return reply(res, 500, {error: String(error.message ?? error)});
    }
  }}));

  ctx.tools.register({
    name: 'speech_transcribe',
    description: 'Transcribe a local 16 kHz mono WAV file with the on-device speech engine. Returns the text and elapsed milliseconds.',
    parameters: {
      type: 'object',
      additionalProperties: false,
      required: ['file'],
      properties: {
        file: {type: 'string', description: 'Absolute path to a 16 kHz mono WAV file'},
        model: {type: 'string', enum: Object.keys(CATALOG), description: 'Recognizer to use'},
        language: {type: 'string', description: 'SenseVoice language hint (zh, en, ja, ko, yue)'}
      }
    },
    output: {
      schema: {type: 'object', additionalProperties: false, required: ['text'],
        properties: {text: {type: 'string'}, ms: {type: 'number'}, model: {type: 'string'}}},
      render: (_args, value) => [{type: 'text', text: String(value?.text ?? '')}]
    },
    async execute(parameters) {
      const result = await transcribeFile(String(parameters.file), {
        model: String(parameters.model ?? 'sensevoice-small'), language: String(parameters.language ?? 'zh')
      });
      return result;
    }
  });
}

export {CATALOG, ROOT, modelDir, modelReady, prepareModel, runtimeReady, status, transcribeFile};
