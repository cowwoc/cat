---
description: >
  Empirically test agent compliance with controlled experiments.
  Trigger words: "test compliance", "run empirical test", "verify agent behavior".
  Use for validating agent adherence to instructions and guidelines.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" empirical-test-agent "$0"`
