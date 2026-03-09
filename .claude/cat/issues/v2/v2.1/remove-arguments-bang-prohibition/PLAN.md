# Plan: remove-arguments-bang-prohibition

## Current State

`skill-conventions.md` contains a "Shell Safety with `$ARGUMENTS`" section (lines ~1133–1187) warning
that placing `$ARGUMENTS` inside `[BANG]` shell commands is dangerous because the shell interprets
special characters, mangling the arguments.

## Target State

The prohibition is removed. `skill-conventions.md` accurately reflects that `[BANG]` directives are
processed entirely by Java (not bash), so `$ARGUMENTS` inside a `[BANG]` command carries no shell-
interpretation risk.

## Parent Requirements

None

## Investigation Summary

Full investigation of `GetSkill.java` confirmed that `!` backtick preprocessor directives are handled
entirely in Java:

1. **`expandDirectiveString()`** substitutes `$ARGUMENTS` via `String.replace("$ARGUMENTS",
   String.join(" ", skillArgs))` — pure Java string replacement, no shell.
2. **`ShellParser.tokenize()`** splits the expanded string into tokens — a Java class, not bash.
3. **`executeDirective()`** extracts the Java class name from the launcher script and invokes it via
   reflection — no `ProcessBuilder`, no `Runtime.exec()`, no bash.

The "test results" in the current section (showing mangled output) describe bash shell behavior that
never applies to CAT's preprocessor. The prohibition is based on a false premise.

## Approaches

### A: Remove prohibition sections entirely (Chosen)

- **Risk:** LOW
- **Scope:** 1 file (~15 lines removed)
- **Description:** Delete the "Shell Safety" subsection, the dangerous-pattern example from "Safe
  Patterns", and the checklist item. The overall `$ARGUMENTS` section remains for valid content
  (markdown-context use, skill-to-skill call guidance).

### B: Replace prohibition with accurate explanation

- **Risk:** LOW
- **Scope:** 1 file (~15 lines replaced with ~5 lines)
- **Description:** Keep a brief note stating that `[BANG]` directives use Java preprocessing so
  `$ARGUMENTS` is safe in that context. Adds accurate documentation.
- **Rejected:** Adding a note explaining the Java internals couples skill-conventions.md to an
  implementation detail that may change. Better to document the convention (what's allowed) than
  the mechanism (why it's allowed). Remove the false prohibition without replacement.

### C: Document Java-only preprocessing in a new dedicated section

- **Risk:** LOW
- **Scope:** 1 file (add ~10 lines)
- **Description:** Add a new section explaining the Java-based preprocessing architecture.
- **Rejected:** Over-engineering. Conventions should say what to do, not explain the runtime
  implementation. The implementation detail is covered in `skill-loading.md`.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — this is documentation-only. No behavior changes.
- **Mitigation:** The existing `get-output-agent` uses `$ARGUMENTS` in a `[BANG]` command and
  already works correctly; removing the prohibition doesn't change any runtime behavior.

## Files to Modify

- `plugin/skills/skill-builder-agent/skill-conventions.md` — remove the prohibition sections:
  - Table row: `| $ARGUMENTS in [BANG] shell command | Shell interprets | **Dangerous** |`
  - The entire "### Shell Safety with `$ARGUMENTS`" subsection (including the "Test results" block)
  - The `[BANG]`process-input.sh "$ARGUMENTS"`` example from "Safe Patterns" (lines ~1167–1173)
  - Checklist item: `- [ ] $ARGUMENTS only appears in markdown context, not inside [BANG] commands`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Remove the prohibition on `$ARGUMENTS` in `[BANG]` commands from `skill-conventions.md`:
  - In the `## Skill Arguments and $ARGUMENTS` section, edit the arguments-flow table to remove
    the row `| $ARGUMENTS in [BANG] shell command | Shell interprets | **Dangerous** |`
  - Delete the entire `### Shell Safety with $ARGUMENTS` subsection (from `### Shell Safety` heading
    through the closing `---` separator, roughly lines 1135–1188)
  - In the `### Safe Patterns` subsection, keep the markdown-context example and the skill-to-skill
    example, but remove the "For shell processing, use controlled inputs" paragraph and its code
    block (the `❌ Dangerous` / `✓ Script controls input` example)
  - In `### Checklist`, remove the item: `$ARGUMENTS only appears in markdown context, not inside
    [BANG] commands`
  - Files: `plugin/skills/skill-builder-agent/skill-conventions.md`
- Commit: `refactor: remove false prohibition on $ARGUMENTS in [BANG] commands from skill-conventions`
- Update STATE.md: status=closed, progress=100%

## Post-conditions

- [ ] `skill-conventions.md` contains no prohibition or warning against `$ARGUMENTS` inside `[BANG]`
  commands
- [ ] The checklist item `$ARGUMENTS only appears in markdown context, not inside [BANG] commands` is
  absent from `skill-conventions.md`
- [ ] The "Shell Safety with `$ARGUMENTS`" subsection is absent from `skill-conventions.md`
- [ ] All remaining content in `skill-conventions.md` is preserved and unaltered
- [ ] E2E: Invoke `cat:get-output-agent` with args `"<catAgentId> status"` and confirm it returns
  correct status output (verifying `$ARGUMENTS` in a `[BANG]` directive continues to work)
