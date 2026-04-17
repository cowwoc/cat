---
description: Reinstall CAT plugin, build Java client, and install the jlink runtime image into the plugin cache
---

# Update Client

Build the Java client first, then reinstall the CAT plugin and install the updated jlink runtime. This ordering minimizes plugin downtime by doing the longest-running build step first.

**IMPORTANT:** All `claude` CLI commands below require unsetting `CLAUDECODE` to avoid the nested-session guard.

## Worktree vs Main Workspace

If `CLAUDE_PROJECT_DIR` contains `/.cat/work/worktrees/`, you are running in a worktree. After the normal
install (which installs the marketplace/main-workspace version), the worktree's `plugin/` files are copied
over the install path so your in-progress changes take effect immediately.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify -Djlink.extra.args=--enable-assertions
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Reinstall Plugin and Install jlink Runtime

First, ensure `CLAUDE_PLUGIN_ROOT` is set. If not set, derive it from plugin metadata files:

```bash
# Derive CLAUDE_PLUGIN_ROOT if not set
if [[ -z "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
  # Read publisher from marketplace.json (top-level "name" field)
  PUBLISHER=$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/.claude-plugin/marketplace.json | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  
  # Read plugin name and version from plugin.json
  PLUGIN_NAME=$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/plugin/.claude-plugin/plugin.json | sed 's/.*"\([^"]*\)"$/\1/')
  PLUGIN_VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/plugin/.claude-plugin/plugin.json | sed 's/.*"\([^"]*\)"$/\1/')
  
  # Construct plugin install path: ${CLAUDE_CONFIG_DIR}/plugins/cache/<publisher>/<name>/<version>
  export CLAUDE_PLUGIN_ROOT="${CLAUDE_CONFIG_DIR}/plugins/cache/${PUBLISHER}/${PLUGIN_NAME}/${PLUGIN_VERSION}"
  
  echo "Derived CLAUDE_PLUGIN_ROOT=${CLAUDE_PLUGIN_ROOT}"
fi
```

If any read fails, stop and report the error.

Then perform the plugin reinstall, jlink installation, and VERSION write in a **single Bash call** to avoid hook
errors. Splitting these into separate calls risks hooks firing while the client directory is absent or incomplete.

```bash
# Reinstall the plugin from the registered marketplace.
# Uninstall uses ; instead of && so a missing plugin does not block the install.
CLAUDECODE= claude plugin uninstall cat@cat 2>/dev/null ; true
CLAUDECODE= claude plugin install cat@cat || { echo "ERROR: Plugin install failed"; exit 1; }

# Replace the client directory with the newly built jlink image.
rm -rf "${CLAUDE_PLUGIN_ROOT}/client"
cp -r /workspace/client/target/jlink "${CLAUDE_PLUGIN_ROOT}/client"

# Stamp the installed runtime with the plugin version.
echo "2.1" > "${CLAUDE_PLUGIN_ROOT}/client/VERSION"
```

If any command fails, stop and report the error.

### 2a. Overlay Worktree Plugin Files (worktree only)

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
"${CLAUDE_PLUGIN_ROOT}/client/bin/java" -version
```
