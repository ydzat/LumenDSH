"""Prepare the selected private CPUFast provider from the evaluated runtime."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import urllib.parse
import urllib.request

os.umask(0o077)
root=Path('/opt/lumendsh-voice');root.mkdir(exist_ok=True)
runtime=Path('/opt/lumendsh-tts-test')
key=root/'token'
if not key.exists(): key.write_text(secrets.token_urlsafe(32))
voices={'silver-wolf':dict(name='银狼',language='zh',reference_audio=str(root/'silver-wolf.wav'),reference_text=(runtime/'reference-text.txt').read_text().strip())}
shutil.copyfile(runtime/'silver-wolf-reference.wav',root/'silver-wolf.wav')
repo='WLWolf56/gpt-sovits-model';revision='8ca20dd1f84feda57396531ef5413130a5d9cc60'
meta=json.load(urllib.request.urlopen(f'https://huggingface.co/api/models/{repo}/revision/{revision}?blobs=true'))
receipts=[]
for voice,name in [('firefly','流萤'),('robin','知更鸟')]:
 row=next(x for x in meta['siblings'] if x['rfilename'].startswith('动漫/sr/audio/'+name+'-') and x['rfilename'].endswith('.wav'))
 source=row['rfilename'];url=f'https://huggingface.co/{repo}/resolve/{revision}/'+urllib.parse.quote(source)
 data=urllib.request.urlopen(url,timeout=60).read();digest=hashlib.sha256(data).hexdigest()
 expected=row.get('lfs',{}).get('sha256')
 if expected and digest!=expected: raise RuntimeError('Reference hash mismatch')
 path=root/(voice+'.wav');path.write_bytes(data)
 voices[voice]=dict(name=name,language='zh',reference_audio=str(path),reference_text=Path(source).stem.split('-',1)[1])
 receipts.append(dict(url=url,sha256=digest))
(root/'reference-provenance.json').write_text(json.dumps(receipts,ensure_ascii=False,indent=2))
config=dict(token_file=str(key),tts_config=str(runtime/'tts.yaml'),models={
 'gpt-sovits-v2pro':dict(gpt_weights='GPT_SoVITS/pretrained_models/s1v3.ckpt',
 sovits_weights='GPT_SoVITS/pretrained_models/v2Pro/s2Gv2Pro.pth',voices=voices)})
(root/'provider.json').write_text(json.dumps(config,ensure_ascii=False,indent=2))
print('REMOTE_PROVIDER_CONFIGURED',len(voices),'voices; credential not printed')
