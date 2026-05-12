---
description: >
  Work on, resume, or continue an existing issue.
  Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working", "next issue", "next".
  NOT for viewing status - use /cat:status for that.
model: sonnet
effort: high
argument-hint: "[version | issue_id | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
user-invocable: false
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
