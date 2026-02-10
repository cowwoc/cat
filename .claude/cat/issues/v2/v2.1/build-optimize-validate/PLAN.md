# Plan: build-optimize-validate

## Goal
Update build infrastructure for jackson-core-only dependency, update Java conventions documentation, rebuild jlink
image, and benchmark startup time to verify the performance target (≤55ms with AOTCache).

## Satisfies
Parent: optimize-hook-json-parser (acceptance criteria 1, 5 + conventions update)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** jlink module resolution may need adjustment; benchmark results depend on environment
- **Mitigation:** Verify jlink image builds and launches correctly before measuring

## Files to Modify
- `hooks/build-jlink.sh` - Update jlink module list if needed (jackson-databind modules removed)
- `.claude/cat/conventions/java.md` - Change JSON Library convention from JsonMapper to
  JsonParser/JsonGenerator (jackson-core streaming)

## Acceptance Criteria
- [ ] jlink image builds successfully with jackson-core only
- [ ] jlink image size < 75MB (down from ~92MB)
- [ ] All handler launchers work (verified by build-jlink.sh Phase 6)
- [ ] Hook startup time ≤ 55ms with AOTCache
- [ ] java.md conventions updated to reflect jackson-core streaming API

## Execution Steps
1. **Update build-jlink.sh:** Review and update any jackson-databind-specific module references. The jlink
   `--add-modules` should now only pull in jackson-core transitively.
2. **Update java.md conventions:** Change "JSON Library" from `JsonMapper` to `JsonParser/JsonGenerator
   (jackson-core streaming)`. Update the JsonMapper Usage section to show streaming patterns.
3. **Rebuild jlink image:** Run `hooks/build-jlink.sh` and verify it completes successfully
4. **Measure jlink image size:** Verify < 75MB
5. **Benchmark startup time:** Run handler launchers with AOTCache and measure startup time

## Success Criteria
- [ ] `hooks/build-jlink.sh` completes successfully
- [ ] jlink image size < 75MB
- [ ] Startup time ≤ 55ms with AOTCache
