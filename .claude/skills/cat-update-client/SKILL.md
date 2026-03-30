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

Then uninstall the old plugin (if installed) and install the current version from the local workspace marketplace:

```bash
CLAUDECODE= claude plugin uninstall cat@cat 2>/dev/null; CLAUDECODE= claude plugin install cat@cat
```

The uninstall uses `;` instead of `&&` so that a missing plugin does not block the install.
If the install command fails, stop and report the error.

### 3. Install jlink Runtime Image to Plugin Cache

Remove the old client directory using the HOOK_STATUS captured in Step 2:

- If HOOK_STATUS was `HOOKS_ACTIVE`: the `BlockUnsafeRemoval` hook was active before reinstall — use the
  `cat:safe-rm-agent` skill to remove the directory:

  ```
  /cat:safe-rm-agent /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
  ```

- If HOOK_STATUS was `NO_HOOKS`: the plugin was not previously installed and no hooks were active — use `rm -rf`
  directly:

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
