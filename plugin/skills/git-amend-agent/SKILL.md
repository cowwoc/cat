---
description: "MANDATORY: Use instead of `git commit --amend` - verifies HEAD and push status first"
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" git-amend-agent "$0"`
