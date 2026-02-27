---
description: Universal silent executor for all verbatim output skill generation
model: haiku
allowed-tools:
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" get-output "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}" $ARGUMENTS`
