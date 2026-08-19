#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "$SCRIPT_DIR/../iris_runtime_helpers.sh"

compact_dump='ServiceRecord{abc u0 app.nophoneinbed/.runtime.TrackerForegroundService c:app.nophoneinbed}'
full_dump='app.nophoneinbed.runtime.TrackerForegroundService'

iris_service_visible "$compact_dump"
iris_service_visible "$full_dump"
if iris_service_visible '(nothing)'; then
  print -u2 -- 'empty service dump must not be considered active'
  exit 1
fi

print -- 'IRIS runtime helper tests passed'
