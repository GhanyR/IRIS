#!/bin/zsh

iris_service_visible() {
  local service_dump="$1"
  [[ "$service_dump" == *"app.nophoneinbed.runtime.TrackerForegroundService"* ||
    "$service_dump" == *"app.nophoneinbed/.runtime.TrackerForegroundService"* ]]
}
