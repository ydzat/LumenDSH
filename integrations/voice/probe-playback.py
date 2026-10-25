"""Run one prepared-model probe in Termux with a bounded address space."""
import argparse
import json
import os
from pathlib import Path
import resource
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("models", type=Path)
parser.add_argument("--low-memory", action="store_true")
parser.add_argument("--staged", action="store_true")
parser.add_argument("--text", default="你好，我是银狼。")
parser.add_argument("--timeout", type=float, default=120)
args = parser.parse_args()
if not args.text.strip() or len(args.text) > 1000 or not 0 < args.timeout <= 120:
    raise SystemExit("Text must contain 1..1000 characters; timeout must be in (0, 120]")
root = Path.home() / "lumendsh-voice-lab"
memory = {
    line.split(":")[0]: int(line.split()[1]) * 1024
    for line in Path("/proc/meminfo").read_text().splitlines()
    if line.startswith(("MemAvailable:", "MemTotal:"))
}
# Reserve 1 GiB of currently available memory for the existing phone services.
limit = min(2 * 1024**3, memory["MemAvailable"] - 1024**3)
if limit < 1024**3:
    raise SystemExit("Insufficient available memory for this isolated probe")


def constrain():
    resource.setrlimit(resource.RLIMIT_AS, (limit, limit))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


env = os.environ.copy()
env["ORT_DYLIB_PATH"] = str(root / "libonnxruntime.so")
env["LD_LIBRARY_PATH"] = str(root) + ":" + env.get("LD_LIBRARY_PATH", "")
if args.low_memory:
    env["LUMENDSH_LOW_MEMORY"] = "1"
output = root / ("prepared-" + str(time.time_ns()) + ".wav")
command = [
    str(root / "gpt_sovits_demo"), "--model-path", str(args.models),
    "--reference-cache", str(args.models / "vits.reference.json"),
    "--text", args.text, "--seed", "1234", "--output", str(output),
]
def run_stage(command, stage_env, label):
    start = time.monotonic()
    peak_rss = 0
    with (root / ("prepared-" + label + ".log")).open("w") as log:
        process = subprocess.Popen(command, env=stage_env, stdout=log, stderr=subprocess.STDOUT,
                                   preexec_fn=constrain)
        stop_reason = None
        while process.poll() is None:
            try:
                counters = Path(f"/proc/{process.pid}/status").read_text()
                rss = next(int(line.split()[1]) * 1024 for line in counters.splitlines()
                           if line.startswith("VmRSS:"))
                peak_rss = max(peak_rss, rss)
            except (FileNotFoundError, ProcessLookupError, StopIteration):
                pass
            if time.monotonic() - start > args.timeout:
                stop_reason = "timeout"
                process.kill()
                break
            time.sleep(0.1)
        return {"stage": label, "exit": process.wait(), "stopReason": stop_reason,
                "seconds": time.monotonic() - start, "sampledPeakRssBytes": peak_rss}


stages = []
prepared = output.with_suffix(".text.json")
try:
    if args.staged:
        stages.append(run_stage([
            str(root / "gpt_sovits_demo"), "--model-path", str(args.models),
            "--text", args.text, "--prepare-text", str(prepared),
        ], env, "text"))
        env["LUMENDSH_PREPARED_TEXT"] = str(prepared)
    if not stages or stages[-1]["exit"] == 0:
        stages.append(run_stage(command, env, "inference"))
finally:
    prepared.unlink(missing_ok=True)
status = {"stages": stages, "addressSpaceLimitBytes": limit, "memoryBefore": memory,
          "lowMemory": args.low_memory, "staged": args.staged,
          "wavExists": output.exists(), "output": str(output)}
(root / "prepared-inference-receipt.json").write_text(json.dumps(status, indent=2))
print(json.dumps(status), flush=True)
