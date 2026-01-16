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
