---
description: Build Java client and install the jlink runtime image into the plugin cache
disable-model-invocation: true
---

# Build Client

Build the self-contained jlink runtime image (includes client JAR, all dependencies, and JDK modules) and install it
into the plugin cache.

## Steps

### 1. Build with Maven

```bash
mvn -f /workspace/client/pom.xml verify
```

This builds the client JAR, patches automatic modules, creates the jlink image with launchers, and generates the AOT cache.
If the build fails, stop and report the error.

### 2. Install jlink Runtime Image to Plugin Cache

```bash
rm -rf /home/node/.config/claude/plugins/cache/cat/cat/2.1/client

cp -r /workspace/client/target/jlink \
      /home/node/.config/claude/plugins/cache/cat/cat/2.1/client
```

### 3. Write VERSION file

Stamp the installed runtime with the plugin version so `session-start.sh` knows it's up to date:

```bash
echo "2.1" > /home/node/.config/claude/plugins/cache/cat/cat/2.1/client/VERSION
```

### 4. Verify

Confirm the jlink runtime works:

```bash
/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/java -version
```
