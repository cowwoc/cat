# Plan

## Goal

Update statusline error prefix from class name to 'CAT' — change error format from '⚠ <class name>: <error message>' to '⚠ CAT: <error message>'

## Pre-conditions

(none)

## Post-conditions

- [ ] All statusline error strings in StatuslineCommand.java use the `⚠ CAT:` prefix instead of class name prefixes (StatuslineCommand, MalformedJson, etc.)
- [ ] Test assertions updated to verify the exact `⚠ CAT:` prefix, not just the warning symbol
- [ ] Javadoc in StatuslineCommand.getActiveIssue() updated to reflect `⚠ CAT: <message>` format
- [ ] All tests passing with no regressions
- [ ] E2E verification: statusline errors display `⚠ CAT: <error message>` instead of `⚠ <class name>: <error message>`
