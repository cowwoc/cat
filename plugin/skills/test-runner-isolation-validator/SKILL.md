---
name: test-runner-isolation-validator
description: Validate test isolation by checking for shared mutable state, concurrent interference, and cleanup. Use when verifying that tests won't interfere with each other in concurrent runs.
user-invocable: true
argument-hint: "<test_directory_path>"
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" test-runner-isolation-validator "$0"`
