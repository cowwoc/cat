# Plan

## Goal

StatuslineCommand.main() does not catch AssertionError thrown during resource initialization (new MainClaudeStatusline). The error propagates to JVM stderr and is invisible to users since Claude Code only displays stdout for statusline commands.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: AssertionError during resource initialization is caught and handled, producing a user-visible error message on stdout
- [ ] Regression test added: Test verifies error handling for resource initialization failures
- [ ] No new issues: No regressions introduced
- [ ] E2E verification: Run the statusline command without CLAUDE_PLUGIN_ROOT set and verify a user-visible error message appears on stdout instead of an invisible stack trace on stderr
