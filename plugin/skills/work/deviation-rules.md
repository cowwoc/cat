---
user-invocable: false
---

# Work Command: Deviation Rules

Rules for handling deviations from plan, plan changes, and user review checkpoints.

---

## Deviation Rules

During execution, handle discoveries automatically:

1. **Auto-fix bugs** - Fix immediately, document in CHANGELOG
2. **Auto-add critical** - Security/correctness gaps, add and document
3. **Auto-fix blockers** - Can't proceed without fix, do it and document
4. **Ask about architectural** - Major structural changes, stop and ask user
5. **Log enhancements** - Nice-to-haves, propose as new task, continue

Only rule 4 requires user intervention.

---

## Plan Change Checkpoint

**MANDATORY: Announce plan changes BEFORE implementation.**

If during execution you discover the plan needs modification:

1. **STOP** implementation immediately
2. **ANNOUNCE** the change to user with:
   - What the original plan said
   - What needs to change
   - Why the change is needed
3. **WAIT** for user acknowledgment before proceeding
4. **DOCUMENT** the change in commit message under "Deviations from Plan"

**Anti-Pattern (M034):** Changing plan silently and only mentioning it after user asks.

**Examples requiring announcement:**
- Removing a planned feature or flag
- Adding unplanned dependencies
- Changing the approach/architecture
- Skipping planned tests

**Examples NOT requiring announcement:**
- Minor implementation details
- Bug fixes discovered during implementation
- Adding helper methods not in plan

---

## User Review Checkpoint

**MANDATORY: User review before merge (unless trust: "high").**

Before merging any work to main:

1. Present complete summary of changes
2. **WAIT** for explicit approval via AskUserQuestion
3. Require explicit "Approve" response before proceeding
4. If changes are made after initial review, request re-approval

**Required behavior (M035):** Always pause for user review before marking complete.

**User review includes:**
- All files changed with diffs
- All commits with messages
- Token usage and compaction events
- Any deviations from original plan

---

## Duplicate Task Handling

**→ Load duplicate-task.md workflow when task is discovered to be duplicate.**

See `concepts/duplicate-task.md` for full handling including:
- Signs of a duplicate task
- Verification process
- STATE.md resolution format
- Commit message format (no Issue ID footer)
- Cleanup and next task flow

**Quick reference:** Set `resolution: duplicate` and `Duplicate Of: v{major}.{minor}-{original-task}` in STATE.md.
