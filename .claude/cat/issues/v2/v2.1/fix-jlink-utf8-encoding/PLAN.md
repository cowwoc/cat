# Plan: fix-jlink-utf8-encoding

## Problem

Unicode characters (box-drawing ╭│╮╰╯─, emojis 📊🔄☑️🔳🚫📋📦🤖) in hook output render as `?` in Claude Code's
terminal display. The jlink launcher scripts start the JVM without `-Dstdout.encoding=UTF-8`. The container locale is
POSIX (ASCII), causing JDK 25 to set `stdout.encoding=ANSI_X3.4-1968`. While `file.encoding` defaults to UTF-8 per
JEP 400, `stdout.encoding` is derived from the native console locale separately. The JVM's `System.out` PrintStream
encodes with ASCII and irreversibly replaces unmappable characters with `0x3F` (`?`) before bytes leave the process.

Python scripts did not have this problem because Python 3.7+ (PEP 540) enables UTF-8 mode automatically when the
locale is POSIX, overriding the ASCII default for stdout.

## Satisfies

None - bugfix

## Root Cause

`generate_launchers()` in `hooks/build-jlink.sh` (lines 297-306) generates launcher scripts without
`-Dstdout.encoding=UTF-8`. The JVM defaults `stdout.encoding` from the native locale, which is POSIX/ASCII in the
container.

## Execution Steps

1. **Edit `hooks/build-jlink.sh`** — In the `generate_launchers()` function, add `-Dstdout.encoding=UTF-8` to the
   launcher template heredoc (line 301, between `-Xms16m -Xmx64m` and `-XX:+UseSerialGC`):

   ```sh
   exec "$DIR/java" \
     -Xms16m -Xmx64m \
     -Dstdout.encoding=UTF-8 \
     -XX:+UseSerialGC \
   ```

2. **Rebuild the jlink image** — Run `hooks/build-jlink.sh` to regenerate all 18 launcher scripts with the new flag.

3. **Verify** — Run `echo '{}' | hooks/target/jlink/bin/get-status-output 2>/dev/null | head -3` and confirm
   box-drawing characters appear instead of `?`.

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `hooks/build-jlink.sh` | Modify | Add `-Dstdout.encoding=UTF-8` to launcher template in `generate_launchers()` |

## Success Criteria

- [ ] All 18 generated launcher scripts contain `-Dstdout.encoding=UTF-8`
- [ ] `get-status-output` produces valid UTF-8 bytes (box-drawing chars, emojis) instead of `0x3F`
- [ ] `mvn -f hooks/pom.xml test` passes
