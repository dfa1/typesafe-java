# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["onnx", "onnxruntime"]
# ///
"""Int8 Laya for LocalTypeSafeClient.laya(...): ~2x the fp32 speed on CPU and a quarter of the size, but its accuracy
depends on the CPU's int8 kernels. On one x86 GitHub runner it agreed with Jev less (78% vs 85% on yes/no); on another
it returned flat, meaningless distributions. Verify it on your target CPU (JevComparison laya-int8) or use fp32.

Dynamic quantization (weights int8, activations quantized at run time), per-channel, with the decision head after the
encoder kept fp32. Run after download.sh:  uv run scripts/laya/quantize_int8.py  ->  ~/.cache/typesafe-local/laya-int8/
"""
import os
import re
import shutil
import tempfile

import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

src = os.path.expanduser("~/.cache/typesafe-local/laya-fp32")
dst = os.path.expanduser("~/.cache/typesafe-local/laya-int8")
model = os.path.join(src, "onnx", "model.onnx")
if not os.path.exists(model):
    model = os.path.join(src, "model.onnx")
os.makedirs(dst, exist_ok=True)
for f in ("tokenizer.json", "config.json", "rl_agent_config.json"):
    if os.path.exists(os.path.join(src, f)):
        shutil.copy(os.path.join(src, f), dst)

# onnx-community's graph carries value_info shape hints that ONNX's (re-)inference rejects (1028 vs 256); drop them
# and let quantize_dynamic re-infer
with tempfile.TemporaryDirectory() as tmp:
    graph = onnx.load(model)
    del graph.graph.value_info[:]
    stripped = os.path.join(tmp, "model.onnx")
    onnx.save(graph, stripped, save_as_external_data=True, location="model.onnx_data")

    head = re.compile(r"(^|\.)(type_emb|head|scorer)\.")  # "model.head.…" (onnx-community) or "m.head.…" (export_onnx.py)
    nodes = graph.graph.node
    first_head = next(i for i, n in enumerate(nodes) if any(head.search(x) for x in n.input))
    exclude = [n.name for n in nodes[first_head:] if n.op_type in ("MatMul", "Gemm")]
    quantize_dynamic(stripped, os.path.join(dst, "model.onnx"), weight_type=QuantType.QInt8, per_channel=True,
                     nodes_to_exclude=exclude)
print("wrote", dst, "(kept", len(exclude), "head MatMul/Gemm nodes fp32)")
