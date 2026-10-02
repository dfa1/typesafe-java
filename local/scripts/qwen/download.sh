#!/bin/sh
# Qwen2.5-1.5B-Instruct (Apache-2.0), 4-bit ONNX export by onnx-community, for LocalTypeSafeClient.qwen(...).
# Writes ~/.cache/typesafe-local/qwen2.5-1.5b/{model.onnx, tokenizer.json} (1.8 GB).
set -eu
dir="${1:-$HOME/.cache/typesafe-local/qwen2.5-1.5b}"
base=https://huggingface.co/onnx-community/Qwen2.5-1.5B-Instruct/resolve/main
mkdir -p "$dir"
[ -f "$dir/tokenizer.json" ] || curl -fL -o "$dir/tokenizer.json" "$base/tokenizer.json"
[ -f "$dir/model.onnx" ] || curl -fL -o "$dir/model.onnx" "$base/onnx/model_q4.onnx"
echo "ready: $dir"
