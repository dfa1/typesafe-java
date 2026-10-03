# /// script
# requires-python = ">=3.10"
# dependencies = ["tokenizers", "torch"]
# ///
"""Clef's own encode_record (joint_schema_model.py, pinned to the revision Ollaya's graph was exported from) on the
requests in src/test/resources/clef/requests.json; ClefEngineTest checks ClefEngine.sequence against it. The last
request is repeated with a state long enough to be truncated.
Run:  uv run scripts/clef/reference.py"""
import importlib.util
import json
import os
import sys
import urllib.request

from tokenizers import Tokenizer

REVISION = "17f0b0ad64efb65d273590632833508766b2aae6"
url = f"https://huggingface.co/Cloudflare/clef-flash/raw/{REVISION}/joint_schema_model.py"
path = os.path.expanduser("~/.cache/typesafe-local/clef-flash/joint_schema_model.py")
if not os.path.exists(path):
    urllib.request.urlretrieve(url, path)
spec = importlib.util.spec_from_file_location("joint_schema_model", path)
jsm = sys.modules["joint_schema_model"] = importlib.util.module_from_spec(spec)
spec.loader.exec_module(jsm)


class Ids:
    def __init__(self, ids):
        self.input_ids = ids


tok = Tokenizer.from_file(os.path.expanduser("~/.cache/typesafe-local/clef-flash/tokenizer.json"))
tokenizer = lambda text, add_special_tokens: Ids(tok.encode(text, add_special_tokens=add_special_tokens).ids)

res = "src/test/resources/clef"
requests = json.load(open(f"{res}/requests.json"))
requests.append({**requests[-1], "state": "the order never arrived. " * 1000})
out = []
for request in requests:
    r = jsm.encode_record(tokenizer, request, max_length=4096)  # Ollaya's decision.json max_tokens
    out.append({
        "input_ids": list(r.input_ids),
        "question_spans": [list(q.question_span) for q in r.questions],
        "question_types": [q.question_type for q in r.questions],
        "option_spans": [list(s) for q in r.questions for s in q.option_spans],
        "option_ids": [list(q.option_ids) for q in r.questions],
    })
json.dump({"requests": requests, "expected": out}, open(f"{res}/expected.json", "w"), ensure_ascii=False)
print(len(out), "requests,", [len(o["input_ids"]) for o in out], "tokens")
