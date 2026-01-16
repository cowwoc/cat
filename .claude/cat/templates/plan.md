# Plan Templates

Select the appropriate template based on work type.

**MANDATORY:** All plans must include a Risk Assessment section with:
- **Risk Level:** LOW | MEDIUM | HIGH (required for stakeholder review decisions)
- Concerns/risks specific to the task type
- Mitigation strategy

---

## Feature Template

```markdown
# Plan: [Task Name]

## Goal
[What this feature accomplishes]

## Approaches

### Conservative
[Minimal changes, safest path, may leave some technical debt]
- **Scope:** [what's included/excluded]
- **Risk:** LOW
- **Tradeoff:** [what you give up]

### Balanced
[Reasonable scope, moderate risk, good tradeoffs]
- **Scope:** [what's included/excluded]
- **Risk:** MEDIUM
- **Tradeoff:** [what you give up]

### Aggressive
[Comprehensive solution, higher risk, addresses root causes]
- **Scope:** [what's included/excluded]
- **Risk:** HIGH
- **Tradeoff:** [what you give up]

## Selected Approach
[To be filled after user selection or auto-selection based on preference]

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Concerns:** [potential issues]
- **Mitigation:** [how to address]

## Files to Modify
- path/to/file1.ext - [reason]
- path/to/file2.ext - [reason]

## Dependencies
- [task-name] - [why needed]

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Execution Steps
1. Step 1
2. Step 2
3. Step 3
```

---

## Bugfix Template

```markdown
# Plan: [Task Name]

## Problem
[Description of the bug]

## Root Cause
[Analysis of why the bug occurs]

## Fix Approaches

### Conservative
[Minimal fix, patches the symptom, low risk]
- **Scope:** [what's fixed]
- **Risk:** LOW
- **Tradeoff:** [may not address root cause]

### Balanced
[Fixes root cause in affected area]
- **Scope:** [what's fixed]
- **Risk:** MEDIUM
- **Tradeoff:** [scope limitation]

### Aggressive
[Comprehensive fix, addresses related issues]
- **Scope:** [what's fixed]
- **Risk:** HIGH
- **Tradeoff:** [larger change surface]

## Selected Approach
[To be filled after selection]

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Regression Risk:** [what could break]
- **Mitigation:** [how to verify]

## Files to Modify
- path/to/file.ext - [change description]

## Test Cases
- [ ] Test case 1
- [ ] Test case 2

## Execution Steps
1. Step 1
2. Step 2
```

---

## Refactor Template

```markdown
# Plan: [Task Name]

## Current State
[What exists now]

## Target State
[What it should become]

## Rationale
[Why this refactor is needed]

## Refactor Approaches

### Conservative
[Rename/reorganize only, no behavior changes]
- **Scope:** [what's refactored]
- **Risk:** LOW
- **Tradeoff:** [limited improvement]

### Balanced
[Improve structure and fix obvious issues]
- **Scope:** [what's refactored]
- **Risk:** MEDIUM
- **Tradeoff:** [some areas untouched]

### Aggressive
[Complete redesign of the target area]
- **Scope:** [what's refactored]
- **Risk:** HIGH
- **Tradeoff:** [large change, more testing needed]

## Selected Approach
[To be filled after selection]

## Files to Modify
- path/to/file.ext - [change description]

## Risk Assessment
- **Risk Level:** LOW | MEDIUM | HIGH
- **Breaking Changes:** [API changes, behavior changes]
- **Mitigation:** [incremental approach, tests]

## Execution Steps
1. Step 1
2. Step 2
```
