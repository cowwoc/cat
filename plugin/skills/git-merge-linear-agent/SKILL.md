---
description: Use when merging a branch with linear history - merge, rebase onto main, linear merge
model: sonnet
user-invocable: false
allowed-tools: Bash, Read
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" git-merge-linear-agent "$0"`
