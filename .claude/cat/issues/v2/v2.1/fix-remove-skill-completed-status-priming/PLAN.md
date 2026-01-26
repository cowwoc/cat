# Plan: fix-remove-skill-completed-status-priming

## Goal
Fix all plugin skill files that use 'completed' as a CAT issue status value, replacing with 'closed' where
appropriate. This removes cognitive anchors that cause agents to write 'complete'/'completed' in STATE.md
files instead of the valid 'closed'.

## Satisfies
None (infrastructure/bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** delegate-agent uses 'completed' for subagent task status (different domain) — must only
  change references that refer to CAT issue status, not subagent completion tracking
- **Mitigation:** Review each occurrence in context before changing

## Files to Modify
- plugin/skills/remove/first-use.md - Replace all 'completed' with 'closed' for CAT issue status:
  - Line 146: "Warn if completed" → "Warn if closed"
  - Line 148: "If status is `completed`" → "If status is `closed`"
  - Line 152: "This issue is already completed" → "This issue is already closed"
  - Line 240: `grep -c "^| .* | completed |"` → `grep -c "^| .* | closed |"` (and rename COMPLETED_ISSUES
    variable to CLOSED_ISSUES for consistency)
- plugin/skills/delegate-agent/first-use.md - Review 'completed' usage (lines 341, 505, 677); change only
  references to CAT issue STATE.md status, leave subagent task tracking as-is if it's a separate domain
- .claude/skills/cat-release-plugin/SKILL.md - Fix 'completed' references for CAT issue status:
  - Line 65: "Generate CHANGELOG from completed issues" → "closed issues"
  - Line 98: `grep -l "status: completed"` → `grep -l "Status:** closed"` (match actual STATE.md format)
  - Line 102: "Found completed issues" → "Found closed issues" (and rename COMPLETED_ISSUES → CLOSED_ISSUES)

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] plugin/skills/remove/first-use.md uses 'closed' as the status check value (not 'completed')
- [ ] No occurrence of 'completed' as a CAT issue status value remains in remove/first-use.md
- [ ] Warning message refers to 'closed' issues consistently
- [ ] COMPLETED_ISSUES variable renamed to CLOSED_ISSUES in remove/first-use.md
- [ ] delegate-agent/first-use.md line 341 uses 'closed' for STATE.md status reference
- [ ] .claude/skills/cat-release-plugin/SKILL.md uses 'closed' for issue status grep and log messages
- [ ] .claude/skills/cat-release-plugin/SKILL.md for loop on line 122 uses $CLOSED_ISSUES (not undeclared $COMPLETED_ISSUES)

## Execution Waves

### Wave 1

### Fix: rename $COMPLETED_ISSUES to $CLOSED_ISSUES in cat-release-plugin/SKILL.md for loop
- In `.claude/skills/cat-release-plugin/SKILL.md` line 122, change
  `for issue_name in $COMPLETED_ISSUES; do` → `for issue_name in $CLOSED_ISSUES; do`
