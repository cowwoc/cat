# Plan: enforce-action-based-test-assertions

## Type
refactor

## Current State
`plugin/skills/instruction-builder-agent/first-use.md` Step 4.1 (Auto-Generate Test Cases) does not specify the
required format for benchmark test case prompts. Agents may use Q&A format — asking the agent a direct question and
asserting on its verbal answer — which tests knowledge recall rather than production behavior.

## Target State
Step 4.1 explicitly mandates that test case prompts must mirror production input sequences (production-sequence
format) and assertions must verify concrete actions taken by the agent (action-based assertions), not verbal Q&A
answers. This ensures benchmarks validate the agent's behavior under real conditions.

## Goal
Update the instruction-builder-agent benchmark test convention to require production-sequence prompts and
action-based assertions.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — documentation only
- **Mitigation:** No code changes; existing benchmarks will be improved when next updated

## Files to Modify
- `plugin/skills/instruction-builder-agent/first-use.md` — add production-sequence test format convention to
  Step 4.1 (Auto-Generate Test Cases)

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `plugin/skills/instruction-builder-agent/first-use.md`, locate Step 4.1 (Auto-Generate Test Cases, around
  line 272-340). Add a convention box or note that specifies:
  - Test case prompts MUST mirror production input sequences (what a real caller would provide), not Q&A questions
  - Assertions MUST verify what the agent does next (concrete actions: file writes, tool calls, bash commands),
    not what it says in a verbal response
  - Q&A format (e.g., "Given you are working on X, which approach should you use?") is explicitly prohibited
    because it tests knowledge recall, not production behavior
  - Example of prohibited Q&A: prompt asks "should you squash before or after rebase?", assertion checks verbal
    answer
  - Example of correct production-sequence: prompt mirrors a real production scenario step with prerequisite
    state established, assertion checks that the agent invokes the correct tool or produces the expected file change

## Post-conditions
- [ ] Step 4.1 in `plugin/skills/instruction-builder-agent/first-use.md` contains a clear convention specifying
  production-sequence format for test prompts
- [ ] The convention explicitly prohibits Q&A format test cases
- [ ] The convention explains action-based assertions (what agent does, not what it says)
- [ ] At least one example of prohibited Q&A format and one example of correct production-sequence format are
  included
- [ ] All other content in first-use.md is unchanged
