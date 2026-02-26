# Plan: fix-macos-sed-incompatibility

## Problem
The launcher generation step in `client/build-jlink.sh` uses `sed -i` (GNU syntax) on line 351, which
fails on macOS because BSD sed requires `sed -i ''` (with an explicit empty backup extension).

## Reproduction Code
```
# Run on macOS:
cd client && ./build-jlink.sh
# Error: sed: -I or -i may not be used with stdin
```

## Expected vs Actual
- **Expected:** Launcher scripts generated successfully on both Linux and macOS
- **Actual:** Build fails on macOS with "sed: -I or -i may not be used with stdin"

## Root Cause
Line 351 of `client/build-jlink.sh` uses `sed -i "s|MODULE_CLASS|$main_class|g" "$launcher"`.
GNU sed accepts `-i` with no backup extension; BSD sed (macOS) requires an explicit empty string: `-i ''`.
The portable fix is to write to a temp file and move it, avoiding `sed -i` entirely.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Launcher generation on Linux could break if the temp-file approach has a bug
- **Mitigation:** Existing guard `grep -q "$main_class" "$launcher"` on line 352 detects failure

## Files to Modify
- `client/build-jlink.sh` - Replace `sed -i` on line 351 with a portable temp-file substitution

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** In `client/build-jlink.sh`, replace line 351:
   ```bash
   # Before:
   sed -i "s|MODULE_CLASS|$main_class|g" "$launcher"
   # After (portable — works on GNU sed and BSD sed):
   sed "s|MODULE_CLASS|$main_class|g" "$launcher" > "${launcher}.tmp" && mv "${launcher}.tmp" "$launcher"
   ```
   Files: `client/build-jlink.sh`
2. **Step 2:** Run `mvn -f client/pom.xml test` and verify all tests pass.
3. **Step 3:** Commit with message `bugfix: fix macOS BSD sed incompatibility in build-jlink.sh launcher generation`

## Post-conditions
- [ ] `client/build-jlink.sh` generates launcher scripts correctly on macOS (no sed error)
- [ ] No regressions: launcher generation still works correctly on Linux
- [ ] All Maven tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: CI passes on macOS (macos-latest runner builds jlink bundle successfully)