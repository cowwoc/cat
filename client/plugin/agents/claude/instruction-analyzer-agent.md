---
name: instruction-analyzer-agent
description: >
  Internal subagent — reads an instruction-test JSON produced by InstructionTestAggregator and surfaces
  actionable patterns: non-discriminating assertions, high-variance evals, and time/token tradeoffs. Returns
  a structured analysis report for the instruction-builder review step.
model: sonnet
effort: high
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../common/instruction-analyzer-agent.md -->
