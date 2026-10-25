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


def run_stage(command, env, limit, job, directory):
    # Limits are applied by a fresh Python launcher, not preexec_fn in this threaded server.
    launcher = [os.sys.executable, str(Path(__file__).resolve()), "--child-limit", str(limit), "--"]
    with (directory / "engine.log").open("wb") as log:
        child = subprocess.Popen(launcher + command, env=env, stdout=log, stderr=subprocess.STDOUT)
        started = time.monotonic()
        try:
            while child.poll() is None:
                with state_lock:
                    stopped = job in cancelled
                if stopped or time.monotonic() - started > 300:
                    child.kill(); child.wait()
                    raise RuntimeError("Synthesis cancelled" if stopped else "Synthesis timed out")
                time.sleep(0.05)
            if child.returncode:
                raise RuntimeError("Native synthesis failed (exit %d)" % child.returncode)
        finally:
            if child.poll() is None:
                child.kill(); child.wait()


def synthesize(text, job):
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
        if self.authorized():
            self.reply(200 if self.path == "/health" else 404,
                       {"engine": ENGINE, "version": 1, "state": "ready"})

    def job_id(self):
        import re
        match = re.fullmatch(r"/synthesize/([a-zA-Z0-9-]{1,80})", self.path)
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
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not job or not 0 < size <= 8192: raise ValueError()
            value = json.loads(self.rfile.read(size))
            text = value["text"]
            if set(value) != {"text"} or not isinstance(text, str) or not text.strip() or len(text) > 1000:
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
            self.reply(200, synthesize(text, job), "audio/wav")
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
    args = parser.parse_args(); root = args.root.resolve(); models = args.models.resolve()
    os.umask(0o077)
    (root / "requests").mkdir(exist_ok=True)
    key = root / "token"
    if not key.exists(): key.write_text(secrets.token_urlsafe(32))
    token = key.read_text().strip()
    if len(token) < 40: raise SystemExit("Invalid local pairing credential")
    for file in (root / "gpt_sovits_demo", root / "libonnxruntime.so", models / "vits.reference.json"):
        if not file.is_file(): raise SystemExit("Missing voice runtime asset")
    ThreadingHTTPServer(("127.0.0.1", 3086), Handler).serve_forever()
