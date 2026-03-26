# Plan

## Goal

Change `StatuslineCommand`'s constructor to accept a `ClaudeStatusline` instance instead of `JvmScope`.
Create a new `ClaudeStatusline` interface that extends `JvmScope`, declaring only the getter methods
for the statusline data that `StatuslineCommand` needs. The interface should be informed by the
available statusline data at https://code.claude.com/docs/en/statusline#available-data.

## Pre-conditions

(none)

## Post-conditions

- [ ] `ClaudeStatusline` interface created, extends `JvmScope`, declares only the getter methods
  `StatuslineCommand` needs (based on statusline available data docs)
- [ ] `StatuslineCommand` constructor signature changed to accept `ClaudeStatusline` instead of `JvmScope`
- [ ] User-visible statusline behavior unchanged
- [ ] All tests updated and passing, no regressions
- [ ] Code quality improved (StatuslineCommand depends on a narrower, purpose-specific interface)
- [ ] E2E verification: configure `StatuslineCommand` with a `ClaudeStatusline` instance and confirm
  statusline output matches pre-refactor behavior
