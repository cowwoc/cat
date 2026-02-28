---
description: PREFER when writing 3+ independent files - batch operation eliminates round-trips (50-70% faster)
model: haiku
user-invocable: false
allowed-tools: Write, Edit, Bash
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" batch-write "${CLAUDE_PROJECT_DIR}" "$0"`
