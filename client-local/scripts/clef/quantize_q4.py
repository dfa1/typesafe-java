# /// script
# requires-python = ">=3.10"
# dependencies = ["onnx", "onnxruntime", "numpy"]
# ///
"""4-bit weights for Clef-flash: Ollaya's fp32 graph with every projection (bf16 W -> Cast -> Transpose -> MatMul)
replaced by ONNX Runtime's MatMulNBits (blocks of 32, with zero points; the layout of
onnxruntime.quantization.matmul_nbits_quantizer). The embedding, lm_head and the small head weights stay bf16 in
Cloudflare's own files, hard-linked (no copy) into the new directory, which ClefEngine loads like the fp32 one.
One weight at a time, so it needs little memory.
Run:  uv run scripts/clef/quantize_q4.py [src] [dst]   (default ~/.cache/typesafe-local/clef-flash{,-q4})"""
import os
import sys

import numpy as np
import onnx
from onnx import TensorProto, helper
from onnxruntime.capi._pybind_state import quantize_matmul_4bits

BLOCK = 32
cache = os.path.expanduser("~/.cache/typesafe-local")
src = sys.argv[1] if len(sys.argv) > 1 else f"{cache}/clef-flash"
dst = sys.argv[2] if len(sys.argv) > 2 else f"{cache}/clef-flash-q4"
os.makedirs(f"{dst}/flash", exist_ok=True)
for f in os.listdir(src):  # tokenizer + safetensors: hard links, no copy
    if (f.endswith(".safetensors") or f == "tokenizer.json") and not os.path.exists(f"{dst}/{f}"):
        os.link(f"{src}/{f}", f"{dst}/{f}")

model = onnx.load(f"{src}/flash/model-fp32.onnx", load_external_data=False)
g = model.graph
init = {t.name: t for t in g.initializer}
producer = {o: n for n in g.node for o in n.output}


def read_bf16(t):
    ext = {kv.key: kv.value for kv in t.external_data}
    with open(f"{src}/flash/{ext['location']}", "rb") as f:
        f.seek(int(ext.get("offset", 0)))
        raw = np.frombuffer(f.read(int(ext["length"])), dtype=np.uint16)
    return (raw.astype(np.uint32) << 16).view(np.float32).reshape(t.dims)  # bf16 is fp32's top half


def weight_of(name):
    """W if name is Transpose(Identity(Cast(W bf16 [N, K]))), else None."""
    chain = []
    for op in ("Transpose", "Identity", "Cast"):
        n = producer.get(name)
        if n is None or n.op_type != op:
            return None
        chain.append(n)
        name = n.input[0]
    t = init.get(name)
    perm = [a.ints for a in chain[0].attribute if a.name == "perm"]
    ok = t is not None and t.data_type == TensorProto.BFLOAT16 and len(t.dims) == 2 and (not perm or list(perm[0]) == [1, 0])
    return t if ok else None


data_name = "model-q4.onnx.data"
data = open(f"{dst}/flash/{data_name}", "wb")


def external(name, array, dtype):
    offset = data.tell()
    data.write(array.tobytes())
    t = TensorProto(name=name, data_type=dtype, dims=array.shape)
    t.data_location = TensorProto.EXTERNAL
    for k, v in (("location", data_name), ("offset", str(offset)), ("length", str(array.nbytes))):
        t.external_data.add(key=k, value=v)
    return t


nodes, done = [], 0
for node in g.node:
    w = weight_of(node.input[1]) if node.op_type == "MatMul" else None
    if w is None:
        nodes.append(node)
        continue
    n_out, k = w.dims
    b = np.ascontiguousarray(read_bf16(w).T)  # [K, N], as MatMul sees it
    k_blocks = (k + BLOCK - 1) // BLOCK
    packed = np.zeros((n_out, k_blocks, BLOCK // 2), dtype=np.uint8)
    zero_points = np.zeros((n_out, (k_blocks + 1) // 2), dtype=np.uint8)
    scales = np.zeros((n_out, k_blocks), dtype=np.float32)
    quantize_matmul_4bits(packed, b, scales, zero_points, BLOCK, n_out, k, False)
    g.initializer.extend([external(w.name + "_q4", packed, TensorProto.UINT8),
                          external(w.name + "_scales", scales, TensorProto.FLOAT),
                          external(w.name + "_zp", zero_points, TensorProto.UINT8)])
    nodes.append(helper.make_node("MatMulNBits", [node.input[0], w.name + "_q4", w.name + "_scales", w.name + "_zp"],
                                  list(node.output), name=node.name + "_q4", domain="com.microsoft",
                                  K=k, N=n_out, bits=4, block_size=BLOCK, accuracy_level=4))
    done += 1
    print(f"\r{done} projections", end="", flush=True)
data.close()
print()

# drop the Cast/Identity/Transpose chains and bf16 weights nothing reads any more
del g.node[:]
g.node.extend(nodes)
while True:
    used = {i for n in g.node for i in n.input} | {o.name for o in g.output}
    live = [n for n in g.node if any(o in used for o in n.output)]
    if len(live) == len(g.node):
        break
    del g.node[:]
    g.node.extend(live)
used = {i for n in g.node for i in n.input}
kept = [t for t in g.initializer if t.name in used]
del g.initializer[:]
g.initializer.extend(kept)
if not any(o.domain == "com.microsoft" for o in model.opset_import):
    model.opset_import.append(helper.make_opsetid("com.microsoft", 1))
onnx.save(model, f"{dst}/flash/model-q4.onnx")
left = sum(int(np.prod(t.dims)) for t in kept if t.data_type == TensorProto.BFLOAT16)
print(f"{done} projections in 4 bits ({os.path.getsize(f'{dst}/flash/{data_name}') / 2**30:.1f} GB); "
      f"{left / 1e9:.2f}B parameters left in bf16")
