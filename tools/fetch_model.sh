#!/usr/bin/env bash
set -euo pipefail

model_url='https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite'
expected_sha256='0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb'
target_path='app/src/main/assets/efficientdet_lite0.tflite'

mkdir -p "$(dirname "$target_path")"
curl -fsSL "$model_url" -o "$target_path"
actual_sha256="$(shasum -a 256 "$target_path" | awk '{print $1}')"

if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "Model checksum mismatch: $actual_sha256" >&2
    exit 1
fi

echo "Verified $target_path ($actual_sha256)"
