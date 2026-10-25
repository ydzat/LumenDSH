"""Pair through authorized ADB without printing or retaining the on-screen token on the host."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--endpoint', required=True)
args = parser.parse_args()
adb = [args.adb, '-s', args.endpoint]
dump = '/data/local/tmp/sliver-wolf-pairing.xml'

def run(*command, **kwargs):
    return subprocess.check_output(adb + list(command), **kwargs)

def nodes():
    run('shell', 'uiautomator', 'dump', dump)
    return ET.fromstring(run('shell', 'cat', dump)).iter('node')

def tap(label):
    node = next(n for n in nodes() if n.get('text', '').casefold() == label.casefold())
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

try:
    tap('Device tools')
    tap('Pair Termux')
    token = next(n.get('text') for n in nodes()
                 if n.get('package') == 'local.sliverwolf.assistant'
                 and re.fullmatch(r'[A-Za-z0-9+/]{43}=', n.get('text', '')))
    command = ('run-as com.termux /data/data/com.termux/files/usr/bin/bash -c '
               "'umask 077; mkdir -p /data/data/com.termux/files/home/sliver-wolf-build/private; "
               "cat > /data/data/com.termux/files/home/sliver-wolf-build/private/device-token'")
    run('shell', command, input=token.encode())
    tap('Done')
    print('Pairing credential stored in private Termux build directory; token not printed.')
finally:
    run('shell', 'rm', '-f', dump)
