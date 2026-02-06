# CAT JDK Infrastructure

This directory contains scripts for managing the custom JDK runtime used by CAT's Java-based hooks.

## Overview

CAT uses a minimal JDK 25 runtime created with `jlink` to provide fast startup times for Java hooks. The custom runtime
includes only the modules needed for JSON processing (Jackson 3) and basic I/O operations.

**Benefits:**
- ~30-40MB vs ~300MB for full JDK
- Faster startup (fewer modules to initialize)
- Self-contained (no external Java dependency required)

## Files

| File | Purpose |
|------|---------|
| `jlink-config.sh` | Build script for creating the custom runtime |
| `session_start.sh` | SessionStart hook to bootstrap the JDK |
| `java_runner.sh` | Intermediary script for invoking Java hooks |

## Building the Runtime

### Prerequisites

- JDK 25 installed (for running jlink)
- curl (for downloading Jackson jars)
- ~100MB disk space

### Build Command

```bash
# From plugin root
./hooks/jdk/jlink-config.sh build --output-dir runtime/

# Or with explicit JDK
JAVA_HOME=/path/to/jdk-25 ./hooks/jdk/jlink-config.sh build
```

### Configuration Info

```bash
./hooks/jdk/jlink-config.sh info
```

## Runtime Location

The custom runtime is installed to:
```
${CLAUDE_PLUGIN_ROOT}/runtime/cat-jdk-25/
```

Structure:
```
cat-jdk-25/
├── bin/
│   └── java          # The Java binary
├── conf/             # JVM configuration
├── lib/              # JVM libraries
│   └── jackson/      # Jackson 3 jars (optional)
└── release           # Version info
```

## Session Bootstrap

The `session_start.sh` hook runs at each Claude Code session start:

1. Checks if custom runtime exists at expected path
2. If missing, attempts to download pre-built bundle
3. Falls back to building locally if JDK 25 is available
4. Exports `CAT_JAVA_HOME` for `java_runner.sh`

If all methods fail, a warning is logged but the session continues (Python hooks remain available).

## Running Java Hooks

Java hooks are invoked through `java_runner.sh`:

```bash
# Direct invocation
echo '{"tool":"Bash","input":"..."}' | ./java_runner.sh BashPreToolHandler

# With environment
CAT_JAVA_HOME=/path/to/runtime ./java_runner.sh ValidationHandler
```

### Handler Classes

| Class | Purpose |
|-------|---------|
| `BashPreToolHandler` | Pre-tool validation for Bash commands |
| `BashPostToolHandler` | Post-tool analysis of Bash results |
| `SkillHandler` | Skill preprocessing and validation |
| `ValidationHandler` | Commit type and code validation |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CAT_JAVA_HOME` | (auto) | Path to CAT's custom JDK runtime |
| `CAT_JAVA_TIMEOUT` | 30 | Timeout for Java hook execution (seconds) |
| `CAT_JAVA_XMS` | 16m | Initial heap size |
| `CAT_JAVA_XMX` | 64m | Maximum heap size |

## Troubleshooting

### "Java not found"

1. Ensure JDK 25 is installed: `java -version`
2. Set JAVA_HOME: `export JAVA_HOME=/path/to/jdk-25`
3. Or build the custom runtime: `./jlink-config.sh build`

### "Hook classpath not found"

The Java hooks JAR is not built yet. This is expected during initial setup. Java hook implementations will be added in
subsequent tasks.

### Build fails with "module not found"

Ensure you're using JDK 25 (not just JRE). jlink requires the full JDK.

## Jackson 3 Integration

The runtime includes Jackson 3.x modules for JSON processing:

- `tools.jackson.core` - Core streaming API
- `tools.jackson.databind` - Object binding
- `tools.jackson.annotation` - Annotations

Jackson 3 uses native JPMS modules (not Moditect), providing better compatibility with jlink.

## Platform Support

Pre-built bundles are available for:

| Platform | Architecture |
|----------|-------------|
| Linux | x64, aarch64 |
| macOS | x64, aarch64 |
| Windows | x64 |

For other platforms, build locally with a JDK 25 installation.
