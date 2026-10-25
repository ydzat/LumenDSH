"""OpenAI-compatible transport around the pinned CPUFast inference implementation."""
import asyncio
import contextlib
import hmac
import io
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import uuid
import wave

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response, JSONResponse
from pydantic import BaseModel, Field

from gpt_sovits_cpufast.runtime import configure_runtime
configure_runtime()
from GPT_SoVITS.TTS_infer_pack.TTS import TTS, TTS_Config

config = json.loads(Path(os.environ['LUMENDSH_VOICE_CONFIG']).read_text())
token = Path(config['token_file']).read_text().strip()
pipeline = TTS(TTS_Config(config['tts_config']))
lock = threading.Lock()
state_lock = threading.Lock()
jobs = {}
active_job = None
app = FastAPI(docs_url=None, redoc_url=None)


@app.middleware('http')
async def authenticate(request, call_next):
    if not hmac.compare_digest(request.headers.get('authorization', ''), 'Bearer '+token):
        return JSONResponse({'error': {'message': 'Invalid speech provider credential'}}, status_code=401)
    return await call_next(request)


@app.get('/v1/models')
def models():
    return {'object': 'list', 'data': [dict(id=m, object='model', created=0,
        owned_by='self-hosted', capabilities=['speech']) for m in config['models']]}


@app.get('/v1/audio/voices')
def voices(model: str = ''):
    if model and model not in config['models']:
        raise HTTPException(404, 'Unknown model')
    return {'voices': [dict(id=key, name=value['name'], language=value.get('language', 'zh'),
        model=m) for m, details in config['models'].items() if not model or model == m
        for key, value in details['voices'].items()], 'cancellation': True}


class Speech(BaseModel):
    model: str
    input: str = Field(min_length=1, max_length=1000)
    voice: str
    response_format: str = 'mp3'
    speed: float = Field(default=1.0, ge=0.5, le=1.5)


def generate(body, job, stopped):
    global active_job
    if not lock.acquire(blocking=False):
        raise HTTPException(409, 'Speech provider is busy')
    try:
        with state_lock: active_job = job
        if stopped.is_set(): raise HTTPException(409, 'Speech cancelled')
        model = config['models'][body.model]
        voice = model['voices'][body.voice]
        # All checkpoint changes and inference share one lock; references are server-owned.
        if str(pipeline.configs.t2s_weights_path) != model['gpt_weights']:
            pipeline.init_t2s_weights(model['gpt_weights'])
        if str(pipeline.configs.vits_weights_path) != model['sovits_weights']:
            pipeline.init_vits_weights(model['sovits_weights'])
        args = dict(text=body.input, text_lang=voice.get('language', 'zh'),
            ref_audio_path=voice['reference_audio'], prompt_text=voice['reference_text'],
            prompt_lang=voice.get('language', 'zh'), text_split_method='cut5',
            batch_size=1, seed=-1, speed_factor=body.speed, return_fragment=True,
            streaming_mode=False, parallel_infer=True, vits_parallel_infer=False)
        parts = []; size = 0; rate = 32000
        # Upstream prints submitted text; suppress it instead of persisting conversations.
        with open(os.devnull, 'w') as quiet, contextlib.redirect_stdout(quiet), contextlib.redirect_stderr(quiet):
            for rate, audio in pipeline.run(args):
                if stopped.is_set(): raise HTTPException(409, 'Speech cancelled')
                data = audio.tobytes(); size += len(data)
                if size > 40*1024*1024: raise HTTPException(413, 'Audio is too large')
                parts.append(data)
        if stopped.is_set(): raise HTTPException(409, 'Speech cancelled')
        pcm = b''.join(parts)
        if not pcm: raise HTTPException(503, 'Empty synthesis output')
        output = io.BytesIO()
        with wave.open(output, 'wb') as wav:
            wav.setparams((1, 2, rate, 0, 'NONE', 'not compressed')); wav.writeframes(pcm)
        data = output.getvalue()
        if body.response_format == 'wav': return data, 'audio/wav'
        args = ['ffmpeg', '-v', 'error', '-i', 'pipe:0']
        args += ['-f', 's16le', '-ar', '24000', '-ac', '1'] if body.response_format == 'pcm' else ['-f', 'mp3', '-b:a', '96k']
        result = subprocess.run(args+['pipe:1'], input=data, capture_output=True, timeout=30, check=True)
        return result.stdout, 'application/octet-stream' if body.response_format == 'pcm' else 'audio/mpeg'
    finally:
        with state_lock: active_job = None
        lock.release()


@app.post('/v1/audio/speech')
async def speech(body: Speech, request: Request):
    if not body.input.strip() or body.response_format not in ('wav', 'mp3', 'pcm'):
        raise HTTPException(400, 'Unsupported input or response_format')
    if body.model not in config['models'] or body.voice not in config['models'][body.model]['voices']:
        raise HTTPException(404, 'Unknown model or voice')
    job = request.headers.get('x-request-id', str(uuid.uuid4()))
    if not re.fullmatch(r'[A-Za-z0-9-]{1,100}', job): raise HTTPException(400, 'Invalid request ID')
    stopped = threading.Event()
    with state_lock:
        now = time.monotonic()
        for key, value in list(jobs.items()):
            if value[1] and now-value[1] > 900: del jobs[key]
        if job in jobs: raise HTTPException(409, 'Request already used or cancelled')
        jobs[job] = (stopped, 0)
    work = asyncio.create_task(asyncio.to_thread(generate, body, job, stopped))
    started = time.monotonic()
    try:
        while not work.done():
            await asyncio.sleep(.1)
            if await request.is_disconnected() or time.monotonic()-started > 600: stopped.set()
            if stopped.is_set():
                with state_lock:
                    if active_job == job: pipeline.stop()
        data, media = await work
        return Response(data, media_type=media, headers={'Cache-Control':'no-store'})
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(503, 'Speech synthesis failed')
    finally:
        with state_lock: jobs[job] = (stopped, time.monotonic())


@app.delete('/v1/audio/speech/{job}')
def cancel(job: str):
    if not re.fullmatch(r'[A-Za-z0-9-]{1,100}', job): raise HTTPException(400, 'Invalid request ID')
    with state_lock:
        stopped, stamp = jobs.get(job, (threading.Event(), time.monotonic()))
        stopped.set(); jobs[job] = (stopped, stamp)
        if active_job == job: pipeline.stop()
    return {'cancelled': True}
