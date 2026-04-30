---
description: Use when adding a new hook or registering a hook script - create and register with proper settings
model: haiku
effort: low
user-invocable: false
allowed-tools: Bash, Write, Read, Edit
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" register-hook-agent "$0"`
