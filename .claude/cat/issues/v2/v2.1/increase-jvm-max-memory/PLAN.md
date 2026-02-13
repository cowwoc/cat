# Plan: increase-jvm-max-memory

## Problem

The jlink launcher template and `load-skill.sh` use `-Xmx64m` which may be insufficient for larger hook handlers. The
maximum heap should be increased to 96m.

## Satisfies

None - configuration improvement

## Root Cause

Conservative initial memory setting in `generate_launchers()` in `hooks/build-jlink.sh` and in
`plugin/scripts/load-skill.sh`.

## Execution Steps

1. **Edit `hooks/build-jlink.sh`** — In `generate_launchers()`, change `-Xms16m -Xmx64m` to `-Xms16m -Xmx96m` (line
   301).

2. **Edit `plugin/scripts/load-skill.sh`** — Change `-Xmx64m` to `-Xmx96m` (line 30).

3. **Rebuild jlink image** — Run `hooks/build-jlink.sh` to regenerate launcher scripts.

4. **Verify** — Confirm all launcher scripts contain `-Xmx96m`.

### Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `hooks/build-jlink.sh` | Modify | Change `-Xmx64m` to `-Xmx96m` in launcher template |
| `plugin/scripts/load-skill.sh` | Modify | Change `-Xmx64m` to `-Xmx96m` in JVM invocation |

## Success Criteria

- [ ] All generated launcher scripts contain `-Xmx96m`
- [ ] `load-skill.sh` uses `-Xmx96m`
- [ ] `mvn -f hooks/pom.xml test` passes
