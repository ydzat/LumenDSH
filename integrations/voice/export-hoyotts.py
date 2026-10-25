"""Export the upstream infer method, retaining dynamic sentence lengths."""
import json
from pathlib import Path
import sys
import time
if len(sys.argv)!=2: raise SystemExit('Usage: export-hoyotts.py LAB_DIRECTORY')
ROOT=Path(sys.argv[1]).resolve()
sys.path.insert(0,str(ROOT/'source'))
import numpy as np
import torch
import onnxruntime as ort
import soundfile as sf
from models import SynthesizerTrn
from text import symbols
torch.set_num_threads(4)
cfg=json.loads((ROOT/'model/config.json').read_text(encoding='utf-8'))
net=SynthesizerTrn(len(symbols),cfg['data']['filter_length']//2+1,
    cfg['train']['segment_size']//cfg['data']['hop_length'],
    n_speakers=cfg['data']['n_speakers'],**cfg['model'])
checkpoint=torch.load(ROOT/'model/G_78000.pth',map_location='cpu',weights_only=True)
net.load_state_dict(checkpoint['model'],strict=True)
del checkpoint
net.eval()
class Export(torch.nn.Module):
    def __init__(self,net):
        super().__init__();self.net=net
    def forward(self,phones,lengths,speaker,tones,language,bert,noise,noise_w):
        return self.net.infer(phones,lengths,speaker,tones,language,bert,
            sdp_ratio=.2,noise_scale=noise,noise_scale_w=noise_w,length_scale=1.0)[0]
wrapper=Export(net).eval()
def inputs(i,noise,noise_w):
    d=np.load(ROOT/f'inputs-{i}.npz')
    return dict(phones=d['phones'],lengths=np.array([d['phones'].shape[1]],dtype=np.int64),
        speaker=np.array([221],dtype=np.int64),tones=d['tones'],language=d['language'],
        bert=d['bert'],noise=np.array(noise,dtype=np.float32),noise_w=np.array(noise_w,dtype=np.float32))
sample=inputs(0,.6,.8)
dest=ROOT/'hoyotts.onnx'
with torch.inference_mode():
    torch.onnx.export(wrapper,tuple(torch.from_numpy(x) for x in sample.values()),str(dest),
        input_names=list(sample),output_names=['audio'],dynamo=False,opset_version=17,
        dynamic_axes={'phones':{1:'tokens'},'tones':{1:'tokens'},'language':{1:'tokens'},
            'bert':{2:'tokens'},'audio':{2:'samples'}})
opts=ort.SessionOptions();opts.intra_op_num_threads=4;opts.log_severity_level=3
session=ort.InferenceSession(str(dest),sess_options=opts,providers=['CPUExecutionProvider'])
receipts=[]
with torch.inference_mode():
    for i in range(3):
        feed=inputs(i,0.,0.)
        expected=wrapper(*(torch.from_numpy(x) for x in feed.values())).numpy()
        actual=session.run(None,feed)[0]
        assert actual.shape==expected.shape,(actual.shape,expected.shape)
        error=float(np.max(np.abs(actual-expected)))
        rms=float(np.sqrt(np.mean((actual-expected)**2)))
        assert error<.001,(i,error,rms)
        receipts.append({'segment':i,'shape':list(actual.shape),'maxAbsoluteError':error,'rmsError':rms})
(ROOT/'export-parity.json').write_text(json.dumps(receipts,indent=2))
print('PARITY',receipts,flush=True)
waves=[]
for i in range(3):
    feed=inputs(i,.6,.8)
    start=time.perf_counter();wave=session.run(None,feed)[0].reshape(-1)
    assert np.isfinite(wave).all() and np.max(np.abs(wave))>0
    print('ORT',i,len(wave)/44100,time.perf_counter()-start,flush=True)
    waves.extend(([np.zeros(11025,dtype=np.float32)] if i else [])+[wave])
sf.write(ROOT/'onnx-audition.wav',np.concatenate(waves),44100,subtype='FLOAT')
print('EXPORTED',dest.stat().st_size,flush=True)
