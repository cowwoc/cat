---
name: diff-validation-agent
description: >
  Internal subagent — verifies that blue-team patch commits address each finding in the red-team's
  findings.json. Returns a validation report with PASS/FAIL/SKIPPED status per finding and exits
  non-zero when any non-disputed CRITICAL or HIGH finding has no matching patch hunk.
model: haiku
effort: high
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../common/diff-validation-agent.md -->
