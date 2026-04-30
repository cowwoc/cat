---
description: >
  Launch a nested Claude CLI instance with an isolated config directory.
  Use when you need to run a prompt in a fresh Claude environment with updated plugin cache.
user-invocable: false
argument-hint: "<cat_agent_id>"
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" claude-runner "$0"`
