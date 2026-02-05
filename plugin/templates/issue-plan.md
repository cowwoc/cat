# Issue Plan Templates

Select the appropriate template based on issue type.

---

## Feature Template

```markdown
# Plan: [Issue Name]

## Goal
[1-2 sentences: what this feature accomplishes]

## Satisfies
[List requirement IDs from parent version PLAN.md, or "None" for infrastructure issues]
- REQ-001

## Approaches (optional - include if multiple viable paths exist)

### A: [Approach Name]
- **Risk:** LOW | MEDIUM | HIGH
- **Scope:** N files (minimal | moderate | comprehensive)
- **Description:** [1-2 sentences]

### B: [Approach Name]
- **Risk:** LOW | MEDIUM | HIGH
- **Scope:** N files (minimal | moderate | comprehensive)
- **Description:** [1-2 sentences]

> When multiple approaches exist and user's trust <= medium, the workflow
> calculates config alignment for each approach. If no approach has >= 85%
> alignment, the user is presented with a choice.

## Risk Assessment
- **Risk Level:** [LOW | MEDIUM | HIGH]
- **Concerns:** [potential issues]
- **Mitigation:** [how to address]

## Files to Modify
- path/to/file1.ext - [specific change]
- path/to/file2.ext - [specific change]

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Execution Steps
<!-- ACTIONS ONLY - Do NOT include expected outcomes like "score = 1.0" or "should be X" -->
<!-- Expected values prime subagents to fabricate results instead of running actual validation -->
1. **Step 1:** [action to perform]
   - Files: [paths]
2. **Step 2:** [action to perform]
   ...

## Success Criteria
<!-- MEASURABLE OUTCOMES - What the execution should achieve -->
<!-- These are verified by the orchestrator, NOT passed to subagents -->
- [ ] Criterion 1 with measurable outcome (e.g., "All files achieve EQUIVALENT status")
- [ ] Criterion 2 with threshold (e.g., "Token reduction > 30%")
```

---

## Bugfix Template

```markdown
# Plan: [Issue Name]

## Problem
[1-2 sentences describing the bug]

## Satisfies
[List requirement IDs or "None"]
- REQ-001

## Reproduction Code
\`\`\`
// Minimal code that triggers the bug - REQUIRED
code_that_fails();
\`\`\`

## Expected vs Actual
- **Expected:** [what should happen]
- **Actual:** [error message or wrong behavior]

## Root Cause
[1-2 sentences - analysis or "to be determined"]

## Risk Assessment
- **Risk Level:** [LOW | MEDIUM | HIGH]
- **Regression Risk:** [what could break]
- **Mitigation:** [how to verify]

## Files to Modify
- path/to/file.ext - [specific change]

## Test Cases
- [ ] Original bug scenario - now passes
- [ ] Edge cases - still work

## Execution Steps
<!-- ACTIONS ONLY - Do NOT include expected outcomes -->
1. **Step 1:** [action with specific code changes]
2. **Step 2:** [next action]

## Success Criteria
<!-- MEASURABLE OUTCOMES - verified separately from execution -->
- [ ] All test cases pass
- [ ] No regressions in related functionality
```

---

## Refactor Template

```markdown
# Plan: [Issue Name]

## Current State
[1-2 sentences - what exists now]

## Target State
[1-2 sentences - what it should become]

## Satisfies
[List requirement IDs or "None" for tech debt]
- REQ-001

## Risk Assessment
- **Risk Level:** [LOW | MEDIUM | HIGH]
- **Breaking Changes:** [API/behavior changes]
- **Mitigation:** [tests, incremental steps]

## Files to Modify
- path/to/file.ext - [specific change]

## Execution Steps
<!-- ACTIONS ONLY - Do NOT include expected outcomes -->
1. **Step 1:** [action with before/after patterns]
2. **Step 2:** [next action]

## Success Criteria
<!-- MEASURABLE OUTCOMES - verified separately from execution -->
- [ ] All tests pass after refactoring
- [ ] Code quality metrics maintained or improved
```
