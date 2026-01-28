# Plan Templates

Select the appropriate template based on work type.

**MANDATORY:** All plans must include a Risk Assessment section with:
- **Risk Level:** LOW | MEDIUM | HIGH (required for stakeholder review decisions)
- Concerns/risks specific to the task type
- Mitigation strategy

---

## Minor Version PLAN.md Template

Minor version PLAN.md defines the requirements for all tasks within that version.
Tasks reference these requirements via their `Satisfies` field.

```markdown
# Plan: v{major}.{minor} - [Version Name]

## Overview
[1-2 sentences: what this version accomplishes]

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | [requirement description] | must-have | [how to verify] |
| REQ-002 | [requirement description] | should-have | [how to verify] |
| REQ-003 | [requirement description] | nice-to-have | [how to verify] |

## Research

*Populated by stakeholder research during version planning. If empty, run `/cat:research`.*

### Stack
| Library | Purpose | Version | Rationale |
|---------|---------|---------|-----------|
| [lib] | [why needed] | [ver] | [why this one] |

### Architecture
- **Pattern:** [recommended pattern]
- **Integration:** [how it fits existing code]

### Pitfalls
- [pitfall 1]: [prevention]
- [pitfall 2]: [prevention]

## Tasks Overview
[Brief description of how tasks divide the work for this version]
```

---

## Task Templates

All task templates include a `Satisfies` field referencing requirements from the parent
minor version's PLAN.md. Tasks may satisfy zero or more requirements.

---

## Feature Template

```markdown
# Plan: [Task Name]

## Goal
[1-2 sentences: what this feature accomplishes]

## Satisfies
[List requirement IDs from parent minor version PLAN.md, or "None" for infrastructure tasks]
- REQ-001
- REQ-002

## Risk Assessment
- **Risk Level:** [LOW | MEDIUM | HIGH]
- **Concerns:** [potential issues]
- **Mitigation:** [how to address]

## Files to Modify
- path/to/file1.ext - [specific change]
- path/to/file2.ext - [specific change]

## Dependencies
- [task-name] - [why needed]

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Execution Steps

**TDD Required:** First step must create failing tests for acceptance criteria.

1. **Write Tests First:**
   - Files: tests/... (test file paths)
   - Action: Create tests that verify acceptance criteria
   - Verify: Tests fail (functionality not implemented yet)
2. **Implement Feature:**
   - Files: [implementation paths]
   - Action: [specific implementation]
   - Verify: Tests now pass
3. **Additional Steps:** [if needed]
   ...
```

---

## Bugfix Template

**MANDATORY (M122):** Bugfix plans MUST include reproduction code. Tasks must be self-contained
and executable without external dependencies (e.g., external codebases, third-party repos).

```markdown
# Plan: [Task Name]

## Problem
[1-2 sentences describing the bug]

## Satisfies
[List requirement IDs from parent minor version PLAN.md, or "None" for standalone fixes]
- REQ-001

## Reproduction Code
\`\`\`
// Minimal code that triggers the bug - REQUIRED
// Use appropriate language for your project
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

**TDD Required:** First step must create failing test that reproduces the bug.

1. **Write Failing Test:**
   - Files: tests/... (test file path)
   - Action: Create test using reproduction code above
   - Verify: Test fails (bug confirmed)
2. **Fix Bug:**
   - Files: [implementation paths]
   - Action: [specific fix]
   - Verify: Test now passes
3. **Add Edge Case Tests:** [if needed]
   - Verify: All tests pass
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
[List requirement IDs from parent minor version PLAN.md, or "None" for tech debt]
- REQ-001

## Risk Assessment
- **Risk Level:** [LOW | MEDIUM | HIGH]
- **Breaking Changes:** [API/behavior changes]
- **Mitigation:** [tests, incremental steps]

## Files to Modify
- path/to/file.ext - [specific change]

## Execution Steps

**TDD Required:** First step must verify existing tests pass, then refactor.

1. **Verify Baseline:**
   - Action: Run existing tests to establish baseline
   - Verify: All tests pass before refactoring
2. **Refactor Incrementally:**
   - Files: [paths with before/after patterns]
   - Action: [specific refactor]
   - Verify: Tests still pass after each change
3. **Add Tests for New Patterns:** [if behavior exposed]
   - Verify: Coverage maintained or improved
```
