---
description: Reinstall CAT plugin, build Java client, and install the jlink runtime image into the plugin cache
disable-model-invocation: true
---

# Update Client

Build the Java client first, then reinstall the CAT plugin and install the updated jlink runtime. This ordering minimizes plugin downtime by doing the longest-running build step first.

**IMPORTANT:** All `claude` CLI commands below require unsetting `CLAUDECODE` to avoid the nested-session guard.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Check Hook Status and Reinstall the Plugin

Check whether the plugin was previously installed by testing if the `pre-bash` binary exists.
**This check must happen before reinstalling** — the reinstall wipes the `client/` directory, making the
binary unavailable afterward.

```bash
test -f /home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/pre-bash && echo "HOOKS_ACTIVE" || echo "NO_HOOKS"
```

Store the result as HOOK_STATUS for use in Step 3.

Then perform the symlink removal, plugin reinstall, and symlink recreation in a **single Bash call** to
avoid hook errors. The symlink must exist when `pre-bash` fires (before the call) and when `post-bash`
fires (after the call). Splitting these into separate calls leaves the symlink absent between calls,
causing hook binary resolution failures.

```bash
# Remove the client symlink before reinstall to prevent claude plugin install from copying it
# into the new cache (which would create a self-referential symlink).
[[ -L "${CLAUDE_PLUGIN_ROOT}/client" ]] && unlink "${CLAUDE_PLUGIN_ROOT}/client"

# Reinstall the plugin from the local workspace marketplace.
# Uninstall uses ; instead of && so a missing plugin does not block the install.
CLAUDECODE= claude plugin uninstall cat@cat 2>/dev/null
CLAUDECODE= claude plugin install cat@cat || { echo "ERROR: Plugin install failed"; exit 1; }

# Recreate the client symlink immediately so hook binaries resolve for all subsequent Bash calls.
# session-start.sh would recreate it at next session start, but we need it now.
INSTALL_PATH=$(grep -o '"installPath"[[:space:]]*:[[:space:]]*"[^"]*"' \
  "${HOME}/.config/claude/plugins/installed_plugins.json" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
if [[ -z "$INSTALL_PATH" ]]; then
  echo "ERROR: Could not determine installPath from installed_plugins.json" >&2
  exit 1
fi
ln -sfn "${INSTALL_PATH}/client" "${CLAUDE_PLUGIN_ROOT}/client"
```

If any command fails, stop and report the error.

### 3. Install jlink Runtime Image to Plugin Cache

Remove the old client directory using the HOOK_STATUS captured in Step 2. The symlink was recreated at
the end of Step 2, so `cat:safe-rm-agent` is usable again:

- If HOOK_STATUS was `HOOKS_ACTIVE`: use the `cat:safe-rm-agent` skill to remove the directory:

  ```
  /cat:safe-rm-agent /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
  ```

- If HOOK_STATUS was `NO_HOOKS`: use `rm -rf` directly:

  ```bash
  rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
  ```

Then copy the new jlink image:

```bash
cp -r /workspace/client/target/jlink \
      /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
```

### 4. Write VERSION file

Stamp the installed runtime with the plugin version so `session-start.sh` knows it's up to date:

```bash
echo "2.1" > /home/node/.config/claude/plugins/cache/cat/cat/2.1/client/VERSION
```

### 5. Verify

Confirm the jlink runtime works:

```bash
/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/java -version
```
