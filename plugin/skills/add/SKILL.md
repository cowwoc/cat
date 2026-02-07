---
description: Use to add a issue to a version OR create a new version (major/minor/patch)
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "[description]"
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" add "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
