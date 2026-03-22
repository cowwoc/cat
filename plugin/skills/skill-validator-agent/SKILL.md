---
description: >
  Internal subagent — validates a skill against should-trigger and should-not-trigger test prompts.
  Invoked by instruction-builder-agent Step 8 to run calibration prompts and return per-prompt pass/fail results.
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" skill-validator-agent "$0"`
