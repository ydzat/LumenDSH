import concurrent.futures
import io
import json
from pathlib import Path
import threading
import time
import urllib.request
import urllib.error
import uuid
import wave

root=Path.home()/'lumendsh-hoyo-candidate'
token=(root/'token').read_text().strip()
base='http://127.0.0.1:3089'
records=[]
def request(method,path,data=None,auth=True):
    headers={'Authorization':'Bearer '+token} if auth else {}
    req=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=320) as response:return response.status,response.read()
    except urllib.error.HTTPError as e:return e.code,e.read()
def synth(label,text):
    job='verify-'+uuid.uuid4().hex;started=time.monotonic();status,data=request('POST','/synthesize/'+job,{'text':text})
    assert status==200,(label,status,data)
    with wave.open(io.BytesIO(data),'rb') as wav:
        assert wav.getnchannels()==1 and wav.getsampwidth()==2 and wav.getframerate()==44100
        duration=wav.getnframes()/wav.getframerate()
        assert duration>0 and any(wav.readframes(wav.getnframes()))
    (root/(label+'.wav')).write_bytes(data)
    r={'case':label,'elapsedSeconds':time.monotonic()-started,'audioSeconds':duration,'status':status};records.append(r);print(json.dumps(r),flush=True)
    assert request('POST','/synthesize/'+job,{'text':text})[0]==409
    return job
try:
    assert request('GET','/health',auth=False)[0]==401
    synth('short','你好，这里是银狼。')
    synth('long','这个任务交给我。今天先把最重要的工作安排好，再检查有没有遗漏的地方。遇到复杂问题不用着急，我们可以把它分成几个小步骤，一步一步解决。现在的语音合成会按句子处理，避免一句话太长时占用过多内存。等你准备好了，就告诉我下一步需要做什么。')
    job='cancel-'+uuid.uuid4().hex
    with concurrent.futures.ThreadPoolExecutor() as pool:
        future=pool.submit(request,'POST','/synthesize/'+job,{'text':'现在开始检查语音取消功能。'*30})
        time.sleep(2)
        assert request('POST','/synthesize/busy-'+uuid.uuid4().hex,{'text':'你好。'})[0]==409
        t=time.monotonic();assert request('DELETE','/synthesize/'+job)[0]==200
        status,data=future.result(timeout=5)
        assert status==503 and 'cancelled' in data.decode(),(status,data)
        records.append({'case':'cancel','seconds':time.monotonic()-t,'status':status})
    synth('after-cancel','取消成功，现在可以继续说话了。')
    assert not list((root/'requests').iterdir()),'Temporary requests remain'
finally:
    result={'cases':records}
    (root/'integration-receipt.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result),flush=True)
