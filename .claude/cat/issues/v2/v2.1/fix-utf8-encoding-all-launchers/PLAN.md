# Plan: fix-utf8-encoding-all-launchers

## Problem

Unicode characters (box-drawing, emojis) render as `?` in `/cat:status` output. The container locale is POSIX (ASCII),
so JDK defaults `stdout.encoding` to `ANSI_X3.4-1968`. The previous fix (`fix-jlink-utf8-encoding`) added
`-Dstdout.encoding=UTF-8` to the jlink launcher template in `build-jlink.sh`, but missed:

1. `plugin/scripts/load-skill.sh` — the actual path `/cat:status` uses via SKILL.md `!` preprocessing. The cached
   version lacks `-Dstdout.encoding=UTF-8` entirely.
2. All launchers lack `-Dstdin.encoding=UTF-8` and `-Dstderr.encoding=UTF-8`.

Additionally, `RunGetStatusOutput` is a trivial wrapper that duplicates `GetStatusOutput`'s logic. Per java.md
convention (main() in business logic classes), the `main()` method belongs directly in `GetStatusOutput`.

## Satisfies

None - bugfix + cleanup

## Root Cause

1. `load-skill.sh` invokes java without `-Dstdout.encoding=UTF-8`, so `SkillLoader` outputs ASCII-encoded text.
2. The jlink launcher template only sets `stdout.encoding`, missing `stdin` and `stderr`.

## Execution Steps

1. **Edit `plugin/scripts/load-skill.sh`** — Add all three encoding flags to the java invocation:
   ```sh
   "$CLAUDE_PLUGIN_ROOT/hooks/bin/java" \
     -Xms16m \
     -Xmx96m \
     -Dstdin.encoding=UTF-8 \
     -Dstdout.encoding=UTF-8 \
     -Dstderr.encoding=UTF-8 \
     -XX:+UseSerialGC \
     -XX:TieredStopAtLevel=1 \
     -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.util.SkillLoader \
   ```

2. **Edit `hooks/build-jlink.sh`** — In the `generate_launchers()` function, update the launcher template heredoc to
   include all three encoding flags:
   ```sh
   exec "$DIR/java" \
     -Xms16m -Xmx96m \
     -Dstdin.encoding=UTF-8 \
     -Dstdout.encoding=UTF-8 \
     -Dstderr.encoding=UTF-8 \
     -XX:+UseSerialGC \
   ```

3. **Merge `RunGetStatusOutput` into `GetStatusOutput`** — Move the `main()` method from `RunGetStatusOutput` into
   `GetStatusOutput`, following the java.md convention (main() in business logic classes). Delete
   `RunGetStatusOutput.java`. Update `build-jlink.sh` HANDLERS array entry for `get-status-output` to reference
   `GetStatusOutput` instead of `RunGetStatusOutput`.

4. **Rebuild jlink image** — Run `hooks/build-jlink.sh` to regenerate all launcher scripts.

5. **Verify** — Run `mvn -f hooks/pom.xml verify` to ensure tests pass.

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `plugin/scripts/load-skill.sh` | Modify | Add `-Dstdin.encoding=UTF-8` and `-Dstderr.encoding=UTF-8` |
| `hooks/build-jlink.sh` | Modify | Add stdin/stderr encoding flags to launcher template; update HANDLERS entry |
| `hooks/src/main/java/.../skills/GetStatusOutput.java` | Modify | Add `main()` method from RunGetStatusOutput |
| `hooks/src/main/java/.../skills/RunGetStatusOutput.java` | Delete | Merged into GetStatusOutput |

## Success Criteria

- [ ] `load-skill.sh` contains all three `-D*encoding=UTF-8` flags
- [ ] All generated launcher scripts contain all three encoding flags
- [ ] `RunGetStatusOutput.java` is deleted; `GetStatusOutput` has `main()`
- [ ] `get-status-output` launcher references `GetStatusOutput` class
- [ ] `mvn -f hooks/pom.xml verify` passes
