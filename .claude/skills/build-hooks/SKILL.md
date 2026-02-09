---
description: Build Java hooks and install the JAR into the plugin cache
disable-model-invocation: true
---

# Build Hooks

Build the Java hooks JAR and copy it into the plugin cache for use by hook handlers.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/hooks/pom.xml verify
```

If the build fails, stop and report the error.

### 2. Install JAR to Plugin Cache

```bash
cp /workspace/hooks/target/cat-hooks.jar \
   /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/hooks.jar
```

### 3. Verify

Confirm the installed JAR exists and report its size:

```bash
ls -la /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/hooks.jar
```
