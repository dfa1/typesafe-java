# /// script
# requires-python = ">=3.10"
# dependencies = ["tokenizers"]
# ///
"""Reference ids from HuggingFace tokenizers (the Rust library behind tokenizer.json) for the strings
TokenizerInputs wrote; BpeTokenizerTest checks the pure-Java tokenizer against them.
Run:  uv run scripts/tokenizer/reference.py"""
import json
import os

from tokenizers import Tokenizer

res = "src/test/resources/tokenizer"
for name, model in (("laya", "laya-fp32"), ("qwen", "qwen2.5-1.5b")):
    tok = Tokenizer.from_file(os.path.expanduser(f"~/.cache/typesafe-local/{model}/tokenizer.json"))
    texts = json.load(open(f"{res}/inputs-{name}.json"))
    out = [{"text": t, "ids": tok.encode(t, add_special_tokens=False).ids} for t in texts]
    json.dump(out, open(f"{res}/expected-{name}.json", "w"), ensure_ascii=False)
    print(name, len(out), "strings")
