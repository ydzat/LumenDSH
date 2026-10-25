import hashlib, json, shutil, urllib.request
from pathlib import Path
root = Path('/opt/lumendsh-tts-test/cpufast')
revisions = {'XXXXRT/GPT-SoVITS-Pretrained':'0c47645e02a7bc3688d7b263b0042c81e3cd82cd','baicai1145/g2pw':'57112a7c10b49c26a171800cf4af04d32a41d5de'}
groups = {
 'XXXXRT/GPT-SoVITS-Pretrained': [
 'chinese-hubert-base/config.json', 'chinese-hubert-base/preprocessor_config.json',
 'chinese-hubert-base/pytorch_model.bin', 'chinese-roberta-wwm-ext-large/config.json',
 'chinese-roberta-wwm-ext-large/pytorch_model.bin', 'chinese-roberta-wwm-ext-large/tokenizer.json',
 'fast_langdetect/lid.176.bin', 'fast_langdetect/lid.176.ftz', 's1v3.ckpt',
 'sv/pretrained_eres2netv2w24s4ep4.ckpt', 'v2Pro/s2Gv2Pro.pth'],
 'baicai1145/g2pw': ['MONOPHONIC_CHARS.txt', 'POLYPHONIC_CHARS.txt', 'config.py', 'g2pw.pth', 'record.log']}
receipts = []
for repo, names in groups.items():
 meta = json.load(urllib.request.urlopen('https://huggingface.co/api/models/'+repo+'/revision/'+revisions[repo]+'?blobs=true', timeout=60))
 revision = meta['sha']; files = {x['rfilename']:x for x in meta['siblings']}
 for name in names:
  upstream = ('GPT_SoVITS/pretrained_models/' + name) if repo.startswith('XXXXRT') else name
  if upstream not in files:
   upstream = next((key for key in files if key.endswith('/'+name) or key == name), upstream)
  info = files[upstream]
  dest = root / ('GPT_SoVITS/pretrained_models/' if repo.startswith('XXXXRT') else 'GPT_SoVITS/text/G2PWModel/') / name
  dest.parent.mkdir(parents=True, exist_ok=True)
  expected = info.get('lfs', {}).get('sha256')
  if not dest.exists():
   if shutil.disk_usage(root).free < info.get('size', 0) + 3*1024**3: raise RuntimeError('Disk reserve reached')
   print('DOWNLOAD', upstream, flush=True)
   url = 'https://huggingface.co/'+repo+'/resolve/'+revision+'/'+upstream
   with urllib.request.urlopen(url, timeout=120) as response, dest.with_suffix(dest.suffix+'.part').open('wb') as out:
    shutil.copyfileobj(response, out)
   dest.with_suffix(dest.suffix+'.part').rename(dest)
  with dest.open('rb') as source: digest = hashlib.file_digest(source, 'sha256').hexdigest()
  if expected and digest != expected: raise RuntimeError('Hash mismatch: '+name)
  receipts.append(dict(repo=repo, revision=revision, file=upstream, bytes=dest.stat().st_size, sha256=digest, lfs_verified=bool(expected)))
  Path('/opt/lumendsh-tts-test/models.json').write_text(json.dumps(receipts, indent=2))
  print('VERIFIED', name, dest.stat().st_size, flush=True)

# Supplement two dictionaries omitted by the pinned installer.
import hashlib, json, urllib.request
from pathlib import Path
root=Path('/opt/lumendsh-tts-test')
revision='b9328ce8129c4717a70f16a2688542a0197541dd'; receipt=[]
for name in ['bopomofo_to_pinyin_wo_tune_dict.json','char_bopomofo_dict.json']:
 url=f'https://raw.githubusercontent.com/GitYCC/g2pW/{revision}/g2pw/{name}'
 data=urllib.request.urlopen(url).read(); json.loads(data)
 (root/'cpufast/GPT_SoVITS/text/G2PWModel'/name).write_bytes(data)
 receipt.append(dict(url=url,sha256=hashlib.sha256(data).hexdigest()))
(root/'extra-assets.json').write_text(json.dumps(receipt,indent=2))
