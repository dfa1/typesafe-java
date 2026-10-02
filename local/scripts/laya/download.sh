#!/bin/sh
# Laya typed-decisions (convaiinnovations, Apache-2.0) as ONNX, exported by onnx-community: encoder + decision head in
# one graph, checked against PyTorch to ~2e-5. For LocalTypeSafeClient.laya(...).
# Writes ~/.cache/typesafe-local/laya-fp32/{onnx/model.onnx, onnx/model.onnx_data, tokenizer.json, config.json} (1.7 GB).
set -eu
dir="${1:-$HOME/.cache/typesafe-local/laya-fp32}"
base=https://huggingface.co/onnx-community/laya-typed-decisions-ONNX/resolve/main
mkdir -p "$dir/onnx"
for f in tokenizer.json config.json onnx/model.onnx onnx/model.onnx_data; do
  [ -f "$dir/$f" ] || curl -fL -o "$dir/$f" "$base/$f"
done
echo "ready: $dir"
