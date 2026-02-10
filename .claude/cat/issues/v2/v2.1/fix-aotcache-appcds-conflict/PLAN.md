# Plan: fix-aotcache-appcds-conflict

## Problem
hook.sh adds both `-XX:AOTCache` and `-XX:SharedArchiveFile` JVM flags when both `aot-cache.aot` and `appcds.jsa` files
exist. The JVM rejects this combination with: "Option AOTCache cannot be used at the same time with SharedArchiveFile".
This causes ALL Java hook handlers to fail on every tool invocation.

## Satisfies
None - infrastructure bugfix

## Root Cause
`hook.sh` lines 66-69 use two independent `[[ -f ]]` checks, both of which add their respective flag. AOTCache
supersedes AppCDS entirely (it includes pre-linked classes + method profiles on top of class data sharing), so AppCDS
is dead weight when AOTCache exists.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - AppCDS is strictly a subset of what AOTCache provides
- **Mitigation:** Verify JVM starts successfully after changes

## Files to Modify
- `plugin/hooks/hook.sh` - Remove AppCDS logic, keep only AOTCache
- `hooks/build-jlink.sh` - Remove AppCDS generation from `generate_startup_archives()`
- `.claude/skills/cat-update-hooks/SKILL.md` - Remove AppCDS mention from description

## Test Cases
- [ ] JVM starts successfully with only AOTCache flag
- [ ] Hook handlers execute without errors
- [ ] build-jlink.sh completes without AppCDS step
- [ ] No references to appcds or SharedArchiveFile remain in modified files

## Execution Steps
1. **Edit `plugin/hooks/hook.sh`:** Remove lines 67-69 (the `appcds` variable and its `[[ -f ]]` conditional). Keep only
   the AOTCache check on lines 66 and 68. Remove "AppCDS" from the comment on line 65. Result should be:
   ```bash
   # Enable AOT cache if present (Leyden AOT -> ~8ms startup)
   local aot_cache="${SCRIPT_DIR}/lib/server/aot-cache.aot"
   [[ -f "$aot_cache" ]] && java_opts+=("-XX:AOTCache=$aot_cache")
   ```
2. **Edit `hooks/build-jlink.sh` function `generate_startup_archives()`:**
   - Remove the `appcds_archive` variable declaration (line 257)
   - Remove the entire AppCDS generation block (lines 261-270): the log, `run_all_handlers` with
     `-XX:ArchiveClassesAtExit`, the file check, and the size log
   - Update the function comment (lines 249-253) to remove AppCDS references. New comment:
     ```
     # --- Phase 5: Generate startup optimization archives ---
     #
     # Leyden AOT cache achieving ~8ms cold startup (vs ~300ms baseline):
     #   Pre-linked classes + method profiles, eliminates class init
     ```
   - Keep the Leyden AOT recording and creation logic unchanged
3. **Edit `.claude/skills/cat-update-hooks/SKILL.md`:**
   - Line 19: Change "creates the jlink image with launchers, and generates the AppCDS archive" to
     "creates the jlink image with launchers, and generates the AOT cache"
4. **Run `mvn -f /workspace/hooks/pom.xml verify`** to ensure the build still passes
5. **Update STATE.md** to status: closed, progress: 100% in the same commit as implementation
6. **Commit all changes** with message: `bugfix: remove AppCDS and use only AOTCache for hook JVM startup`

## Success Criteria
- [ ] No references to appcds, AppCDS, or SharedArchiveFile in hook.sh
- [ ] No AppCDS generation in build-jlink.sh
- [ ] build-jlink.sh still generates AOT cache successfully
- [ ] Maven build passes
- [ ] Hook handlers start without JVM errors