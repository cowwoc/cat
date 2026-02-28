---
description: "MANDATORY: Run before git push --force, rebase, or reset to verify safety"
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" validate-git-safety "${CLAUDE_PROJECT_DIR}" "$0"`
