# Plan: fix-stale-session-cwd-javadoc

## Type
refactor

## Goal
Remove stale `session.cwd` reference from SessionEndHookTest.java Javadoc. The CWD backup/restore
mechanism was removed in commit 0e6778695, but the Javadoc example was missed.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None
- **Mitigation:** Single-line Javadoc edit with no behavior change

## Files to Modify
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java` - Remove `session.cwd` example from class Javadoc (lines 33-34)

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] SessionEndHookTest.java class Javadoc no longer references `session.cwd`

## Sub-Agent Waves

### Wave 1
- Edit `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java`
  - Change line 33: `Session-scoped files (such as {@code session.cwd}) are managed independently by the broader` to `Session-scoped files are managed independently by the broader session cleanup pipeline`
  - Change line 34: `session cleanup pipeline and are not cleaned up by this hook.` → remove (merged into line 33 above)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionEndHookTest.java`
