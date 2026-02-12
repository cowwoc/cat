# Plan: fix-hook-subpackage-resolution

## Problem
hook.sh incorrectly treats handler class names containing dots (e.g., `skills.RunGetStatusOutput`) as fully-qualified
class names, passing them directly to the JVM. Rather than patching the name-mapping logic, eliminate hook.sh entirely by
baking its capabilities into the jlink launcher scripts.

## Satisfies
None - infrastructure bugfix

## Reproduction Code
```bash
# This fails with: Could not find or load main class skills.RunGetStatusOutput
${PLUGIN_ROOT}/hooks/hook.sh skills.RunGetStatusOutput
```

## Expected vs Actual
- **Expected:** Java handler executes successfully
- **Actual:** Class not found because hook.sh passes `skills.RunGetStatusOutput` as-is instead of prepending the module
  package prefix

## Root Cause
hook.sh has a flawed dot-detection heuristic (line 50) that assumes any name with a dot is already fully qualified. But
the real problem is that hook.sh is an unnecessary indirection layer — jlink already generates per-handler launcher
scripts that know their exact FQCN.

## Approach: Eliminate hook.sh, generate custom launchers

hook.sh provides 5 things. All can be handled without it:

| Capability | Current (hook.sh) | Replacement |
|---|---|---|
| JVM tuning flags | Runtime shell logic | Build-generated launcher scripts |
| AOT cache | Dynamic `$SCRIPT_DIR` path | Build-generated launcher scripts with `$DIR`-relative path |
| Timeout | `timeout 30` shell wrapper | Unnecessary — Claude Code enforces its own hook timeout (default 60s) |
| Class name validation | Regex check | Unnecessary — launchers are fixed entry points |
| Name → FQCN mapping | Broken for subpackages | Unnecessary — launchers already know their FQCN |

**Why not `--add-options`?** jlink's `--add-options` bakes literal strings into `lib/modules`. It cannot resolve shell
variables like `$DIR` at runtime, so it cannot reference the AOT cache at a path relative to the image. Tested: the JVM
resolves `-XX:AOTCache` relative to CWD, not `java.home`, so a literal relative path fails when invoked from a different
directory.

**Why not `--launcher` + post-processing?** jlink's `--launcher` generates rigid 4-line shell scripts. Post-processing
them with sed is fragile — tied to jlink's exact output format which can change across JDK versions. Since we already
have the HANDLERS array with every name:FQCN mapping, we can generate launchers directly with the exact content needed.

**Approach:** Skip both `--add-options` and `--launcher`. Build the jlink image for the runtime only. Then generate our
own launcher scripts from the HANDLERS array using a simple template.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** All hook entry points change from `hook.sh ClassName` to `bin/launcher-name`
- **Mitigation:** Verify every hooks.json entry and handler.sh caller works after migration

## Files to Modify
- `hooks/build-jlink.sh` - Remove `--launcher` and `--add-options`; replace `post_process_launchers()` with
  `generate_launchers()` that writes scripts from a template using the HANDLERS array
- `plugin/hooks/hooks.json` - Change all commands from `hook.sh ClassName` to `bin/launcher-name`
- `plugin/hooks/hook.sh` - Delete this file
- `plugin/skills/status/handler.sh` - Change from `hook.sh skills.RunGetStatusOutput` to `bin/get-status-output`
- `plugin/scripts/get-render-diff.sh` - Change from `hook.sh` invocation to direct launcher call

## Execution Steps

### Step 1: Replace `--launcher` and `--add-options` with `generate_launchers()`

In `hooks/build-jlink.sh`:

**Remove from `build_jlink_image()`:**
- The `launcher_args` array construction loop
- The `"${launcher_args[@]}"` argument to jlink
- The `--add-options` argument to jlink (if present)

**Replace `post_process_launchers()` with `generate_launchers()`:**

Add a new function that generates launcher scripts directly from the HANDLERS array. Call it after
`generate_startup_archives()` (so the AOT cache file exists). Each launcher follows this template:

```sh
#!/bin/sh
DIR=`dirname $0`
exec "$DIR/java" \
  -Xms16m -Xmx64m \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \
  -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.CLASS_NAME "$@"
```

The function iterates the HANDLERS array, extracts the launcher name and class name, uses `handler_main()` to get the
full module/class path, writes the script to `${OUTPUT_DIR}/bin/${name}`, and makes it executable.

- Files: `hooks/build-jlink.sh`

### Step 3: Update hooks.json to call launchers directly

Replace every `"${CLAUDE_PLUGIN_ROOT}/hooks/hook.sh ClassName"` with `"${CLAUDE_PLUGIN_ROOT}/hooks/bin/launcher-name"`.

Mapping (from HANDLERS array in build-jlink.sh):

| hooks.json current | hooks.json new |
|---|---|
| `hook.sh GetSkillOutput` | `bin/skill` |
| `hook.sh GetAskPretoolOutput` | `bin/before-ask` |
| `hook.sh GetBashPretoolOutput` | `bin/before-bash` |
| `hook.sh GetReadPretoolOutput` | `bin/before-read` |
| `hook.sh GetEditPretoolOutput` | `bin/before-edit` |
| `hook.sh GetWriteEditPretoolOutput` | `bin/before-write-edit` |
| `hook.sh GetTaskPretoolOutput` | `bin/before-task` |
| `hook.sh GetPosttoolOutput` | `bin/after-tool` |
| `hook.sh GetBashPosttoolOutput` | `bin/after-bash` |
| `hook.sh GetReadPosttoolOutput` | `bin/after-read` |
| `hook.sh EnforceStatusOutput` | `bin/enforce-status` |
| `hook.sh GetSessionEndOutput` | `bin/get-session-end-output` |

- Files: `plugin/hooks/hooks.json`

### Step 4: Update handler scripts that call hook.sh

**status/handler.sh:** Replace `"${PLUGIN_ROOT}/hooks/hook.sh" skills.RunGetStatusOutput` with
`"${PLUGIN_ROOT}/hooks/bin/get-status-output"`

**scripts/get-render-diff.sh:** Replace hook.sh invocation with direct launcher call to the appropriate launcher.

- Files: `plugin/skills/status/handler.sh`, `plugin/scripts/get-render-diff.sh`

### Step 5: Delete hook.sh

Remove `plugin/hooks/hook.sh`.

- Files: `plugin/hooks/hook.sh`

### Step 6: Rebuild jlink image and install to plugin cache

```bash
mvn -f hooks/pom.xml verify
cp -r hooks/target/jlink/* /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/
rm -f /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/hook.sh
```

### Step 7: Verify all hooks work

Test that status skill works: `CLAUDE_PROJECT_DIR=/workspace /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/bin/get-status-output`

Test a hooks.json entry works: `echo '{}' | /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/bin/bash-pretool`

## Success Criteria
- [ ] `bin/get-status-output` executes successfully and produces status output
- [ ] All hooks.json entries invoke launchers directly (no hook.sh references remain)
- [ ] hook.sh is deleted from plugin/hooks/
- [ ] Launcher scripts include JVM tuning flags and AOT cache path (generated from template, not post-processed)
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] No regressions in hook invocations
