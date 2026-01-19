# Task Plan Templates

Select the appropriate template based on task type.

---

## Feature Template

```markdown
# Plan: [Task Name]

## Goal
[1-2 sentences: what this feature accomplishes]

## Satisfies
[List requirement IDs from parent minor version PLAN.md, or "None" for infrastructure tasks]
- REQ-001

## Approach Outlines

### Conservative
[1-2 sentences describing minimal scope approach]
- **Risk:** LOW
- **Tradeoff:** [what you give up]

### Balanced
[1-2 sentences describing moderate scope approach]
- **Risk:** MEDIUM
- **Tradeoff:** [what you give up]

### Aggressive
[1-2 sentences describing comprehensive approach]
- **Risk:** HIGH
- **Tradeoff:** [what you give up]
```

After approach selection, add:

```markdown
## Selected Approach
[Conservative | Balanced | Aggressive]

## Detailed Implementation

### Risk Assessment
- **Risk Level:** [from selected approach]
- **Concerns:** [potential issues]
- **Mitigation:** [how to address]

### Files to Modify
- path/to/file1.ext - [specific change]
- path/to/file2.ext - [specific change]

### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

### Execution Steps
1. **Step 1:** [action]
   - Files: [paths]
   - Verify: [command to prove it worked]
```

---

## Bugfix Template

```markdown
# Plan: [Task Name]

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

## Fix Approach Outlines

### Conservative
[1 sentence - patches symptom]
- **Risk:** LOW
- **Tradeoff:** [may not address root cause]

### Balanced
[1 sentence - fixes root cause locally]
- **Risk:** MEDIUM
- **Tradeoff:** [scope limitation]

### Aggressive
[1 sentence - comprehensive fix]
- **Risk:** HIGH
- **Tradeoff:** [larger change surface]
```

After approach selection, add:

```markdown
## Selected Approach
[Conservative | Balanced | Aggressive]

## Detailed Fix

### Risk Assessment
- **Risk Level:** [from selected]
- **Regression Risk:** [what could break]
- **Mitigation:** [how to verify]

### Files to Modify
- path/to/file.ext - [specific change]

### Test Cases
- [ ] Original bug scenario - now passes
- [ ] Edge cases - still work

### Execution Steps
1. **Step 1:** [action with specific code changes]
   - Verify: [test command]
```

---

## Refactor Template

```markdown
# Plan: [Task Name]

## Current State
[1-2 sentences - what exists now]

## Target State
[1-2 sentences - what it should become]

## Satisfies
[List requirement IDs or "None" for tech debt]
- REQ-001

## Refactor Approach Outlines

### Conservative
[1 sentence - rename/reorganize only]
- **Risk:** LOW
- **Tradeoff:** [limited improvement]

### Balanced
[1 sentence - improve structure]
- **Risk:** MEDIUM
- **Tradeoff:** [some areas untouched]

### Aggressive
[1 sentence - complete redesign]
- **Risk:** HIGH
- **Tradeoff:** [large change surface]
```

After approach selection, add:

```markdown
## Selected Approach
[Conservative | Balanced | Aggressive]

## Detailed Refactor

### Risk Assessment
- **Risk Level:** [from selected]
- **Breaking Changes:** [API/behavior changes]
- **Mitigation:** [tests, incremental steps]

### Files to Modify
- path/to/file.ext - [specific change]

### Execution Steps
1. **Step 1:** [action with before/after patterns]
   - Verify: [tests pass]
```
