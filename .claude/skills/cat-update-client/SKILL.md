---
description: Reinstall CAT plugin, build Java client, and install the jlink runtime image into CLAUDE_PLUGIN_DATA
---

# Update Client

Build the Java client first, then reinstall the CAT plugin and install the updated jlink runtime. This ordering minimizes plugin downtime by doing the longest-running build step first.

**IMPORTANT:** All `claude` CLI commands below require unsetting `CLAUDECODE` to avoid the nested-session guard.

Before any `rm -rf`, check whether CAT is already installed. If it is, load `cat:safe-rm-agent` **before running any workflow commands that include `rm -rf`** and follow its checklist. If it is not, use a normal `rm -rf`.

Apply this only to the update workflow's `rm -rf` steps; do not load `cat:safe-rm-agent` for unrelated cleanup.

Quick preflight check:

```bash
if CLAUDECODE= claude plugin list | grep -q 'cat@cat'; then
  # CAT is installed -> load /cat:safe-rm-agent before continuing this workflow.
  echo "CAT installed: load /cat:safe-rm-agent, then continue."
else
  echo "CAT not installed: safe-rm skill not required for this workflow."
fi
```

## Worktree vs Main Workspace

If `CLAUDE_PROJECT_DIR` contains `/.cat/work/worktrees/`, you are running in a worktree. After the normal
install (which installs the marketplace/main-workspace version), the worktree's `plugin/` files are copied
over the install path so your in-progress changes take effect immediately.

## Steps

### 0. Capture startup plugin data path (if present) and prepare temp path for AOT

`CLAUDE_PLUGIN_DATA` may be unset at invocation and is not guaranteed to appear mid-session after reinstall.
Capture any startup value once, then use a temporary plugin-data directory for build-time AOT.

```bash
STARTUP_PLUGIN_DATA="${CLAUDE_PLUGIN_DATA:-}"
TEMP_PLUGIN_DATA="$(mktemp -d)"
export CLAUDE_PLUGIN_DATA="${TEMP_PLUGIN_DATA}"

cleanup_temp_plugin_data() {
  # Ensure shell is not inside the target directory before deleting it.
  if [[ "$(pwd)" == "${TEMP_PLUGIN_DATA}"* ]]; then
    cd /workspace
  fi
  find "${TEMP_PLUGIN_DATA}" -mindepth 1 -delete 2>/dev/null || true
  rmdir "${TEMP_PLUGIN_DATA}" 2>/dev/null || true
}

trap cleanup_temp_plugin_data EXIT
```

### 1. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify -Djlink.extra.args=--enable-assertions
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Install/Reinstall Plugin and Install jlink Runtime

Perform plugin reinstall, then resolve the real install data path and install the jlink runtime there in a **single
Bash call** to avoid hook timing issues.

```bash
# IMPORTANT: Do not use `set -u` here. Claude shell snapshots may reference unset shell variables
# (for example ZSH_VERSION), and nounset can terminate the script before install completes.
set -eo pipefail

# Install or reinstall plugin from the registered marketplace.
# If CAT is already installed, uninstall first. If not installed yet, skip uninstall.
if CLAUDECODE= claude plugin list | grep -q '^cat@cat\b'; then
  CLAUDECODE= claude plugin uninstall cat@cat || { echo "ERROR: Plugin uninstall failed"; exit 1; }
fi
CLAUDECODE= claude plugin install cat@cat || { echo "ERROR: Plugin install failed"; exit 1; }

# Determine install plugin data path.
# If CLAUDE_PLUGIN_DATA was unset at startup, use Claude's standard plugin data path.
if [[ -n "${STARTUP_PLUGIN_DATA:-}" ]]; then
  INSTALL_PLUGIN_DATA="${STARTUP_PLUGIN_DATA}"
  SHOULD_RESTART_CLAUDE=0
else
  INSTALL_PLUGIN_DATA="${HOME}/.claude/plugins/data"
  SHOULD_RESTART_CLAUDE=1
fi

# Always replace the runtime directory in install plugin data with the newly built jlink image.
rm -rf "${INSTALL_PLUGIN_DATA}/client"
cp -r /workspace/client/target/jlink "${INSTALL_PLUGIN_DATA}/client"

# Stamp the installed runtime with the plugin version.
echo "2.1" > "${INSTALL_PLUGIN_DATA}/client/VERSION"

# Use install path for the verify step.
export CLAUDE_PLUGIN_DATA="${INSTALL_PLUGIN_DATA}"

if [[ "${SHOULD_RESTART_CLAUDE}" -eq 1 ]]; then
  echo "Restart Claude Code to complete the plugin installation."
fi
```

If any command fails, stop and report the error.

### 2a. Install to Development Data Path

When running from source, hooks read runtime assets from `CLAUDE_PLUGIN_DATA`.
Install the runtime there so development sessions can start without needing a GitHub release.

```bash
# Replace the development runtime bundle in CLAUDE_PLUGIN_DATA.
rm -rf "${CLAUDE_PLUGIN_DATA}/client"
cp -r /workspace/client/target/jlink "${CLAUDE_PLUGIN_DATA}/client"
echo "2.1" > "${CLAUDE_PLUGIN_DATA}/client/VERSION"
echo "Installed runtime to development data path: ${CLAUDE_PLUGIN_DATA}/client/"
```

### 2b. Overlay Worktree Plugin Files (worktree only)

If running in a worktree, copy the worktree's `plugin/` files over the install path so in-progress changes
take effect. This overwrites files installed from the marketplace with the worktree version; new files are
added. Run only when `CLAUDE_PROJECT_DIR` contains `/.cat/work/worktrees/`:

```bash
cp -r "${CLAUDE_PROJECT_DIR}/plugin/." "${CLAUDE_PLUGIN_ROOT}/"
echo "Overlaid worktree plugin files from ${CLAUDE_PROJECT_DIR}/plugin/"
```

### 3. Verify

Confirm the jlink runtime works:

```bash
"${CLAUDE_PLUGIN_DATA}/client/bin/java" -version
```
