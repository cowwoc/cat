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

### Stage 1: Approach Outlines (lightweight, ~5K tokens)

```markdown
# Plan: [Task Name]

## Goal
[1-2 sentences: what this feature accomplishes]

## Satisfies
[List requirement IDs from parent minor version PLAN.md, or "None" for infrastructure tasks]
- REQ-001
- REQ-002

## Approach Outlines

### 🛡️ Conservative
[1-2 sentences describing minimal scope approach]
- **Risk:** LOW
- **Tradeoff:** [brief - what you give up]

### ⚖️ Balanced
[1-2 sentences describing moderate scope approach]
- **Risk:** MEDIUM
- **Tradeoff:** [brief - what you give up]

### ⚔️ Aggressive
[1-2 sentences describing comprehensive approach]
- **Risk:** HIGH
- **Tradeoff:** [brief - what you give up]
```

### Stage 2: Detailed Spec (after selection, ~20K tokens)

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

### Dependencies
- [task-name] - [why needed]

### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

### Execution Steps
1. **Step 1:** [action]
   - Files: [paths]
   - Action: [specific implementation - what to do, how, what to avoid]
   - Verify: [command to prove it worked]
2. **Step 2:** [action]
   ...
```

---

## Bugfix Template

**MANDATORY (M122):** Bugfix plans MUST include reproduction code. Tasks must be self-contained
and executable without external dependencies (e.g., external codebases, third-party repos).

### Stage 1: Approach Outlines

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

## Fix Approach Outlines

### 🛡️ Conservative
[1 sentence - patches symptom]
- **Risk:** LOW
- **Tradeoff:** [may not address root cause]

### ⚖️ Balanced
[1 sentence - fixes root cause locally]
- **Risk:** MEDIUM
- **Tradeoff:** [scope limitation]

### ⚔️ Aggressive
[1 sentence - comprehensive fix]
- **Risk:** HIGH
- **Tradeoff:** [larger change surface]
```

### Stage 2: Detailed Spec

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

### Stage 1: Approach Outlines

```markdown
# Plan: [Task Name]

## Current State
[1-2 sentences - what exists now]

## Target State
[1-2 sentences - what it should become]

## Satisfies
[List requirement IDs from parent minor version PLAN.md, or "None" for tech debt]
- REQ-001

## Refactor Approach Outlines

### 🛡️ Conservative
[1 sentence - rename/reorganize only]
- **Risk:** LOW
- **Tradeoff:** [limited improvement]

### ⚖️ Balanced
[1 sentence - improve structure]
- **Risk:** MEDIUM
- **Tradeoff:** [some areas untouched]

### ⚔️ Aggressive
[1 sentence - complete redesign]
- **Risk:** HIGH
- **Tradeoff:** [large change surface]
```

### Stage 2: Detailed Spec

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
