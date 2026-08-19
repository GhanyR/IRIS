#!/bin/zsh
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
PACKAGE_NAME="app.nophoneinbed"
SERVICE_NAME="app.nophoneinbed.runtime.TrackerForegroundService"
DURATION_SECONDS="${IRIS_SOAK_SECONDS:-1800}"
INTERVAL_SECONDS="${IRIS_SOAK_INTERVAL_SECONDS:-30}"
OUTPUT_PATH="${IRIS_SOAK_OUTPUT:-/tmp/iris-soak-$(date +%Y%m%d-%H%M%S).tsv}"

choose_serial() {
  if [[ -n "${IRIS_ADB_SERIAL:-}" ]]; then
    print -r -- "$IRIS_ADB_SERIAL"
    return
  fi

  local connected
  connected="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  local wireless
  wireless="$(print -r -- "$connected" | awk '/:5555$/ { print; exit }')"
  if [[ -n "$wireless" ]]; then
    print -r -- "$wireless"
  else
    print -r -- "$connected" | awk 'NF { print; exit }'
  fi
}

SERIAL="$(choose_serial)"
if [[ -z "$SERIAL" ]]; then
  print -u2 -- "IRIS soak gagal: tidak ada perangkat ADB yang tersambung."
  exit 2
fi

if ! [[ "$DURATION_SECONDS" =~ '^[0-9]+$' && "$INTERVAL_SECONDS" =~ '^[0-9]+$' ]]; then
  print -u2 -- "IRIS_SOAK_SECONDS dan IRIS_SOAK_INTERVAL_SECONDS harus berupa angka bulat."
  exit 2
fi
if (( DURATION_SECONDS < 1 || INTERVAL_SECONDS < 1 )); then
  print -u2 -- "Durasi dan interval soak harus lebih besar dari nol."
  exit 2
fi

START_EPOCH="$(date +%s)"
DEADLINE_EPOCH=$(( START_EPOCH + DURATION_SECONDS ))
SAMPLES=0
UNHEALTHY_SAMPLES=0

print -r -- $'time\telapsed_s\tpid\tservice_visible\tlog_age_s\tbattery_pct\tbattery_temp_c\tlatest_iris_log' > "$OUTPUT_PATH"
print -r -- "Memantau IRIS di $SERIAL selama ${DURATION_SECONDS}s; log: $OUTPUT_PATH"

while true; do
  NOW_EPOCH="$(date +%s)"
  ELAPSED=$(( NOW_EPOCH - START_EPOCH ))
  PID="$($ADB_BIN -s "$SERIAL" shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' || true)"
  SERVICE_DUMP="$($ADB_BIN -s "$SERIAL" shell dumpsys activity services "$PACKAGE_NAME" 2>/dev/null || true)"
  if [[ "$SERVICE_DUMP" == *"$SERVICE_NAME"* ]]; then
    SERVICE_VISIBLE="yes"
  else
    SERVICE_VISIBLE="no"
  fi
  BATTERY_DUMP="$($ADB_BIN -s "$SERIAL" shell dumpsys battery 2>/dev/null || true)"
  BATTERY_LEVEL="$(print -r -- "$BATTERY_DUMP" | awk -F': ' '/level:/ { print $2; exit }')"
  BATTERY_TEMP_RAW="$(print -r -- "$BATTERY_DUMP" | awk -F': ' '/temperature:/ { print $2; exit }')"
  if [[ "$BATTERY_TEMP_RAW" =~ '^[0-9]+$' ]]; then
    BATTERY_TEMP_C="$(awk -v value="$BATTERY_TEMP_RAW" 'BEGIN { printf "%.1f", value / 10 }')"
  else
    BATTERY_TEMP_C="unknown"
  fi
  LATEST_LOG_RAW="$($ADB_BIN -s "$SERIAL" logcat -d -v epoch -s 'IRIS:I' '*:S' 2>/dev/null | tail -n 1 | tr '\t\r\n' '   ' || true)"
  LOG_EPOCH="$(print -r -- "$LATEST_LOG_RAW" | awk 'NF { print int($1); exit }')"
  DEVICE_EPOCH="$($ADB_BIN -s "$SERIAL" shell date +%s 2>/dev/null | tr -d '\r' || true)"
  if [[ "$LOG_EPOCH" =~ '^[0-9]+$' && "$DEVICE_EPOCH" =~ '^[0-9]+$' ]]; then
    LOG_AGE_SECONDS=$(( DEVICE_EPOCH - LOG_EPOCH ))
  else
    LOG_AGE_SECONDS="unknown"
  fi

  print -r -- "$(date -Iseconds)\t$ELAPSED\t${PID:-missing}\t$SERVICE_VISIBLE\t$LOG_AGE_SECONDS\t${BATTERY_LEVEL:-unknown}\t$BATTERY_TEMP_C\t${LATEST_LOG_RAW:-none}" >> "$OUTPUT_PATH"
  SAMPLES=$(( SAMPLES + 1 ))
  print -r -- "sample=$SAMPLES elapsed=${ELAPSED}s pid=${PID:-missing} service=$SERVICE_VISIBLE log_age=${LOG_AGE_SECONDS}s battery=${BATTERY_LEVEL:-?}% temp=${BATTERY_TEMP_C}C"

  if [[ -z "$PID" || "$SERVICE_VISIBLE" != "yes" || "$LOG_AGE_SECONDS" == "unknown" ]] || (( LOG_AGE_SECONDS > 15 )); then
    UNHEALTHY_SAMPLES=$(( UNHEALTHY_SAMPLES + 1 ))
  else
    UNHEALTHY_SAMPLES=0
  fi
  if (( UNHEALTHY_SAMPLES >= 2 )); then
    print -u2 -- "IRIS soak gagal: proses, service, atau log frame tidak sehat pada dua sampel berurutan."
    exit 1
  fi
  if (( NOW_EPOCH >= DEADLINE_EPOCH )); then
    break
  fi
  sleep "$INTERVAL_SECONDS"
done

if (( UNHEALTHY_SAMPLES > 0 )); then
  print -u2 -- "IRIS soak gagal: sampel terakhir tidak sehat."
  exit 1
fi
print -r -- "IRIS soak lulus: $SAMPLES sampel, proses dan foreground service tetap hidup."
