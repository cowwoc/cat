# Plan: Unknown Terminal Fallback

## Goal
When `TerminalType.detect()` returns `UNKNOWN` (e.g. in CI or unsupported terminals), `DisplayUtils`
currently throws an `IOException` crashing the AOT training step and any hook output. Fix by falling back to
the first available terminal's widths and warning the user once per session (suppressed in CI).

## Satisfies
- None (infrastructure fix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Fallback widths may not match the user's terminal exactly; warning marker file I/O failure
- **Mitigation:** Fallback is best-effort; I/O failures are silently ignored (warning may repeat)

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java` - fall back to first terminal,
  expose `isUsingFallbackWidths()` and `getDetectedTerminalKey()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/WarnUnknownTerminal.java` - new handler, emits
  warning once per session via marker file, suppressed in CI (`CI` env var set)
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` - register `WarnUnknownTerminal`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnUnknownTerminalTest.java` - new tests

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Modify `DisplayUtils`:** In `loadEmojiWidthsFromFile`, when terminal key not found, fall back to first
   terminal's widths. Add `usingFallbackWidths` and `detectedTerminalKey` fields + getters. Change return
   type to private `LoadResult` record.
2. **Create `WarnUnknownTerminal`:** `SessionStartHandler` that checks `isUsingFallbackWidths()`, skips if
   `CI` env var set, checks marker file `terminal-warning-emitted` in session dir, emits warning + creates
   marker if not present.
3. **Register in `SessionStartHook`:** Add `new WarnUnknownTerminal(scope)` after `CheckUpdateAvailable`.
4. **Write tests:** `WarnUnknownTerminalTest` with unknown/known terminal cases and marker dedup.
5. **Run `mvn -f client/pom.xml verify`** — all tests pass.

## Post-conditions
- [ ] `DisplayUtils` no longer throws when terminal type is not in `emoji-widths.json`
- [ ] `DisplayUtils.isUsingFallbackWidths()` returns `true` for unknown terminals
- [ ] `WarnUnknownTerminal` handler is registered in `SessionStartHook`
- [ ] Warning is suppressed when `CI` env var is set
- [ ] Warning appears only once per session (marker file dedup)
- [ ] `mvn -f client/pom.xml verify` passes
