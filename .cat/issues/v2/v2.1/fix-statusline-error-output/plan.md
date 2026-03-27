# Plan

## Goal

Fix StatuslineCommand error handling to output plain text instead of hook JSON. Currently, when StatuslineCommand.main() catches an error, it calls block(scope, message) which outputs Claude Code hook JSON format ({"decision":"block",...}). But the statusline command should output plain text to stdout — Claude Code displays whatever the script prints. Per the Claude Code docs, "Scripts that exit with non-zero codes or produce no output cause the status line to go blank." The fix should: (1) output a plain-text error indicator (e.g., "⚠ Error: <message>") to stdout on failure instead of hook JSON, and (2) always exit with code 0 so the statusline doesn't go blank.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: StatuslineCommand error handling outputs plain text to stdout instead of hook JSON
- [ ] Regression test added: Tests verify error output format is plain text, not JSON
- [ ] No new issues: Error handling change doesn't break normal statusline output
- [ ] E2E verification: Run statusline-command with invalid input and confirm plain-text error output is displayed
