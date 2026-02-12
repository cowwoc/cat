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

## Approach: Eliminate hook.sh, use jlink launchers directly

hook.sh provides 5 things. All can be handled without it:

| Capability | Current (hook.sh) | Replacement |
|---|---|---|
| JVM tuning flags | Runtime shell logic | `--add-options` baked into jlink image |
| AOT cache | Dynamic `$SCRIPT_DIR` path | Post-process launchers to inject relative AOT path |
| Timeout | `timeout 30` shell wrapper | Add `timeout` to hooks.json command strings |
| Class name validation | Regex check | Unnecessary — launchers are fixed entry points |
| Name → FQCN mapping | Broken for subpackages | Unnecessary — launchers already know their FQCN |

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** All hook entry points change from `hook.sh ClassName` to `bin/launcher-name`
- **Mitigation:** Verify every hooks.json entry and handler.sh caller works after migration

## Files to Modify
- `hooks/build-jlink.sh` - Add `--add-options` for JVM flags; add post-processing step to inject AOT cache path and
  timeout into generated launcher scripts
- `plugin/hooks/hooks.json` - Change all commands from `hook.sh ClassName` to `timeout 30 bin/launcher-name`
- `plugin/hooks/hook.sh` - Delete this file
- `plugin/skills/status/handler.sh` - Change from `hook.sh skills.RunGetStatusOutput` to `bin/get-status-output`
- `plugin/scripts/get-render-diff.sh` - Change from `hook.sh` invocation to direct launcher call

## Execution Steps

### Step 1: Update build-jlink.sh to bake JVM options into the image

Add `--add-options` to the jlink command (line 237-245) with these flags:
```
--add-options "-Xms16m -Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"
```

- Files: `hooks/build-jlink.sh`

### Step 2: Add post-processing step to inject AOT cache into launchers

After `build_jlink_image()` and `generate_startup_archives()`, add a new phase that post-processes each launcher script
in `${OUTPUT_DIR}/bin/` to inject the AOT cache flag. Each generated launcher looks like:

```sh
#!/bin/sh
JLINK_VM_OPTIONS=
DIR=`dirname $0`
$DIR/java $JLINK_VM_OPTIONS -m io.github.cowwoc.cat.hooks/...
```

Post-process to become:

```sh
#!/bin/sh
DIR=`dirname $0`
AOT_OPTS=""
[ -f "$DIR/../lib/server/aot-cache.aot" ] && AOT_OPTS="-XX:AOTCache=$DIR/../lib/server/aot-cache.aot"
exec timeout "${CAT_JAVA_TIMEOUT:-30}" "$DIR/java" $AOT_OPTS -m io.github.cowwoc.cat.hooks/...
```

This injects: AOT cache detection (conditional), timeout wrapping, and `exec` for clean process replacement.

Skip the `java` and `keytool` binaries — only post-process launcher scripts that invoke `-m io.github.cowwoc.cat.hooks/`.

- Files: `hooks/build-jlink.sh`

### Step 3: Update hooks.json to call launchers directly

Replace every `"${CLAUDE_PLUGIN_ROOT}/hooks/hook.sh ClassName"` with `"${CLAUDE_PLUGIN_ROOT}/hooks/bin/launcher-name"`.

Mapping (from HANDLERS array in build-jlink.sh):

| hooks.json current | hooks.json new |
|---|---|
| `hook.sh GetSkillOutput` | `bin/skill` |
| `hook.sh GetAskPretoolOutput` | `bin/ask-pretool` |
| `hook.sh GetBashPretoolOutput` | `bin/bash-pretool` |
| `hook.sh GetReadPretoolOutput` | `bin/read-pretool` |
| `hook.sh GetEditPretoolOutput` | `bin/edit-pretool` |
| `hook.sh GetWriteEditPretoolOutput` | `bin/write-edit-pretool` |
| `hook.sh GetTaskPretoolOutput` | `bin/task-pretool` |
| `hook.sh GetPosttoolOutput` | `bin/posttool` |
| `hook.sh GetBashPosttoolOutput` | `bin/bash-posttool` |
| `hook.sh GetReadPosttoolOutput` | `bin/read-posttool` |
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
- [ ] Launcher scripts include JVM tuning flags, AOT cache detection, and timeout
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] No regressions in hook invocations
