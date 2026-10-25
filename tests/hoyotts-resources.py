import importlib.util
import json
from pathlib import Path
import subprocess
import threading
import time
root=Path.home()/'lumendsh-hoyo-candidate'
spec=importlib.util.spec_from_file_location('voice_service',root/'serve.py')
service=importlib.util.module_from_spec(spec);spec.loader.exec_module(service)
service.root=root;service.models=root/'models';service.backend='hoyotts'
original=subprocess.Popen
stages=[];threads=[]
def launch(*args,**kwargs):
    child=original(*args,**kwargs)
    record={'peakRssBytes':0};stages.append(record)
    def monitor():
        while child.poll() is None:
            try:
                status=Path(f'/proc/{child.pid}/status').read_text()
                rss=next(int(x.split()[1])*1024 for x in status.splitlines() if x.startswith('VmRSS:'))
                record['peakRssBytes']=max(record['peakRssBytes'],rss)
            except (OSError,StopIteration):pass
            time.sleep(.025)
    thread=threading.Thread(target=monitor);thread.start();threads.append(thread)
    return child
subprocess.Popen=launch
started=time.monotonic()
data=service.synthesize('你好，今天我们先完成最重要的任务，再检查其他安排。','full-resource-probe')
for thread in threads:thread.join()
assert len(stages)==3 and all(x['peakRssBytes']>0 for x in stages),stages
receipt={'elapsedSeconds':time.monotonic()-started,'stages':stages,'peakChildRssBytes':max(x['peakRssBytes'] for x in stages)}
(root/'resource-receipt.json').write_text(json.dumps(receipt,indent=2))
print(json.dumps(receipt))
