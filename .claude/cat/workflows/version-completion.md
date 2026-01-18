# Workflow: Version Completion

## When to Load

Load this workflow when **all tasks in a minor version are completed** (no pending/in-progress).

## Minor Version Complete

### Check Minor Completion

```bash
# Count pending/in-progress tasks in this minor version
PENDING_COUNT=$(find ".claude/cat/v${MAJOR}/v${MAJOR}.${MINOR}/task" -name "STATE.md" -exec grep -l 'Status.*pending\|Status.*in-progress' {} \; 2>/dev/null | wc -l)

if [[ "$PENDING_COUNT" -eq 0 ]]; then
  MINOR_COMPLETE=true
fi
```

### Check Requirements Satisfaction

**MANDATORY**: Before marking a minor version complete, verify all requirements are satisfied.

> **Note**: This check is implicit and always runs - it is not listed in the Exit gate section.
> Exit gates are for user-defined additional conditions (tests passing, manual sign-off, etc.).

1. **Extract requirements from minor version PLAN.md**:
   - Read `.claude/cat/v${MAJOR}/v${MAJOR}.${MINOR}/PLAN.md`
   - Parse the Requirements table for all REQ-XXX IDs

2. **Collect satisfied requirements from all tasks**:
   - For each completed task in the minor version
   - Read the task's PLAN.md and extract the `## Satisfies` section
   - Build a set of all satisfied requirement IDs

3. **Identify unsatisfied requirements**:
   ```
   unsatisfied = version_requirements - task_satisfied_requirements
   ```

4. **Block completion if unsatisfied requirements exist**:
   - If `unsatisfied` is not empty, display:
     ```
     ⚠️ Cannot complete v{major}.{minor}: unsatisfied requirements

     The following requirements are not satisfied by any completed task:
     - REQ-XXX: [requirement description]
     - REQ-YYY: [requirement description]

     Options:
     1. Add a task to satisfy these requirements
     2. Remove requirements that are no longer needed
     3. Mark requirements as deferred (update PLAN.md)
     ```
   - Use AskUserQuestion to let user choose resolution path
   - Do NOT mark the version as complete until resolved

5. **Proceed if all requirements satisfied**:
   - All must-have requirements must be satisfied
   - should-have and nice-to-have may be deferred with explicit notation

### Celebration and Review Prompt

Display completion celebration:

```
---

## Task Complete

**{task-name}** merged to main.

## 🎉 Minor Version v{major}.{minor} Complete!

All tasks in this minor version are done.

---
```

### Stakeholder Review Option

Use AskUserQuestion:
- header: "Version Complete"
- question: "Would you like to run a stakeholder review on v{major}.{minor}?"
- options:
  - "Run stakeholder review" - Comprehensive multi-perspective quality review
  - "Skip review" - Continue to next version without review
  - "View status first" - Show /cat:status before deciding

**If "Run stakeholder review":**
Invoke `/cat:stakeholder-review .claude/cat/v{major}/v{major}.{minor}`

**If "Skip review":**
Continue with next steps.

---

## Major Version Complete

### Check Major Completion

```bash
# Count incomplete minor versions in this major
INCOMPLETE_MINORS=$(find ".claude/cat/v${MAJOR}" -maxdepth 1 -name "v${MAJOR}.*" -type d | while read dir; do
  [ -f "$dir/STATE.md" ] && ! grep -q 'Status.*completed' "$dir/STATE.md" && echo "$dir"
done | wc -l)

if [[ "$INCOMPLETE_MINORS" -eq 0 ]]; then
  MAJOR_COMPLETE=true
fi
```

### Check Major Requirements Satisfaction

**MANDATORY**: Before marking a major version complete, verify all minor versions have satisfied
their requirements.

1. **For each minor version in the major**:
   - Verify its requirements satisfaction status
   - A major version cannot be complete if any minor has unsatisfied must-have requirements

2. **Aggregate requirements coverage**:
   - Report total requirements across all minors
   - Report satisfaction rate: `{satisfied}/{total} requirements met`

3. **Block completion if any minor has unsatisfied must-have requirements**:
   - Display which minor versions have gaps
   - Require resolution before major completion

### Major Completion Celebration

```
---

## 🏆 Major Version v{major} Complete!

All minor versions in v{major} are done.

---
```

### Major Stakeholder Review Option

Use AskUserQuestion:
- header: "Major Version Complete"
- question: "Would you like to run a comprehensive stakeholder review on v{major}?"
- options:
  - "Run full review" - Review entire major version (Recommended for releases)
  - "Skip review" - Continue to next major version
  - "View status first" - Show /cat:status before deciding

---

## Next Steps After Version Completion

```
Use `/cat:status` to see overall progress.
Use `/cat:add-task` to add more tasks.
Use `/cat:add-minor-version` to add a new minor version.
```

---

## When NOT to Load

- When tasks remain in current version
- During normal task execution
- When finding next task
