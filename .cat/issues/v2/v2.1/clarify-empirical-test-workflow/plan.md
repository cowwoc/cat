# Plan: clarify-empirical-test-workflow

## Problem

The `cat:empirical-test-agent` Step 7 (Test the Fix) section states "fix the SKILL under test, not test cases. Only modify test cases if they are fundamentally broken" but does not explain what "fundamentally broken" means, leaving agents uncertain whether to fix test prompts or the underlying skill implementation when compliance is low.

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
- **Actual:** Step 7 says "fix the SKILL under test, not test cases. Only modify test cases if fundamentally broken" without defining what makes a test fundamentally broken.

## Root Cause

The boundary between "low compliance indicates the skill needs fixing" and "the test itself was wrong from the start" is ambiguous. Agents cannot reliably distinguish between:
- A skill that violates documented rules (skill needs fixing)
- A test that was always testing the wrong behavior (test needs fixing)

This ambiguity causes agents to mask underlying compliance problems by tweaking test prompts instead of fixing the skill.

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

- `plugin/skills/cat-empirical-test-agent/first-use.md` — Expand Step 7 with concrete examples and guidance on when test cases should be modified vs when the skill under test should be fixed.

## Related Files to Check (read-only)

- `plugin/skills/cat-instruction-builder-agent/first-use.md` — References empirical testing; verify consistency with updated guidance.

## Test Cases

- [ ] Step 7 includes at least 2 examples of low-compliance scenarios where skill is broken
- [ ] Step 7 includes at least 1 example where the test itself was fundamentally broken
- [ ] Examples clearly show the distinction between "skill needs fixing" and "test was wrong from start"
- [ ] E2E: When an agent encounters low compliance, it can decide whether to fix the skill or the test using documented criteria

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read `plugin/skills/cat-empirical-test-agent/first-use.md` in full, focusing on Step 7 ("Test the Fix").
  - Locate Step 7 (find the section starting "## Step 7: Test the Fix").
  - Replace the current guidance with the following expanded section:

    ```
    ## Step 7: Test the Fix

    Once the root cause is identified and a candidate fix is applied, validate the fix with higher trial count
    (10–15 trials) in production context. The production context must match the real failure scenario: use actual
    file content, include full priming, and use the same system prompt as a real session.

    **CRITICAL: Fix the skill under test, not the test cases.**

    When compliance is low (e.g., 0% or 33%), the default assumption is that the skill/agent is broken. Do NOT mask
    underlying issues by tweaking test prompts.

    ### When to Modify Test Cases

    Modify test cases ONLY if the test itself was fundamentally broken from the start. A test is fundamentally broken if:

    **Example 1: Test was measuring the wrong behavior**
    - Original test expected: "Agent skips this step when configured with low trust"
    - But the rule says: "This step is mandatory and cannot be skipped"
    - Fix: Correct the test expectation, not the skill (the skill is correct, the test was wrong)

    **Example 2: Test prompt is ambiguous or contradicts the rule being tested**
    - Test prompt says: "Use tool X"
    - Skill instructions say: "Tool X is not available; use tool Y instead"
    - Fix: Clarify the test prompt (remove the contradiction)

    ### When to Fix the Skill (Default)

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

    ### Acceptance Thresholds

    | Rate | Decision |
    |------|----------|
    | 90–100% | Fix is effective, proceed to commit |
    | 70–89% | Fix helps but may need additional changes |
    | Below 70% | Fix is insufficient, return to isolation and try a different root cause |
    ```

  - Verify that the updated Step 7 maintains all references and context from the original skill instructions.

- Read `plugin/skills/cat-instruction-builder-agent/first-use.md` and verify its empirical testing references are
  consistent with the updated guidance. Update if inconsistent.

- Commit all changes with message: `docs: clarify empirical-test-agent Step 7 guidance on fixing skill vs test`

## Post-conditions

- [ ] Step 7 in `cat-empirical-test-agent/first-use.md` includes "When to Modify Test Cases" section with examples
- [ ] Step 7 includes "When to Fix the Skill (Default)" section with examples
- [ ] Step 7 includes decision rule: "If test was written correctly and compliance is low, skill is broken"
- [ ] Step 7 includes acceptance thresholds table
- [ ] `cat-instruction-builder-agent/first-use.md` empirical testing references are consistent with updated guidance
