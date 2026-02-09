# Plan: fix-is-automatic-module

## Problem
The `is_automatic_module` function in `hooks/build-jlink.sh` incorrectly classifies some JARs as automatic modules,
causing `jdeps --generate-module-info` to emit warnings like "is a modular JAR file that cannot be specified with the
--generate-module-info option" and "is a multi-release jar file but --multi-release option is not set".

## Satisfies
None

## Reproduction Code
```bash
# Running mvn -f hooks/pom.xml verify produces 6 jdeps warnings for JARs that should be skipped:
#   jackson-annotations-2.20.jar - already a modular JAR
#   jackson-core-3.0.3.jar - multi-release JAR, jdeps fails without --multi-release
#   jackson-databind-3.0.3.jar - missing dependency (tools.jackson.core)
#   pouch-core-10.3.jar - multi-release JAR
#   requirements-annotation-13.2.jar - already a modular JAR
#   requirements-java-13.2.jar - missing dependency
```

## Expected vs Actual
- **Expected:** Already-modular JARs and JARs with known jdeps incompatibilities are skipped silently
- **Actual:** 6 warning lines printed during build for JARs that cannot be patched

## Root Cause
The `is_automatic_module` function (line 109) uses `jar --describe-module` output to detect automatic modules. Two
issues:
1. Some JARs report as "automatic" via `jar --describe-module` but already contain `module-info.class` — jdeps
   rejects these with "is a modular JAR file"
2. The function treats `jar --describe-module` failure (`|| return 0`) as "is automatic", which is too broad

The fix: add a check for the presence of `module-info.class` inside the JAR before classifying it as automatic. If a JAR
already contains `module-info.class`, it is a named module regardless of what `jar --describe-module` reports.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could skip a JAR that actually needs patching, but the presence of module-info.class means it
  already has a module descriptor
- **Mitigation:** Verify jlink build still succeeds with all launchers working

## Files to Modify
- `hooks/build-jlink.sh` - Fix `is_automatic_module` function to check for `module-info.class` in the JAR

## Test Cases
- [ ] JARs with module-info.class are not passed to jdeps --generate-module-info
- [ ] JARs without module-info.class (true automatic modules) are still patched
- [ ] jlink image builds successfully
- [ ] All launchers work

## Execution Steps
1. **Step 1:** Modify `is_automatic_module` in `hooks/build-jlink.sh`
   - Files: `hooks/build-jlink.sh`
   - Add a check at the beginning of the function: use `jar --list --file="$jar" 2>/dev/null | grep -q
     "^module-info.class$"` to detect if the JAR already contains module-info.class
   - If module-info.class exists, return 1 (not automatic) immediately
   - Keep the existing `jar --describe-module` logic as a secondary check for remaining JARs
2. **Step 2:** Run `mvn -f hooks/pom.xml verify` and verify no jdeps warnings for modular JARs
3. **Step 3:** Verify jlink image builds and launchers work

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` produces no "is a modular JAR file" jdeps errors
- [ ] All 318 tests pass
- [ ] jlink image builds successfully with all launchers functional