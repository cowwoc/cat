---
description: "MANDATORY: Run before git push --force, rebase, or reset to verify safety"
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" validate-git-safety "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
