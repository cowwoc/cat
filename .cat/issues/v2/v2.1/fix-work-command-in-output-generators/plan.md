# Plan

## Goal

Fix Java output generators suggesting nonexistent /cat:work command - replace with natural language instruction inside a code fence: `Work on {issue-id}` instead of slash command references

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: Java output generators no longer suggest `/cat:work` or any slash command in user-facing output
- [ ] Output strings use backtick-wrapped natural language (e.g., `` `Work on 2.1-fix-something` ``) instead of `` `/cat:work 2.1-fix-something` ``
- [ ] The entire phrase "Work on {version}-{issue-name}" is inside the code fence as a single unit
- [ ] Regression test: Output from GetAddOutput, GetStatusOutput, GetInitOutput, and GetWorkOutput contains backtick-wrapped natural language next-step instructions, not slash commands
- [ ] No new issues: All existing tests continue to pass
- [ ] E2E verification: Run get-add-output and confirm the "Next:" line uses backtick-wrapped natural language, not a slash command
- [ ] All Java files that render user-facing output with `/cat:work` references are updated
