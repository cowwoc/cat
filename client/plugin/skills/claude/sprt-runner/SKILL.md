---
description: "Internal subagent — runs the full SPRT loop (sequential probability ratio test) over every .md test case in a test directory and reports per-test-case decisions and an overall result. INVOKE for 'run SPRT tests', 'SPRT', or 'sequential probability ratio test'. Invoked by instruction-builder-agent after skill implementation."
user-invocable: false
argument-hint: "<test_dir> <worktree_path> <test_model> <test_effort> <expected_instruction_sha>"
model: haiku
effort: low
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
