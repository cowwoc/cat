---
description: "MANDATORY: Run before git push --force, rebase, or reset to verify safety"
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" validate-git-safety-agent "$0"`
