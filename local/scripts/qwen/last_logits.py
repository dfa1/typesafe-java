# /// script
# requires-python = ">=3.10"
# dependencies = ["onnx"]
# ///
"""Logits at the last position only: a Slice before lm_head, so a prefill of n tokens allocates one row of logits
(about 600 KB for Qwen2.5's 151936-token vocabulary) instead of n. QwenEngine reads only the last row anyway, so its
answers don't change. Rewrites the graph in place (weights untouched); running it again does nothing.
Run:  uv run scripts/qwen/last_logits.py [dir]   (default ~/.cache/typesafe-local/qwen2.5-1.5b)"""
import glob
import os
import sys

import onnx
from onnx import helper

d = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.cache/typesafe-local/qwen2.5-1.5b")
graphs = glob.glob(f"{d}/onnx/*.onnx") or glob.glob(f"{d}/*.onnx")
if len(graphs) != 1:
    sys.exit(f"expected one .onnx file in {d} or {d}/onnx, found {graphs}")
path = graphs[0]
model = onnx.load(path, load_external_data=False)
g = model.graph
producer = {o: n for n in g.node for o in n.output}

head = producer["logits"]
while head.op_type == "Cast":  # fp16 graphs cast lm_head's output to fp32
    head = producer[head.input[0]]
if head.op_type not in ("MatMul", "MatMulNBits", "Gemm"):
    sys.exit(f"logits come from {head.op_type} {head.name}, not an lm_head MatMul")
if head.input[0] in producer and producer[head.input[0]].name == "last_position":
    sys.exit(f"{path} already computes the last position only")

for name, value in (("last_position_starts", -1), ("last_position_ends", 2**63 - 1), ("last_position_axes", 1)):
    g.initializer.append(helper.make_tensor(name, onnx.TensorProto.INT64, [1], [value]))
sliced = head.input[0] + "_last"
g.node.insert(list(g.node).index(head), helper.make_node(
    "Slice", [head.input[0], "last_position_starts", "last_position_ends", "last_position_axes"], [sliced],
    name="last_position"))
head.input[0] = sliced

tmp = path + ".tmp"
onnx.save(model, tmp)  # external data, if any, stays where it is: same directory, same file names
os.replace(tmp, path)
print(f"{path}: logits at the last position only")
