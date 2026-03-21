---
description: "MANDATORY: Run before git push --force, rebase, or reset to verify safety"
model: haiku
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" validate-git-safety-agent "$0"`
