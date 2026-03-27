# Plan: clarify-empirical-test-workflow

## Problem

The `cat:empirical-test-agent` Step 7 (Test the Fix) section describes how to test a candidate fix but provides no guidance on whether to fix the skill under test or modify the test cases when compliance is low. Agents are left uncertain about which to change.

## Parent Requirements

None

## Reproduction Code

When an empirical test shows low compliance (e.g., 0% pass rate on multiple trials), an agent needs guidance on whether to:
1. Modify the test case prompts to increase pass rate
2. Fix the underlying skill/agent being tested
3. Something else

The current Step 7 guidance does not distinguish between these options clearly.

## Expected vs Actual

- **Expected:** Step 7 explicitly defines "fundamentally broken test case" as a test that was testing the wrong behavior (e.g., testing that an agent skips steps when the rule says steps must not be skipped). Guidance clarifies: "If test compliance is low, assume the skill/agent is broken, not the test. Only modify the test if it was measuring the wrong thing from the start."
- **Actual:** Step 7 describes how to test a candidate fix (apply fix, run with production context, check acceptance thresholds) but has no guidance on whether to fix the skill or modify the test cases when compliance is low.

## Root Cause

Step 7 focuses entirely on the mechanics of testing a candidate fix (apply fix, run trials, check thresholds) but never addresses the decision of what to fix. When compliance is low, agents lack guidance on whether the skill under test is broken or the test cases themselves are flawed. Without this guidance, agents may mask underlying compliance problems by tweaking test prompts instead of fixing the skill.

## Alternatives Considered

### A: Add a examples section to Step 7 (chosen)
Expand Step 7 with concrete examples showing:
- Example 1: "Test expects agent to echo content, agent doesn't → skill is broken"
- Example 2: "Test expected agent to skip a step, but rules say step is mandatory → test was broken from start"
- Example 3: "Test prompt is unclear, causing agent to misunderstand intention → test prompt needs clarification"

- **Risk:** LOW — Documentation-only change; clarifies intent without changing behavior.
- **Chosen because:** Examples make the boundary concrete and actionable.

### B: Add decision tree (rejected)
Create a flowchart for "Is the test broken or is the skill broken?"
- **Risk:** MEDIUM — Complex decision logic in documentation; hard to maintain.
- **Rejected because:** Examples are sufficient and more intuitive.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Documentation-only change; no runtime behavior changes.
- **Mitigation:** Review Step 7 carefully to ensure examples are accurate and cover common cases.

## Files to Modify

- `plugin/skills/empirical-test-agent/first-use.md` — Expand Step 7 with concrete examples and guidance on when test cases should be modified vs when the skill under test should be fixed.

## Related Files to Check (read-only)

- `plugin/skills/instruction-builder-agent/first-use.md` — References empirical testing; verify consistency with updated guidance.
- `plugin/skills/instruction-builder-agent/testing.md` — References empirical testing; verify consistency with updated guidance.

## Jobs

### Job 1
- Read `plugin/skills/empirical-test-agent/first-use.md` in full, focusing on Step 7 ("Test the Fix").
- Locate Step 7 (the `## Step 7: Test the Fix` section, currently at approximately line 307).
- Insert the expanded guidance below into Step 7, immediately after the opening paragraph ("Once the root cause is identified, create the candidate fix and test it in production context:") and before the numbered list ("1. Apply the fix..."). The existing numbered list, code block, and acceptance thresholds table remain unchanged after the inserted content. The new content to insert is:

  **CRITICAL: Fix the skill under test, not the test cases.**

  When compliance is low (e.g., 0% or 33%), the default assumption is that the skill/agent is broken. Do NOT mask
  underlying issues by tweaking test prompts.

  **When to Modify Test Cases:**

  Modify test cases ONLY if the test itself was fundamentally broken from the start. A test is fundamentally broken if:

  **Example 1: Test was measuring the wrong behavior**
  - Original test expected: "Agent skips this step when configured with low trust"
  - But the rule says: "This step is mandatory and cannot be skipped"
  - Fix: Correct the test expectation, not the skill (the skill is correct, the test was wrong)

  **Example 2: Test prompt is ambiguous or contradicts the rule being tested**
  - Test prompt says: "Use tool X"
  - Skill instructions say: "Tool X is not available; use tool Y instead"
  - Fix: Clarify the test prompt (remove the contradiction)

  **When to Fix the Skill (Default):**

  When compliance is low, assume the skill/agent is broken and needs fixing. Common scenarios:

  **Example 3: Agent doesn't follow a clearly documented rule**
  - Rule says: "Always echo content verbatim"
  - Test has agent echo content, but agent is paraphrasing (0% pass rate)
  - Fix: Improve the skill instructions or system prompt so agent follows the rule

  **Example 4: Agent uses wrong approach despite clear guidance**
  - Rule says: "Use tool X to validate the data"
  - Agent is not using tool X even with clear instructions (33% pass rate over 5 trials)
  - Fix: Add stronger instruction or example to the skill

  **Decision Rule:** If the test was written correctly (prompts are clear, expectations match documented rules),
  and compliance is still low, the skill is broken. Fix the skill, not the test.

  Note: Do NOT duplicate the acceptance thresholds table that already exists later in Step 7.

- Read `plugin/skills/instruction-builder-agent/first-use.md` and `plugin/skills/instruction-builder-agent/testing.md`
  and verify their empirical testing references are consistent with the updated guidance. Update if inconsistent.
- Update `.cat/issues/v2/v2.1/clarify-empirical-test-workflow/index.json` to set `status` to `closed` in the same
  commit as the implementation.
- Commit all changes with message: `feature: clarify empirical-test-agent Step 7 guidance on fixing skill vs test`

## Post-conditions

- [ ] Step 7 in `plugin/skills/empirical-test-agent/first-use.md` includes "When to Modify Test Cases" section with examples
- [ ] Step 7 includes "When to Fix the Skill (Default)" section with examples
- [ ] Step 7 includes decision rule: "If test was written correctly and compliance is low, skill is broken"
- [ ] Step 7 retains existing acceptance thresholds table (not duplicated)
- [ ] `plugin/skills/instruction-builder-agent/first-use.md` and `plugin/skills/instruction-builder-agent/testing.md` empirical testing references are consistent with updated guidance
