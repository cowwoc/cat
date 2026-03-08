<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: add-parallel-wave-spawn-enforcement

## Current State

The `work-implement-agent/first-use.md` Parallel Subagent Execution section states "Spawn all subagents
in the same message" but this phrase is insufficiently prominent. Agents violate it by spawning Wave 1,
awaiting the result, then spawning Wave 2 in a separate message — causing sequential execution instead
of parallel execution. Mistake M509 documents this violation.

## Target State

The Parallel Subagent Execution section contains a visually distinct CRITICAL block that names the
sequential anti-pattern by name and shows a concrete example of the correct two-Agent-call-in-one-message
pattern.

## Parent Requirements

None — quality improvement

## Research Findings

M509 root cause: the parallel spawning requirement was documented but not prominent enough. The agent
declared it would "spawn both waves in parallel" then only made one Agent call. The anti-pattern needs
to be named explicitly ("Do NOT spawn Wave 1, await result, then spawn Wave 2") and a code example
must show both Agent calls in the same message block.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — documentation only
- **Mitigation:** No code changes; no tests needed

## Files to Modify

1. `plugin/skills/work-implement-agent/first-use.md` — strengthen Parallel Subagent Execution section

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Strengthen parallel spawning requirement in `plugin/skills/work-implement-agent/first-use.md`**:
  - Find the `### Parallel Subagent Execution (two or more groups)` section
  - Add a CRITICAL warning block immediately before the first Task tool example:
    ```
    **CRITICAL: Parallel means one message — not "start Wave 1, then start Wave 2".**
    When WAVES_COUNT >= 2, spawn ALL wave subagents in a SINGLE assistant message by making
    multiple Agent (or Task) tool calls in that same message. Do NOT spawn Wave 1, await its
    result, then spawn Wave 2 in a separate message — that is sequential execution masquerading
    as parallel.

    Correct pattern (one message, two Agent calls):
    ```
    Agent tool call: Wave 1 subagent
    Agent tool call: Wave 2 subagent
    ```
    (Both calls appear in the same response turn.)
    ```
  - Ensure the existing sentence "Spawn all subagents in the same message (Task tool calls can be parallel)"
    is retained or absorbed into the new CRITICAL block
  - Files: `plugin/skills/work-implement-agent/first-use.md`

## Post-conditions

- [ ] `plugin/skills/work-implement-agent/first-use.md` Parallel Subagent Execution section contains
      explicit anti-pattern prohibition naming sequential spawning (Wave 1 then Wave 2 in separate messages)
- [ ] A concrete code example shows two Agent/Task tool calls in the same message for the multi-wave case
- [ ] The requirement is visually distinct (CRITICAL label or bold)
- [ ] No other sections of the file are modified
