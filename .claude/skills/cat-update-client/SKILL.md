---
description: Reinstall CAT plugin, build Java client, and install the jlink runtime image into the plugin cache
---

# Update Client

Build the Java client first, then reinstall the CAT plugin and install the updated jlink runtime. This ordering minimizes plugin downtime by doing the longest-running build step first.

**IMPORTANT:** All `claude` CLI commands below require unsetting `CLAUDECODE` to avoid the nested-session guard.

## Worktree vs Main Workspace

If `CLAUDE_PROJECT_DIR` contains `/.cat/work/worktrees/`, you are running in a worktree. After the normal
install (which installs the marketplace/main-workspace version), the worktree's `plugin/` files are copied
over the install path so your in-progress changes take effect immediately. No marketplace modification or
symlinks are needed.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify -Djlink.extra.args=--enable-assertions
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Reinstall the Plugin

Perform the symlink removal, plugin reinstall, and symlink recreation in a **single Bash call** to
avoid hook errors. The symlink must exist when `pre-bash` fires (before the call) and when `post-bash`
fires (after the call). Splitting these into separate calls leaves the symlink absent between calls,
causing hook binary resolution failures.

```bash
# Remove the client symlink before reinstall to prevent claude plugin install from copying it
# into the new cache (which would create a self-referential symlink).
[[ -L "${CLAUDE_PLUGIN_ROOT}/client" ]] && unlink "${CLAUDE_PLUGIN_ROOT}/client"

# Reinstall the plugin from the registered marketplace.
# Uninstall uses ; instead of && so a missing plugin does not block the install.
CLAUDECODE= claude plugin uninstall cat@cat 2>/dev/null ; true
CLAUDECODE= claude plugin install cat@cat || { echo "ERROR: Plugin install failed"; exit 1; }

# Recreate the client symlink immediately so hook binaries resolve for all subsequent Bash calls.
# session-start.sh would recreate it at next session start, but we need it now.
INSTALL_PATH=$(grep -o '"installPath"[[:space:]]*:[[:space:]]*"[^"]*"' \
  "${HOME}/.config/claude/plugins/installed_plugins.json" \
  | grep '/cat/cat/' \
  | sed 's/.*"\([^"]*\)"$/\1/')
if [[ -z "$INSTALL_PATH" ]]; then
  echo "ERROR: Could not determine cat@cat installPath from installed_plugins.json" >&2
  exit 1
fi
ln -sfn "${INSTALL_PATH}/client" "${CLAUDE_PLUGIN_ROOT}/client"
echo "INSTALL_PATH=${INSTALL_PATH}"
```

If any command fails, stop and report the error. Note the printed `INSTALL_PATH` — it is needed for Steps 3–5.

### 2a. Overlay Worktree Plugin Files (worktree only)

If running in a worktree, copy the worktree's `plugin/` files over the install path so in-progress changes
take effect. This overwrites files installed from the marketplace with the worktree version; new files are
added. Run only when `CLAUDE_PROJECT_DIR` contains `/.cat/work/worktrees/`:

```bash
cp -r "${CLAUDE_PROJECT_DIR}/plugin/." "${INSTALL_PATH}/"
echo "Overlaid worktree plugin files from ${CLAUDE_PROJECT_DIR}/plugin/"
```

### 3. Install jlink Runtime Image to Plugin Cache

Using the `INSTALL_PATH` printed in Step 2, remove the old client directory. After the plugin reinstall in
Step 2, `INSTALL_PATH/client` no longer exists (the reinstall wipes it), so this is typically a no-op.
Use `rm -rf` directly — the hook binary is inaccessible at this point because the symlink target was wiped:

```bash
rm -rf "${INSTALL_PATH}/client"
```

Then copy the new jlink image:

```bash
cp -r /workspace/client/target/jlink "${INSTALL_PATH}/client"
```

### 4. Write VERSION file

Stamp the installed runtime with the plugin version so `session-start.sh` knows it's up to date:

```bash
echo "2.1" > "${INSTALL_PATH}/client/VERSION"
```

### 5. Verify

Confirm the jlink runtime works:

```bash
"${INSTALL_PATH}/client/bin/java" -version
```
