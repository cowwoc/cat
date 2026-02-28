---
description: PREFER when reading 3+ related files - batch operation eliminates round-trips (50-70% faster)
model: haiku
user-invocable: false
allowed-tools: Bash
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" batch-read "${CLAUDE_PROJECT_DIR}" "$0"`
