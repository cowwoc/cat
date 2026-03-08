---
description: Use when merging a branch with linear history - merge, rebase onto main, linear merge
model: sonnet
user-invocable: false
allowed-tools: Bash, Read
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" git-merge-linear-agent "$0"`
