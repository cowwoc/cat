---
description: "Internal subagent — runs the full SPRT loop (sequential probability ratio test) over every .md test case in a test directory and reports per-test-case decisions and an overall result. INVOKE for 'run SPRT tests', 'SPRT', or 'sequential probability ratio test'. Invoked by instruction-builder after skill implementation."
user-invocable: false
argument-hint: "<test_dir> <worktree_path> <test_model>"
model: haiku
effort: low
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
