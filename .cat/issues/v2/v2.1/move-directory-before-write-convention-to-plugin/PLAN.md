# Plan: move-directory-before-write-convention-to-plugin

## Goal
Move the "directory before file write" convention (M470) from `.claude/rules/common.md` (project-only)
to `InjectSessionInstructions.java` (end-user plugin) so it applies to all CAT users.

## Satisfies
- None (convention relocation)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None
- **Mitigation:** Simple text move

## Files to Modify
- `client/src/main/java/.../InjectSessionInstructions.java` — add convention to Tool Usage Efficiency section
- `.claude/rules/common.md` — remove the convention from Shell Efficiency section

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add "Directory before file write" paragraph to InjectSessionInstructions.java after "Chain independent commands"
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
- Remove the "Directory before file write" paragraph and code block from common.md Shell Efficiency section
  - Files: `.claude/rules/common.md`

## Post-conditions
- [ ] InjectSessionInstructions.java contains "Directory before file write" convention
- [ ] common.md no longer contains "Directory before file write" convention
- [ ] All tests pass
