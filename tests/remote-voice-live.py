"""Real private provider checks; credentials are read on the server, never printed."""
import concurrent.futures
import io
import json
import os
from pathlib import Path
import time
import urllib.request
import urllib.error
import uuid
import wave

root=Path('/opt/lumendsh-voice')
base=os.environ['VOICE_PROVIDER_URL'].rstrip('/')
token=(root/'token').read_text().strip()
def request(path,body=None,method=None,key=token,job=None):
 headers={'Authorization':'Bearer '+key}
 if body is not None: headers['Content-Type']='application/json'
 if job:headers['X-Request-ID']=job
 req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=180) as r:return r.status,r.read(),r.headers.get('Content-Type')
 except urllib.error.HTTPError as e:return e.code,e.read(),e.headers.get('Content-Type')
def speech(voice='silver-wolf',**extra):
 return dict(model='gpt-sovits-v2pro',input='你好，今天的事情交给我吧。',voice=voice,response_format='wav',**extra)
assert request('/models',key='wrong')[0]==401
models=json.loads(request('/models')[1]);assert models['data'][0]['id']=='gpt-sovits-v2pro'
voices=json.loads(request('/audio/voices?model=gpt-sovits-v2pro')[1]);assert len(voices['voices'])==3
assert request('/audio/voices?model=missing')[0]==404
assert request('/audio/speech',speech('missing'))[0]==404
receipts=[]
for voice in ('silver-wolf','firefly','robin'):
 job=str(uuid.uuid4());start=time.monotonic();status,data,media=request('/audio/speech',speech(voice),job=job)
 assert status==200,(status,data[:200])
 with wave.open(io.BytesIO(data)) as w:
  assert w.getnchannels()==1 and w.getsampwidth()==2 and w.getnframes()>0
  duration=w.getnframes()/w.getframerate()
 (root/(voice+'-check.wav')).write_bytes(data)
 receipts.append(dict(voice=voice,seconds=time.monotonic()-start,audioSeconds=duration))
 assert request('/audio/speech',speech(voice),job=job)[0]==409
job=str(uuid.uuid4());assert request('/audio/speech/'+job,method='DELETE')[0]==200
assert request('/audio/speech',speech(),job=job)[0]==409
for format in ('mp3','pcm'):
 body=speech();body['response_format']=format
 status,data,media=request('/audio/speech',body);assert status==200 and len(data)>100
 if format=='mp3':assert data.startswith(b'ID3') or data[0]==255
 else:assert len(data)%2==0
receipts.append(dict(authentication=True,catalog=True,unknownSelectionRejected=True,duplicateRejected=True,cancelBeforeSubmit=True,formats=['wav','mp3','pcm']))
(root/'checks.json').write_text(json.dumps(receipts,indent=2));print(json.dumps(receipts,indent=2))
