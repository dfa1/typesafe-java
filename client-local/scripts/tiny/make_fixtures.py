# /// script
# requires-python = ">=3.10"
# dependencies = ["onnx"]
# ///
"""Tiny stand-ins for the Laya, Qwen and Clef model directories, so the engines' Java plumbing (sequences,
batching, tensors, reading logits, mapping them to answers) runs in every unit build without model files.

Each graph declares the real model's inputs and outputs but computes deterministic logits with a few ops:
answer quality is the job of the @Tag("model") tests against the real models. The tokenizer is byte-level BPE
over the 256 byte symbols, plus merges for "Yes"/"No" (Qwen needs them as single tokens) and the special
tokens Laya and Qwen use. A few KB in total.
Run:  uv run scripts/tiny/make_fixtures.py   (writes src/test/resources/tiny/{laya,qwen,clef})"""
import json
import os

import onnx
from onnx import TensorProto, helper

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "test", "resources", "tiny")


def byte_to_unicode():
    """GPT-2's reversible byte-to-printable-character table, as BpeTokenizer has it."""
    table, n = [], 0
    for b in range(256):
        printable = ord("!") <= b <= ord("~") or 0xA1 <= b <= 0xAC or 0xAE <= b <= 0xFF
        table.append(chr(b) if printable else chr(256 + n))
        n += 0 if printable else 1
    return table


def tokenizer():
    vocab = {c: i for i, c in enumerate(byte_to_unicode())}
    merges = []
    for word in ("Yes", "No"):
        for i in range(2, len(word) + 1):
            merges.append(f"{word[:i - 1]} {word[i - 1]}")
            vocab.setdefault(word[:i], len(vocab))
    special = ["[CLS]", "[SEP]", "[PAD]", "[MASK]", "<|im_start|>", "<|im_end|>"]
    added = [{"id": len(vocab) + i, "content": t, "special": True, "lstrip": False, "rstrip": False,
              "single_word": False, "normalized": False} for i, t in enumerate(special)]
    return {"version": "1.0", "normalizer": None,
            "pre_tokenizer": {"type": "ByteLevel", "add_prefix_space": False, "trim_offsets": True, "use_regex": True},
            "model": {"type": "BPE", "dropout": None, "unk_token": None, "continuing_subword_prefix": None,
                      "end_of_word_suffix": None, "fuse_unk": False, "byte_fallback": False, "vocab": vocab,
                      "merges": merges},
            "added_tokens": added}, len(vocab) + len(special)


def save(graph, path):
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
    model.ir_version = 9  # what ONNX Runtime 1.30 reads
    onnx.checker.check_model(model)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    onnx.save(model, path)


def scale(name, value):
    return helper.make_tensor(name, TensorProto.FLOAT, [], [value])


def laya():
    """logits[r, i] = 0.1 * marker_pos[r, i]: each option scores by its position, the same alone or in a padded batch."""
    i = helper.make_tensor_value_info
    graph = helper.make_graph(
        [helper.make_node("Cast", ["marker_pos"], ["pos"], to=TensorProto.FLOAT),
         helper.make_node("Mul", ["pos", "scale"], ["logits"])],
        "tiny-laya",
        [i("input_ids", TensorProto.INT64, ["batch", "seq"]), i("attention_mask", TensorProto.INT64, ["batch", "seq"]),
         i("marker_pos", TensorProto.INT64, ["batch", "k"]), i("marker_mask", TensorProto.BOOL, ["batch", "k"]),
         i("qtype", TensorProto.INT64, ["batch"])],
        [i("logits", TensorProto.FLOAT, ["batch", "k"])],
        [scale("scale", 0.1)])
    save(graph, os.path.join(OUT, "laya", "onnx", "model.onnx"))
    with open(os.path.join(OUT, "laya", "config.json"), "w") as f:
        json.dump({"laya": {"max_len": 128, "head_max_len": 64, "temperature": [1.0, 1.0, 1.0],
                            "temperature_by_options": {"choice:3-5": 2.0}}}, f, indent=2)


def qwen(vocab):
    """logits[0, t, v] = input_ids[0, t] * w[v]: the next token's scores depend on the last token, like a real prefill.
    One unused past_key_values input, so the empty-cache path runs too."""
    i = helper.make_tensor_value_info
    w = helper.make_tensor("w", TensorProto.FLOAT, [vocab], [((v * 37) % 101) / 100.0 for v in range(vocab)])
    graph = helper.make_graph(
        [helper.make_node("Cast", ["input_ids"], ["ids"], to=TensorProto.FLOAT),
         helper.make_node("Unsqueeze", ["ids", "last_axis"], ["ids3"]),
         helper.make_node("Mul", ["ids3", "w"], ["logits"])],
        "tiny-qwen",
        [i("input_ids", TensorProto.INT64, [1, "seq"]), i("attention_mask", TensorProto.INT64, [1, "seq"]),
         i("position_ids", TensorProto.INT64, [1, "seq"]),
         i("past_key_values.0.key", TensorProto.FLOAT, [1, 2, "past", 4])],
        [i("logits", TensorProto.FLOAT, [1, "seq", vocab])],
        [w, helper.make_tensor("last_axis", TensorProto.INT64, [1], [2])])
    save(graph, os.path.join(OUT, "qwen", "onnx", "model.onnx"))


def clef():
    """logits[o] = 0.01 * option_spans[o, 0]: one logit per option, from where the option starts in the sequence."""
    i = helper.make_tensor_value_info
    graph = helper.make_graph(
        [helper.make_node("Gather", ["option_spans", "zero"], ["starts"], axis=1),
         helper.make_node("Cast", ["starts"], ["s"], to=TensorProto.FLOAT),
         helper.make_node("Mul", ["s", "scale"], ["logits"])],
        "tiny-clef",
        [i("input_ids", TensorProto.INT64, [1, "seq"]), i("token_positions", TensorProto.INT64, ["seq"]),
         i("question_spans", TensorProto.INT64, ["q", 2]), i("question_types", TensorProto.INT64, ["q"]),
         i("option_spans", TensorProto.INT64, ["o", 2]), i("option_question", TensorProto.INT64, ["o"])],
        [i("logits", TensorProto.FLOAT, ["o"])],
        [scale("scale", 0.01), helper.make_tensor("zero", TensorProto.INT64, [], [0])])
    save(graph, os.path.join(OUT, "clef", "flash", "model.onnx"))


tok, vocab = tokenizer()
laya()
qwen(vocab)
clef()
for engine in ("laya", "qwen", "clef"):
    with open(os.path.join(OUT, engine, "tokenizer.json"), "w") as f:
        json.dump(tok, f, ensure_ascii=False)
print(f"wrote {OUT} (vocab {vocab})")
