#!/bin/sh
# Laya typed-decisions (convaiinnovations, Apache-2.0) as ONNX, exported by onnx-community: encoder + decision head in
# one graph, checked against PyTorch to ~2e-5. For LocalTypeSafeClient.laya(...).
#   download.sh          ~/.cache/typesafe-local/laya-fp32  (1.7 GB, the default)
#   download.sh fp16     ~/.cache/typesafe-local/laya-fp16  (0.85 GB: same answers, faster on GPU, slower on CPU)
set -eu
variant="${1:-fp32}"
base=https://huggingface.co/onnx-community/laya-typed-decisions-ONNX/resolve/main
case "$variant" in
  fp32) model=onnx/model.onnx;      data=model.onnx_data ;;
  fp16) model=onnx/model_fp16.onnx; data=model_fp16.onnx_data ;;
  *) echo "usage: download.sh [fp32|fp16] [dir]" >&2; exit 2 ;;
esac
dir="${2:-$HOME/.cache/typesafe-local/laya-$variant}"
mkdir -p "$dir/onnx"
for f in tokenizer.json config.json; do
  [ -f "$dir/$f" ] || curl -fL -o "$dir/$f" "$base/$f"
done
# the engine loads onnx/model.onnx; the weights file keeps the name the graph references
[ -f "$dir/onnx/model.onnx" ] || curl -fL -o "$dir/onnx/model.onnx" "$base/$model"
[ -f "$dir/onnx/$data" ] || curl -fL -o "$dir/onnx/$data" "$base/onnx/$data"
echo "ready: $dir"
