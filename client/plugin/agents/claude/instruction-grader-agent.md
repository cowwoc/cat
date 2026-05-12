---
name: instruction-grader-agent
description: >
  Internal subagent — grades a list of assertions against a single test-case output, assigning
  pass/fail verdicts with evidence quotes. Writes grading JSON to the provided output path and
  returns the path. Never commits files.
model: claude-haiku-4-5
effort: low
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../common/instruction-grader-agent.md -->
