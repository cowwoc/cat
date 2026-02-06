# Plan: clarify-task-satisfies-parent

## Problem
The task PLAN.md template's `## Satisfies` section says "List requirement IDs from parent minor version PLAN.md" - but
with flexible versioning, a task's parent could be:
- A patch version (v1.0.1)
- A minor version (v1.0)
- A major version (v1)

The documentation incorrectly assumes tasks always belong to minor versions.

## Satisfies
- Supports flexible-version-schema by making task documentation version-agnostic

## Root Cause
The template was written before flexible versioning was added.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - documentation-only change
- **Mitigation:** N/A

## Files to Modify
- plugin/.claude/cat/templates/task-plan.md
  - Change "parent minor version" → "parent version"
- plugin/commands/add.md
  - Update task_select_requirements step to reference "parent version" not "parent minor"
  - Update any other minor-specific language in task workflow

## Acceptance Criteria
- [ ] Template says "parent version" not "parent minor version"
- [ ] add.md task workflow is version-agnostic

## Execution Steps
1. **Update task-plan.md template**
   - Change: "from parent minor version PLAN.md" → "from parent version PLAN.md"
   - Verify: grep confirms change

2. **Update add.md task workflow**
   - Review all task_* steps for minor-specific language
   - Update to be version-agnostic
   - Verify: grep for "minor" in task steps shows none
