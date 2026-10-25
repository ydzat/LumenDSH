"""Compare VITS graphs using tensors captured from a real synthesis request."""
import argparse
import gc
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import helper


def array(value, dtype):
    return np.asarray(value["data"], dtype=dtype).reshape(value["dim"])


def run(model_path, feeds):
    model = onnx.load(model_path)
    for node in model.graph.node:
        if node.op_type.startswith("Random"):
            retained = [x for x in node.attribute if x.name != "seed"]
            del node.attribute[:]
            node.attribute.extend(retained)
            node.attribute.append(helper.make_attribute("seed", 1234.0))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(model.SerializeToString(), options,
                                   providers=["CPUExecutionProvider"])
    result = session.run(None, feeds)[0]
    del session, model
    gc.collect()
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("frozen", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("capture", type=Path)
    parser.add_argument("receipt", type=Path)
    args = parser.parse_args()
    reference = json.loads(args.reference.read_text(encoding="utf-8"))["reference"]
    capture = json.loads(args.capture.read_text(encoding="utf-8"))
    feeds = {name: array(capture[name], np.int64) for name in ("text_seq", "pred_semantic")}
    baseline = run(args.source, dict(feeds, ref_audio=array(reference["ref_audio_32k"], np.float32),
                                    sv_emb=array(reference["sv_emb"], np.float32)))
    voice = json.loads(args.frozen.with_suffix(".reference.json").read_text(encoding="utf-8"))
    conditioning = voice["reference"].get("vits_conditioning", {})
    frozen = run(args.frozen, dict(feeds, **{name: array(value, np.float32)
                                           for name, value in conditioning.items()}))
    assert baseline.shape == frozen.shape, "Output shape changed"
    assert np.isfinite(baseline).all() and np.isfinite(frozen).all(), "Non-finite audio"
    np.testing.assert_allclose(frozen, baseline, rtol=1e-4, atol=1e-5)
    receipt = {"status": "passed", "shape": list(frozen.shape),
               "semanticShape": list(feeds["pred_semantic"].shape),
               "maxAbsoluteError": float(np.max(np.abs(frozen - baseline))),
               "rmsError": float(np.sqrt(np.mean((frozen - baseline) ** 2))),
               "noiseSeed": 1234, "rtol": 1e-4, "atol": 1e-5}
    args.receipt.write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(receipt))
