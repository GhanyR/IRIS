#!/bin/zsh
set -euo pipefail

readonly DEFAULT_IRIS_IP="10.20.10.50"
readonly ADB_PORT="5555"

if ! command -v adb >/dev/null 2>&1; then
  print -u2 "adb tidak ditemukan. Install Android platform-tools lebih dulu."
  exit 1
fi
if ! command -v scrcpy >/dev/null 2>&1; then
  print -u2 "scrcpy tidak ditemukan. Jalankan: brew install scrcpy"
  exit 1
fi

network_serial="$(adb devices | awk '$1 ~ /:5555$/ && $2 == "device" { print $1; exit }')"

if [[ -z "$network_serial" ]]; then
  usb_serial="$(adb devices | awk '$1 !~ /:/ && $2 == "device" { print $1; exit }')"
  if [[ -n "$usb_serial" ]]; then
    device_ip="$(adb -s "$usb_serial" shell ip route | awk '/wlan0/ { for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }' | tr -d '\r')"
    if [[ -n "$device_ip" ]]; then
      adb -s "$usb_serial" tcpip "$ADB_PORT" >/dev/null
      sleep 2
      adb connect "$device_ip:$ADB_PORT" >/dev/null
      network_serial="$device_ip:$ADB_PORT"
    fi
  fi
fi

if [[ -z "$network_serial" ]]; then
  device_ip="${IRIS_DEVICE_IP:-$DEFAULT_IRIS_IP}"
  adb connect "$device_ip:$ADB_PORT" >/dev/null || true
  network_serial="$device_ip:$ADB_PORT"
fi

if [[ "$(adb -s "$network_serial" get-state 2>/dev/null || true)" != "device" ]]; then
  print -u2 "Motorola tidak terhubung di $network_serial. Pastikan Mac dan HP berada di Wi-Fi yang sama."
  print -u2 "Jika IP berubah, jalankan: IRIS_DEVICE_IP=x.x.x.x $0"
  exit 1
fi

model="$(adb -s "$network_serial" shell getprop ro.product.model | tr -d '\r')"
print "Membuka IRIS di $model melalui Wi-Fi ($network_serial)"
adb -s "$network_serial" shell am start -n app.nophoneinbed/.MainActivity >/dev/null

exec scrcpy \
  --serial="$network_serial" \
  --no-audio \
  --stay-awake \
  --window-title="IRIS — Wireless Setup & Live Monitor"
