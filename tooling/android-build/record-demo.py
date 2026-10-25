"""Record a short, silent Android screen demo into local ignored artifacts."""
import argparse
from datetime import datetime
from pathlib import Path
import re
import os
import subprocess
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default=r'D:\Programs\MuMuPlayer\nx_main\adb.exe')
parser.add_argument('--endpoint', required=True)
parser.add_argument('--label', required=True)
parser.add_argument('--scrcpy', help='Path to a verified portable scrcpy executable (for devices without screenrecord)')
parser.add_argument('--seconds', type=int, default=8, choices=range(5, 16))
args = parser.parse_args()
if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,60}', args.label):
    parser.error('label must contain only lowercase letters, digits, and hyphens')
root = Path(__file__).resolve().parents[2]
destination = root / 'artifacts' / 'demos'
destination.mkdir(parents=True, exist_ok=True)
name = f'{datetime.now():%Y%m%d-%H%M%S}-{args.label}-{uuid.uuid4().hex[:8]}.mp4'
target = destination / name
remote = f'/data/local/tmp/sliver-wolf-{uuid.uuid4().hex}.mp4'
base = [args.adb, '-s', args.endpoint]
subprocess.run(base + ['shell', 'echo', 'DEMO_ADB_OK'], check=True)
print('Recording actual screen without audio. Use only inspected test content.', flush=True)
try:
    if args.scrcpy:
        environment = dict(os.environ, ADB=args.adb)
        subprocess.run([args.scrcpy, '--serial=' + args.endpoint, '--no-audio', '--no-control',
                        '--no-playback', '--no-window', '--max-size=1170', '--max-fps=20',
                        '--time-limit=' + str(args.seconds), '--record=' + str(target)],
                       env=environment, check=True)
    else:
        subprocess.run(base + ['shell', 'screenrecord', '--size', '540x1170',
                              '--bit-rate', '2000000', '--time-limit', str(args.seconds), remote], check=True)
        subprocess.run(base + ['pull', remote, str(target)], check=True)
    if target.stat().st_size < 1024:
        raise RuntimeError('Recording is unexpectedly small; inspect before delivery')
    print(f'LOCAL_DEMO={target}')
finally:
    if not args.scrcpy:
        subprocess.run(base + ['shell', 'rm', '-f', remote], check=True)
