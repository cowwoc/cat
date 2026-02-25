---
description: Use when removing code patterns across multiple files - safe removal with validation and rollback
user-invocable: false
allowed-tools: Bash, Read, Edit, Grep, Glob
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" safe-remove-code "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
