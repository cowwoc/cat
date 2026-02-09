# Plan: fix-multirelease-jar-detection

## Problem
`build-jlink.sh` does not properly handle multi-release JARs in two locations:
1. `is_automatic_module()` uses `grep -q "^module-info.class$"` which only matches root-level module-info.class, missing
   `META-INF/versions/N/module-info.class` in multi-release JARs like jackson-core-3.0.3.jar and pouch-core-10.3.jar
2. `patch_automatic_module()` does not pass `--multi-release` to jdeps, so any multi-release JAR that slips past
   detection fails with "is a multi-release jar file but --multi-release option is not set"

## Satisfies
None

## Reproduction Code
```bash
# Running mvn -f hooks/pom.xml verify produces warnings:
#   Warning: jdeps failed for jackson-core-3.0.3.jar
#   Warning: jdeps failed for pouch-core-10.3.jar
# Both are multi-release JARs with module-info in META-INF/versions/N/
```

## Expected vs Actual
- **Expected:** Multi-release JARs with versioned module-info.class are recognized as named modules and skipped
- **Actual:** They are misclassified as automatic modules, sent to jdeps which fails without --multi-release

## Root Cause
Two gaps in multi-release JAR handling:
1. Line 116: `grep -q "^module-info.class$"` anchored pattern only matches root-level module-info.class
2. Line 150-152: jdeps invocation lacks `--multi-release` flag for JARs that are multi-release

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Could incorrectly skip a JAR that has module-info.class but still needs patching (unlikely — presence
  of module-info.class means it already has a module descriptor)
- **Mitigation:** Verify jlink build succeeds with zero jdeps warnings and all launchers functional

## Files to Modify
- `hooks/build-jlink.sh` - Fix `is_automatic_module()` grep pattern and add `--multi-release` to jdeps in
  `patch_automatic_module()`

## Acceptance Criteria
- [ ] `is_automatic_module()` returns 1 (not automatic) when module-info.class exists at any path in the JAR (root or
  META-INF/versions/N/)
- [ ] `patch_automatic_module()` passes `--multi-release base` to jdeps for multi-release JARs (defensive fallback for
  any multi-release automatic module that lacks module-info but has versioned entries)
- [ ] `mvn -f hooks/pom.xml verify` produces no "Warning: jdeps failed for" lines
- [ ] jackson-core-3.0.3.jar and pouch-core-10.3.jar are processed without warnings
- [ ] All tests pass
- [ ] jlink image builds successfully with all launchers functional

## Execution Steps
1. **Step 1:** Fix `is_automatic_module()` in `hooks/build-jlink.sh`
   - Files: `hooks/build-jlink.sh`
   - Change grep from `grep -q "^module-info.class$"` to `grep -q "module-info\.class"` so it matches module-info.class
     at any depth (root or META-INF/versions/N/). When found, function returns 1 (not automatic module).
2. **Step 2:** Add multi-release detection and `--multi-release` flag to `patch_automatic_module()`
   - Files: `hooks/build-jlink.sh`
   - This is a defensive fallback: if a multi-release JAR without module-info reaches patch_automatic_module(), jdeps
     needs `--multi-release base` to analyze it. Before the jdeps call, detect if the JAR is multi-release (has
     `META-INF/versions/` entries). If multi-release, add `--multi-release base` to jdeps_args.
3. **Step 3:** Run `mvn -f hooks/pom.xml verify` and confirm no "Warning: jdeps failed for" lines
4. **Step 4:** Verify jlink image builds and all launchers work

## Success Criteria
- [ ] No "Warning: jdeps failed for" lines in mvn verify output
- [ ] All tests pass
- [ ] jlink image builds with all launchers functional
