---
description: >
  Use when starting a new project or adding CAT to an existing one - initializes planning structure.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: sonnet
allowed-tools: [Read, Write, Bash, Glob, Grep, AskUserQuestion]
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" init-agent "$0"`
