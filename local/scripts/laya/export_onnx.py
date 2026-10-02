# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["torch", "transformers>=4.48", "safetensors", "huggingface_hub", "onnx", "onnxscript", "onnxruntime", "numpy"]
# ///
"""Export Laya (convaiinnovations/laya-typed-decisions, Apache-2.0) to ONNX ourselves. Not needed to use the module
(download.sh fetches onnx-community's equivalent export); kept to regenerate the PyTorch reference fixture that
LayaEngineTest checks the Java port against.

Laya ships only PyTorch weights plus a custom decision head (rl_common.DecisionModel). This exports
encoder + head as one graph: (input_ids, attention_mask, marker_pos, marker_mask, qtype) -> logits,
checks onnxruntime against PyTorch, and writes a fixture (token ids, marker positions, logits) that
the Java tests compare against. Writes ~/.cache/typesafe-local/laya-export/{model.onnx, model.onnx.data, tokenizer.json,
rl_agent_config.json}, also usable with LocalTypeSafeClient.laya(...).
Run:  uv run scripts/laya/export_onnx.py
"""
import importlib, json, os, shutil, sys

import numpy as np
import torch

REPO = "convaiinnovations/laya-typed-decisions"
OUT = os.path.expanduser("~/.cache/typesafe-local/laya-export")
FIXTURE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/test/resources/laya/fixture.json")

from huggingface_hub import hf_hub_download, snapshot_download
src = snapshot_download(REPO)  # HF's own cache
code = hf_hub_download("convaiinnovations/laya", "rl_common.py")  # the head's code lives in the base repo
sys.path.insert(0, os.path.dirname(code))
rc = importlib.import_module("rl_common")
os.makedirs(OUT, exist_ok=True)
shutil.copy(os.path.join(src, "tokenizer", "tokenizer.json"), OUT)
shutil.copy(os.path.join(src, "rl_agent_config.json"), OUT)

from safetensors.torch import load_file
from transformers import AutoConfig, AutoModel, AutoTokenizer

cfg = json.load(open(os.path.join(src, "rl_agent_config.json")))
enc = AutoModel.from_config(AutoConfig.from_pretrained(os.path.join(src, "encoder")), attn_implementation="eager")
model = rc.DecisionModel(enc, cfg["head_layers"], len(cfg["act_costs"]) + 1)
model.load_state_dict(load_file(os.path.join(src, "model.safetensors")), strict=True)
model.eval()
torch.backends.mha.set_fastpath_enabled(False)  # nn.TransformerEncoderLayer's fused fast path doesn't export
tok = AutoTokenizer.from_pretrained(os.path.join(src, "tokenizer"))


class Logits(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_ids, attention_mask, marker_pos, marker_mask, qtype):
        return self.m(input_ids, attention_mask, marker_pos, marker_mask.bool(), qtype)[0]


state = "Help! My payouts have been failing for 3 days and I can't pay my staff."
questions = [
    {"t": "noul", "ins": "Does this message require immediate attention?", "crit": None},
    {"t": "choice", "ins": "What is this support message about?",
     "crit": {"billing": "payments, charges, invoices, refunds", "bug": "the product crashes, errors or misbehaves",
              "feature": "a request for something new", "account": "login, access, account settings"}},
    {"t": "score", "ins": "How angry is the customer?", "crit": ["calm", "annoyed", "angry", "furious"]},
]
items = []
for q in questions:
    ids, markers = rc.build_sequence(tok, state, q, cfg["max_len"], cfg["head_max_len"])
    items.append({"ids": ids, "markers": markers, "qtype": rc.QTYPES[q["t"]], "target": [0.0] * len(markers),
                  "label": -1, "episode": 0, "ep_step": 0, "ep_len": 1, "src": "export"})
b = collate_items = rc.collate_items([items], tok.pad_token_id)
args = (b["input_ids"], b["attention_mask"], b["marker_pos"], b["marker_mask"].long(), b["qtype"])
wrapped = Logits(model).eval()
with torch.no_grad():
    expected = wrapped(*args).numpy()

onnx_path = os.path.join(OUT, "model.onnx")
# dynamo: the legacy tracer bakes the sample's seq length into nn.MultiheadAttention's reshapes
batch, seq, options = torch.export.Dim("batch", max=256), torch.export.Dim("seq", min=8, max=cfg["max_len"]), \
    torch.export.Dim("options", min=2, max=255)
program = torch.onnx.export(wrapped, args, dynamo=True, opset_version=18,
                            input_names=["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"],
                            output_names=["logits"],
                            dynamic_shapes=({0: batch, 1: seq}, {0: batch, 1: seq}, {0: batch, 1: options},
                                            {0: batch, 1: options}, {0: batch}))
program.save(onnx_path, external_data=False)

import onnxruntime as ort
sess = ort.InferenceSession(onnx_path)
got = sess.run(None, {n: a.numpy() for n, a in zip(["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"], args)})[0]
diff = float(np.abs(np.where(b["marker_mask"].numpy(), got - expected, 0)).max())
print("onnx vs torch max |diff| over real options:", diff)
assert diff < 1e-3, diff

solo = rc.collate_items([[items[0]]], tok.pad_token_id)
got_solo = sess.run(None, {"input_ids": solo["input_ids"].numpy(), "attention_mask": solo["attention_mask"].numpy(),
                           "marker_pos": solo["marker_pos"].numpy(), "marker_mask": solo["marker_mask"].long().numpy(),
                           "qtype": solo["qtype"].numpy()})[0]
print("noul alone vs in batch:", got_solo[0, :2], expected[0, :2])
assert np.abs(got_solo[0, :2] - expected[0, :2]).max() < 1e-3

os.makedirs(os.path.dirname(FIXTURE), exist_ok=True)
json.dump({"state": state, "questions": questions,
           "items": [{"ids": it["ids"], "markers": it["markers"], "qtype": it["qtype"],
                      "logits": expected[i, :len(it["markers"])].tolist()} for i, it in enumerate(items)]},
          open(FIXTURE, "w"), indent=1)
print("wrote", onnx_path, "and", os.path.abspath(FIXTURE))
