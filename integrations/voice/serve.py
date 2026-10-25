"""Authenticated phone-local character synthesis using the prepared native engine."""
import argparse
import array
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import resource
import secrets
import signal
import struct
import subprocess
import tempfile
import threading
import time
import wave

ENGINE = "lumendsh.silver_wolf.local"
active_lock = threading.Lock()
state_lock = threading.Lock()
cancelled = {}
seen = {}


def pcm_wave(path):
    raw = path.read_bytes()
    if len(raw) > 40 * 1024 * 1024 or raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise RuntimeError("Invalid synthesis waveform")
    chunks = {}; offset = 12
    while offset + 8 <= len(raw):
        name, size = struct.unpack_from("<4sI", raw, offset)
        chunks[name] = raw[offset + 8:offset + 8 + size]
        offset += 8 + size + size % 2
    fmt = chunks[b"fmt "]; tag, channels, rate = struct.unpack_from("<HHI", fmt)
    bits = struct.unpack_from("<H", fmt, 14)[0]
    if channels == 1 and rate == 44100 and tag == 1 and bits == 16:
        if not chunks.get(b"data") or len(chunks[b"data"]) % 2:
            raise RuntimeError("Invalid PCM waveform")
        return raw
    if channels != 1 or rate != 32000 or bits != 32 or not (tag == 3 or
            (tag == 65534 and len(fmt) >= 40 and struct.unpack_from("<I", fmt, 24)[0] == 3)):
        raise RuntimeError("Unexpected waveform format")
    import math
    samples = array.array("f"); samples.frombytes(chunks[b"data"])
    if not samples or not all(math.isfinite(x) for x in samples):
        raise RuntimeError("Empty or nonfinite waveform")
    pcm = array.array("h", (int(max(-1, min(1, x)) * 32767) for x in samples))
    out = io.BytesIO()
    with wave.open(out, "wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(32000); wav.writeframes(pcm.tobytes())
    return out.getvalue()


def run_stage(command, env, limit, job, directory, deadline=None, bounded_rss=False):
    # Limits are applied by a fresh Python launcher, not preexec_fn in this threaded server.
    launcher = [os.sys.executable, str(Path(__file__).resolve()), "--child-limit", str(limit), "--"]
    with (directory / "engine.log").open("wb") as log:
        child = subprocess.Popen(launcher + command, env=env, stdout=log, stderr=subprocess.STDOUT)
        started = time.monotonic()
        try:
            while child.poll() is None:
                with state_lock:
                    stopped = job in cancelled
                if stopped or time.monotonic() - started > 300 or (deadline and time.monotonic() > deadline):
                    child.kill(); child.wait()
                    raise RuntimeError("Synthesis cancelled" if stopped else "Synthesis timed out")
                if bounded_rss:
                    try:
                        status = Path('/proc/%d/status' % child.pid).read_text()
                        resident = next(int(x.split()[1]) * 1024 for x in status.splitlines() if x.startswith('VmRSS:'))
                        available = next(int(x.split()[1]) * 1024 for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:'))
                        if resident > 700 * 1024**2 or available < 768 * 1024**2:
                            child.kill(); child.wait()
                            raise RuntimeError('Character synthesis exceeded memory budget')
                    except (FileNotFoundError, StopIteration):
                        pass
                time.sleep(0.05)
            if child.returncode:
                raise RuntimeError("Native synthesis failed (exit %d)" % child.returncode)
        finally:
            if child.poll() is None:
                child.kill(); child.wait()


def synthesize(text, job, selected=None):
    selected = selected or backend
    models = root / ('hoyo-models' if selected == 'hoyotts' else 'models')
    if selected == 'hoyotts':
        return synthesize_hoyo(text, job, models)
    available = next(int(x.split()[1]) * 1024 for x in Path("/proc/meminfo").read_text().splitlines()
                     if x.startswith("MemAvailable:"))
    limit = min(2 * 1024**3, available - 1024**3)
    if limit < 1536 * 1024**2:
        raise RuntimeError("Insufficient available memory for character voice")
    with tempfile.TemporaryDirectory(prefix="request-", dir=root / "requests") as temp:
        directory = Path(temp); prepared = directory / "text.json"; output = directory / "audio.wav"
        env = os.environ.copy()
        env.update(ORT_DYLIB_PATH=str(root / "libonnxruntime.so"), LUMENDSH_LOW_MEMORY="1")
        env.pop("LUMENDSH_PREPARED_TEXT", None)
        base = [str(root / "gpt_sovits_demo"), "--model-path", str(models), "--text", text]
        run_stage(base + ["--prepare-text", str(prepared)], env, limit, job, directory)
        env["LUMENDSH_PREPARED_TEXT"] = str(prepared)
        run_stage(base + ["--reference-cache", str(models / "vits.reference.json"),
                         "--output", str(output)], env, limit, job, directory)
        return pcm_wave(output)


def synthesize_hoyo(text, job, models):
    import re
    if re.search(r'[A-Za-z]', text):
        raise RuntimeError('This character voice supports Chinese; select system speech for English')
    available = next(int(x.split()[1]) * 1024 for x in Path('/proc/meminfo').read_text().splitlines()
                     if x.startswith('MemAvailable:'))
    if available < (700 + 768) * 1024**2:
        raise RuntimeError('Insufficient available memory for character voice')
    deadline = time.monotonic() + 300
    with tempfile.TemporaryDirectory(prefix='request-', dir=root / 'requests') as temp:
        directory = Path(temp)
        text_file = directory / 'input.txt'; text_file.write_text(text, encoding='utf-8')
        rows = directory / 'rows.json'; output = directory / 'audio.wav'
        env = os.environ.copy()
        env.update(ORT_DYLIB_PATH=str(root / 'libonnxruntime.so'), PYTHONUTF8='1')
        commands = [
            [os.sys.executable, str(root / 'hoyo-frontend.py'), '--frontend', str(root / 'hoyo-frontend'),
             '--dependencies', str(root / 'hoyo-deps'), '--text-file', str(text_file), '--output', str(rows)],
            [str(root / 'hoyo_native'), 'prepare', str(models), str(rows), str(directory / 'ready')],
            [str(root / 'hoyo_native'), 'synthesize', str(models), str(rows), str(output)]
        ]
        for command in commands:
            run_stage(command, env, 2 * 1024**3, job, directory, deadline=deadline, bounded_rss=True)
        return pcm_wave(output)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Never persist submitted text, credentials, or request URLs.

    def reply(self, status, value, content_type="application/json"):
        data = value if isinstance(value, bytes) else json.dumps(value).encode()
        self.send_response(status); self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data))); self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try: self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError): pass

    def authorized(self):
        if self.headers.get("Origin") or not hmac.compare_digest(
                self.headers.get("Authorization", ""), "Bearer " + token):
            self.reply(401, {"error": "Unauthorized"}); return False
        return True

    def do_GET(self):
        from urllib.parse import urlsplit, parse_qs
        if not self.authorized(): return
        path = urlsplit(self.path)
        available = []
        if (root/'hoyo-models/hoyotts.onnx').is_file() and (root/'hoyo_native').is_file(): available.append('hoyotts')
        if (root/'models/vits.reference.json').is_file() and (root/'gpt_sovits_demo').is_file(): available.append('gpt-sovits')
        if path.path == '/v1/models':
            self.reply(200, {'object':'list','data':[{'id':m,'object':'model','owned_by':'phone-local'} for m in available]})
        elif path.path == '/v1/audio/voices':
            selected = parse_qs(path.query).get('model', [''])[0]
            if selected and selected not in available: self.reply(404, {'error':'Unknown model'}); return
            self.reply(200, {'voices':[{'id':'silver-wolf','name':'银狼','language':'zh','model':m} for m in available if not selected or selected==m], 'cancellation':True})
        else:
            self.reply(200 if path.path == '/health' else 404,
                       {'engine':ENGINE,'version':2,'state':'ready','backend':backend})

    def job_id(self):
        import re
        match = re.fullmatch(r"/(?:synthesize|v1/audio/speech)/([a-zA-Z0-9-]{1,100})", self.path)
        return match.group(1) if match else None

    def do_DELETE(self):
        if not self.authorized(): return
        job = self.job_id()
        if not job: self.reply(404, {"error": "Unknown request"}); return
        with state_lock:
            cancelled[job] = time.monotonic()
        self.reply(200, {"cancelled": True})

    def do_POST(self):
        if not self.authorized(): return
        job = self.job_id()
        standard = self.path == '/v1/audio/speech'
        if standard:
            import re
            job = self.headers.get('X-Request-ID', secrets.token_hex(16))
            if not re.fullmatch(r'[a-zA-Z0-9-]{1,100}', job): job = None
        selected = backend
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not job or not 0 < size <= 8192: raise ValueError()
            value = json.loads(self.rfile.read(size))
            text = value['input' if standard else 'text']
            if standard:
                selected = value.get('model')
                if selected not in ('hoyotts','gpt-sovits') or value.get('voice') != 'silver-wolf':
                    self.reply(404, {'error':'Unknown model or voice'}); return
                if value.get('response_format','wav') != 'wav' or value.get('speed',1) != 1:
                    self.reply(400, {'error':'Local provider requires WAV and speed 1; adjust playback rate in the client'}); return
            if (not standard and set(value) != {'text'}) or not isinstance(text, str) or not text.strip() or len(text) > 1000:
                raise ValueError()
        except (ValueError, KeyError, TypeError):
            self.reply(400, {"error": "Expected 1..1000 characters"}); return
        with state_lock:
            now = time.monotonic()
            for records in (seen, cancelled):
                for key, stamp in list(records.items()):
                    if now - stamp > 900: del records[key]
            if job in seen or job in cancelled:
                self.reply(409, {"error": "Request already used or cancelled"}); return
            seen[job] = now
        if not active_lock.acquire(blocking=False):
            self.reply(409, {"error": "Character voice is busy"}); return
        try:
            self.reply(200, synthesize(text, job, selected), "audio/wav")
        except Exception as error:
            self.reply(503, {"error": str(error) if isinstance(error, RuntimeError) else "Synthesis failed"})
        finally:
            active_lock.release()


if __name__ == "__main__":
    if len(os.sys.argv) > 1 and os.sys.argv[1] == "--child-limit":
        limit = int(os.sys.argv[2]); command = os.sys.argv[4:]
        resource.setrlimit(resource.RLIMIT_AS, (limit, limit))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        os.execvpe(command[0], command, os.environ)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument('--backend', choices=['gpt-sovits', 'hoyotts'], default='gpt-sovits')
    parser.add_argument('--port', type=int, default=3086)
    args = parser.parse_args(); root = args.root.resolve(); models = args.models.resolve(); backend = args.backend
    os.umask(0o077)
    (root / "requests").mkdir(exist_ok=True)
    key = root / "token"
    if not key.exists(): key.write_text(secrets.token_urlsafe(32))
    token = key.read_text().strip()
    if len(token) < 40: raise SystemExit("Invalid local pairing credential")
    files = ((root / 'hoyo_native', root / 'hoyo-frontend.py', root / 'hoyo-frontend/text/chinese.py',
              root / 'hoyo-deps/jieba/__init__.py', root / 'hoyo-deps/pypinyin/__init__.py',
              root / 'hoyo-deps/cn2an/__init__.py', models / 'hoyotts.onnx', models / 'bert.onnx', models / 'tokenizer.json')
             if backend == 'hoyotts' else (root / 'gpt_sovits_demo', models / 'vits.reference.json'))
    for file in (*files, root / 'libonnxruntime.so'):
        if not file.is_file(): raise SystemExit("Missing voice runtime asset")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
