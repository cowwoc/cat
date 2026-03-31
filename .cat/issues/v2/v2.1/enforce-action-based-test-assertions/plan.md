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

## Jobs

### Job 1
- In `plugin/skills/instruction-builder-agent/first-use.md`, locate Step 4.1 around line 305 (`#### Step 4.1:
  Auto-Generate Test Cases`). Read the file to find the exact insertion point: after step 3 of the algorithm
  (`3. Generate plain-text assertions...` ending around line 329) and before the `**Scenario file naming:**`
  heading (around line 331).
- Insert the following block at that insertion point (between step 3 and `**Scenario file naming:**`):

  ```
  **Production-sequence prompt format (MANDATORY):** Test case prompts MUST mirror production input
  sequences — the exact type of message a real caller would send when invoking this skill in normal use.
  Prompts MUST NOT use Q&A format (posing a direct question to test knowledge recall).

  - **Prohibited (Q&A format):** `"Given you are implementing step 4, should you squash before or after
    rebase?"` — tests verbal knowledge recall, not production behavior.
  - **Correct (production-sequence format):** `"I've finished implementing 2.1-my-issue and the commits
    look good. Please merge it."` — mirrors real production input; assertion checks that the agent invokes
    `cat:git-squash-agent` before the approval gate.

  **Action-based assertions (MANDATORY):** Assertions MUST verify what the agent does next — concrete,
  observable actions such as tool invocations, file writes, or Bash commands. Assertions MUST NOT verify
  what the agent says in a verbal response.

  - **Prohibited (verbal assertion):** `"Agent explains that squashing must happen before rebasing."` —
    verifies knowledge verbalization, not behavior.
  - **Correct (action-based assertion):** `"The Skill tool was invoked with skill cat:git-squash-agent."` —
    verifies the concrete action taken.
  ```

- After inserting the block, verify the surrounding content is intact: step 3 of the algorithm still ends
  the numbered list, the inserted block follows immediately, and `**Scenario file naming:**` immediately
  follows the inserted block.
- Update `.cat/issues/v2/v2.1/enforce-action-based-test-assertions/index.json` in the same commit: set
  `"status": "closed"` and `"progress": 100`.
- Commit with message: `refactor: enforce production-sequence prompts and action-based assertions in Step 4.1`

## Post-conditions
- [ ] Step 4.1 in `plugin/skills/instruction-builder-agent/first-use.md` contains a clear convention specifying
  production-sequence format for test prompts
- [ ] The convention explicitly prohibits Q&A format test cases
- [ ] The convention explains action-based assertions (what agent does, not what it says)
- [ ] At least one example of prohibited Q&A format and one example of correct production-sequence format are
  included
- [ ] All other content in first-use.md is unchanged
