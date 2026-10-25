"""Precompute reference-only ONNX nodes on the preparation computer."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import helper


def specialize(source: Path, cache: Path, destination: Path):
    if destination.exists():
        raise FileExistsError(destination)
    envelope = json.loads(cache.read_text(encoding="utf-8"))
    if envelope["schemaVersion"] != 1:
        raise ValueError("Unsupported reference cache")
    reference = envelope["reference"]
    fixed = {}
    for name, field in [("ref_audio", "ref_audio_32k"), ("sv_emb", "sv_emb")]:
        tensor = reference[field]
        fixed[name] = np.asarray(tensor["data"], dtype=np.float32).reshape(tensor["dim"])

    model = onnx.load(source)
    for value in model.graph.input:
        if value.name in fixed:
            value.CopyFrom(helper.make_tensor_value_info(
                value.name, onnx.TensorProto.FLOAT, list(fixed[value.name].shape)))
    model = onnx.shape_inference.infer_shapes(model, data_prop=True)
    graph = model.graph
    inputs = {x.name: x for x in graph.input}
    if set(inputs) != {"text_seq", "pred_semantic", "ref_audio", "sv_emb"}:
        raise ValueError("Expected the pinned v2Pro VITS input contract")
    known = {x.name for x in graph.initializer} | set(fixed)
    dependent = set(fixed)
    static_nodes = set()
    for index, node in enumerate(graph.node):
        names = {x for x in node.input if x}
        if names <= known and not node.op_type.startswith("Random"):
            known.update(node.output)
            if names & dependent:
                dependent.update(node.output)
                static_nodes.add(index)
    frontier = sorted({name for index, node in enumerate(graph.node)
                       if index not in static_nodes for name in node.input
                       if name in dependent})
    if not frontier or set(frontier) & set(fixed):
        raise ValueError("Reference path cannot be fully separated from text synthesis")

    # Retain only the ancestors needed to calculate the reference boundary.
    needed = set(frontier)
    reference_nodes = []
    for node in reversed(graph.node):
        if set(node.output) & needed:
            reference_nodes.append(copy.deepcopy(node))
            needed.update(x for x in node.input if x)
    if needed & {"text_seq", "pred_semantic"}:
        raise ValueError("Reference extraction unexpectedly depends on synthesis text")
    values = {v.name: v for v in list(graph.value_info) + list(graph.output) + list(graph.input)}
    outputs = [copy.deepcopy(values[name]) for name in frontier]
    reference_graph = helper.make_graph(
        list(reversed(reference_nodes)), "reference-preparation",
        [copy.deepcopy(inputs[name]) for name in fixed if name in needed], outputs,
        [copy.deepcopy(x) for x in graph.initializer if x.name in needed],
    )
    preparation = helper.make_model(reference_graph, opset_imports=model.opset_import)
    preparation.ir_version = model.ir_version
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(preparation.SerializeToString(), options,
                                   providers=["CPUExecutionProvider"])
    tensors = session.run(frontier, {name: value for name, value in fixed.items() if name in needed})
    # Some upstream reference outputs have unknown rank. Obtain their concrete
    # types/shapes from this actual fixed-reference execution before validation.
    for value, tensor in zip(preparation.graph.output, tensors):
        value.CopyFrom(helper.make_tensor_value_info(
            value.name, helper.np_dtype_to_tensor_dtype(tensor.dtype), list(tensor.shape)))
        if not np.isfinite(tensor).all():
            raise ValueError(f"Non-finite reference feature: {value.name}")
    onnx.checker.check_model(preparation)
    materialized = dict(zip(frontier, tensors))
    del session, preparation

    needed = {v.name for v in graph.output}
    retained = []
    for node in reversed(graph.node):
        if set(node.output) & needed and not set(node.output) <= set(materialized):
            retained.append(copy.deepcopy(node))
            needed.update(x for x in node.input if x)
    if needed & set(fixed):
        raise ValueError("Raw reference inputs remain in the playback graph")
    frozen = copy.deepcopy(model)
    del frozen.graph.node[:]
    frozen.graph.node.extend(reversed(retained))
    del frozen.graph.input[:]
    frozen.graph.input.extend(copy.deepcopy(inputs[name]) for name in ("text_seq", "pred_semantic"))
    frozen.graph.input.extend(helper.make_tensor_value_info(
        name, helper.np_dtype_to_tensor_dtype(value.dtype), list(value.shape))
        for name, value in materialized.items() if name in needed)
    del frozen.graph.initializer[:]
    frozen.graph.initializer.extend(copy.deepcopy(x) for x in graph.initializer if x.name in needed)
    del frozen.graph.value_info[:]
    onnx.checker.check_model(frozen)
    destination.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(frozen, destination)
    playback_cache = copy.deepcopy(envelope)
    playback_cache["reference"]["ref_audio_32k"] = {"v": 1, "dim": [1, 0], "data": []}
    playback_cache["reference"]["sv_emb"] = None
    playback_cache["reference"]["vits_conditioning"] = {
        name: {"v": 1, "dim": list(value.shape), "data": value.reshape(-1).tolist()}
        for name, value in materialized.items() if name in needed}
    destination.with_suffix(".reference.json").write_text(
        json.dumps(playback_cache, separators=(",", ":")), encoding="utf-8")
    receipt = {
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "referenceSha256": hashlib.sha256(cache.read_bytes()).hexdigest(),
        "outputSha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
        "sourceNodes": len(graph.node), "playbackNodes": len(retained),
        "materializedTensors": {name: list(value.shape) for name, value in materialized.items()},
        "playbackInputs": [v.name for v in frozen.graph.input],
        "parity": "not-yet-verified",
    }
    destination.with_suffix(".receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(receipt))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("cache", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    specialize(args.source, args.cache, args.destination)
