#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
if /usr/bin/curl --silent --fail http://127.0.0.1:8765/api/status >/dev/null 2>&1; then
  /usr/bin/open http://127.0.0.1:8765
  exit 0
fi
exec /usr/bin/env python3 "$SCRIPT_DIR/iris_manager.py"
