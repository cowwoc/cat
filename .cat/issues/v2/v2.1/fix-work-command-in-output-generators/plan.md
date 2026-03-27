# Plan

## Goal

Fix Java output generators suggesting nonexistent /cat:work command - replace with natural language instruction "Work on {issue-id}" instead of slash command references

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: Java output generators no longer suggest `/cat:work` or any slash command in user-facing output
- [ ] Output strings use natural language (e.g., "Work on 2.1-fix-something") instead of `/cat:work 2.1-fix-something`
- [ ] Regression test: Output from GetAddOutput, GetStatusOutput, GetInitOutput, and GetWorkOutput contains natural language next-step instructions, not slash commands
- [ ] No new issues: All existing tests continue to pass
- [ ] E2E verification: Run get-add-output and confirm the "Next:" line uses natural language, not a slash command
- [ ] All Java files that render user-facing output with `/cat:work` references are updated
