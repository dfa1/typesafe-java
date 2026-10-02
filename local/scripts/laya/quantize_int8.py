# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["onnx", "onnxruntime"]
# ///
"""Int8 Laya for LocalTypeSafeClient.laya(...): ~2.3x the fp32 throughput at a small accuracy cost.

Dynamic quantization (weights int8, activations quantized at run time), per-channel, with the
decision head after the encoder kept fp32: ~6% of the weights, all of the scoring. Naive per-tensor
quantization of everything was a bit faster but lost too much agreement with Jev (urgent 17/24 vs 20/24).
Run after export_onnx.py:  uv run scripts/laya/quantize_int8.py  ->  ~/.cache/typesafe-local/laya-int8/
"""
import os
import shutil

import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

src = os.path.expanduser("~/.cache/typesafe-local/laya-fp32")
dst = os.path.expanduser("~/.cache/typesafe-local/laya-int8")
os.makedirs(dst, exist_ok=True)
for f in ("tokenizer.json", "rl_agent_config.json"):
    shutil.copy(os.path.join(src, f), dst)

nodes = onnx.load(os.path.join(src, "model.onnx"), load_external_data=False).graph.node
first_head = next(i for i, n in enumerate(nodes) if any(x.startswith(("m.type_emb", "m.head", "m.scorer")) for x in n.input))
exclude = [n.name for n in nodes[first_head:] if n.op_type in ("MatMul", "Gemm")]
quantize_dynamic(os.path.join(src, "model.onnx"), os.path.join(dst, "model.onnx"), weight_type=QuantType.QInt8,
                 per_channel=True, nodes_to_exclude=exclude)
print("wrote", dst, "(kept", len(exclude), "head MatMul/Gemm nodes fp32)")
