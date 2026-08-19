#!/usr/bin/env bash
set -euo pipefail

model_url='https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite2/int8/latest/efficientdet_lite2.tflite'
expected_sha256='b3f50554cb0ea559e90328845f7d9ba4d13c8bff372914d24e06bc8bb72fa896'
target_path='app/src/main/assets/efficientdet_lite2.tflite'

mkdir -p "$(dirname "$target_path")"
curl -fsSL "$model_url" -o "$target_path"
actual_sha256="$(shasum -a 256 "$target_path" | awk '{print $1}')"

if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "Model checksum mismatch: $actual_sha256" >&2
    exit 1
fi

echo "Verified $target_path ($actual_sha256)"
