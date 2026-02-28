---
description: "MANDATORY: Use instead of rm -rf or rm -r to prevent shell session breakage"
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" safe-rm "${CLAUDE_PROJECT_DIR}" "$0"`
