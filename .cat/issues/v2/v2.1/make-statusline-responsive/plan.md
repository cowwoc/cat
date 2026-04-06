# Plan: make-statusline-responsive

## Goal
When the `cat:statusline` output exceeds the terminal width, wrap at component boundaries so each
component (e.g., active issue, branch name, status indicator) moves to the next line as a unit
rather than overflowing or being truncated.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Terminal width detection must work correctly; wrapping must not break single-line layouts
- **Mitigation:** Unit tests covering single-line, exact-fit, and multi-line wrap scenarios

## Files to Modify
- `client/src/main/java/.../ClaudeStatusLine.java` (or equivalent statusline renderer) — add
  responsive layout: measure total width of all components, if it exceeds terminal width, wrap
  each component to its own line
- `client/src/test/java/.../ClaudeStatusLineTest.java` (or equivalent) — add tests for:
  single-line layout (fits), exact terminal width (no wrap), overflow triggers per-component wrap

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Locate the Java class that renders the `cat:statusline` output and understand its component model
  - Files: `client/src/main/java/`
- Implement responsive layout: measure combined display width of all components; if total exceeds
  terminal width, render each component on its own line
  - Files: statusline renderer class
- Add unit tests covering: all components fit on one line, exact fit, one component causes overflow
  (wraps all), and each wrap preserves component content intact
  - Files: statusline test class

## Post-conditions
- [ ] When all components fit within terminal width, output is a single line (no change from current behavior)
- [ ] When combined component width exceeds terminal width, each component is rendered on its own line
- [ ] No component content is truncated or dropped during wrapping
- [ ] All existing statusline tests continue to pass
- [ ] E2E: running `cat:statusline` in a narrow terminal (e.g., 40 cols) produces multi-line output with one component per line
