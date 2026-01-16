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

## Approach
[High-level implementation strategy]

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

## Fix Approach
[How the fix will work]

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
