---
description: Build Java engine and install the jlink runtime image into the plugin cache
disable-model-invocation: true
---

# Build Engine

Build the self-contained jlink runtime image (includes engine JAR, all dependencies, and JDK modules) and install it
into the plugin cache.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/engine/pom.xml verify
```

This builds the engine JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Install jlink Runtime Image to Plugin Cache

```bash
rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/bin \
       /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/lib \
       /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/conf \
       /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/legal \
       /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/release

cp -r /workspace/engine/target/jlink/* \
      /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/
```

### 3. Verify

Confirm the jlink runtime works:

```bash
/home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/bin/java -version
```
