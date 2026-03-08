---
description: Reinstall CAT plugin, build Java client, and install the jlink runtime image into the plugin cache
disable-model-invocation: true
---

# Update Client

Reinstall the CAT plugin to pick up changes to hooks, skills, and agents, then build and install the jlink runtime.

**IMPORTANT:** All `claude` CLI commands below require unsetting `CLAUDECODE` to avoid the nested-session guard.

## Steps

### 1. Reinstall the Plugin

Uninstall the old plugin (if installed) and install the current version from the local workspace marketplace:

```bash
CLAUDECODE= claude plugin uninstall cat@cat 2>/dev/null; CLAUDECODE= claude plugin install cat@cat
```

The uninstall uses `;` instead of `&&` so that a missing plugin does not block the install.
If the install command fails, stop and report the error.

### 2. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 3. Install jlink Runtime Image to Plugin Cache

```bash
rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/client

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
