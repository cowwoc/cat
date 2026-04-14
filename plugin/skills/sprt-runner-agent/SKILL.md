---
description: "Internal subagent — runs the full SPRT loop (sequential probability ratio test) over every .md test case in a test directory and reports per-test-case decisions and an overall result. INVOKE for 'run SPRT tests', 'SPRT', or 'sequential probability ratio test'. Invoked by instruction-builder-agent after skill implementation."
user-invocable: false
argument-hint: "<cat_agent_id> <test_dir> <worktree_path> <test_model>"
effort: high
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" sprt-runner-agent "$0"`
