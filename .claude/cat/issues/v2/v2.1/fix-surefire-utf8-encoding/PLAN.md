# Plan: fix-surefire-utf8-encoding

## Problem

Tests that produce Unicode output (box-drawing characters, emojis) may have encoding issues when run via Maven Surefire.
The surefire plugin configuration in `hooks/pom.xml` does not set `-Dstdout.encoding=UTF-8` in the JVM arguments for
test execution.

## Satisfies

None - bugfix

## Root Cause

The `maven-surefire-plugin` configuration in `hooks/pom.xml` lacks an `<argLine>` element with
`-Dstdout.encoding=UTF-8`. The forked JVM inherits the container's POSIX locale, causing `stdout.encoding` to default
to `ANSI_X3.4-1968` (ASCII).

## Execution Steps

1. **Edit `hooks/pom.xml`** — Add `<argLine>-Dstdout.encoding=UTF-8</argLine>` to the `maven-surefire-plugin`
   `<configuration>` section (around line 134).

2. **Run tests** — `mvn -f hooks/pom.xml verify` to confirm tests pass with the new argument.

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `hooks/pom.xml` | Modify | Add `-Dstdout.encoding=UTF-8` argLine to surefire configuration |

## Success Criteria

- [ ] Surefire configuration includes `-Dstdout.encoding=UTF-8` in argLine
- [ ] `mvn -f hooks/pom.xml verify` passes
