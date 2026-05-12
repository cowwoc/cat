---
name: cat-install
description: Build CAT's flattened Claude plugin artifact and reinstall/update the active local Claude plugin from it. Use after changing Java client code, launcher generation, or plugin source files that should be reflected in an installed local CAT plugin.
model: sonnet
effort: medium
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

# Install CAT

Use this skill to update a local Claude-installed CAT plugin from a source checkout or issue worktree.

The workflow builds the flattened Claude release artifact, overlays the installed plugin root from that artifact, and
installs the bundled jlink runtime from the same flattened artifact into the active Claude plugin data directory.

## Required Inputs

Infer these paths when possible, otherwise ask for the missing value:

- `SOURCE_ROOT`: the CAT source checkout containing `client/plugin/` and `client/cli/`. Default to the current git
  worktree root.
- `CAT_PLUGIN_ROOT`: the installed CAT plugin root. Default to `${CLAUDE_PLUGIN_ROOT}`.
- `CAT_PLUGIN_DATA`: the installed CAT plugin data directory. Default to `${CLAUDE_PLUGIN_DATA}`.

Stop if `CAT_PLUGIN_ROOT` or `CAT_PLUGIN_DATA` cannot be resolved. They identify the active Claude installation that
will be updated.

## Steps

### 1. Resolve Paths

Run from the CAT source checkout or issue worktree:

```bash
SOURCE_ROOT="$(git rev-parse --show-toplevel)"
CAT_PLUGIN_ROOT="${CAT_PLUGIN_ROOT:-${CLAUDE_PLUGIN_ROOT:-}}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CLAUDE_PLUGIN_DATA:-}}"

: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT or CLAUDE_PLUGIN_ROOT is required}"
: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA or CLAUDE_PLUGIN_DATA is required}"

echo "SOURCE_ROOT=${SOURCE_ROOT}"
echo "CAT_PLUGIN_ROOT=${CAT_PLUGIN_ROOT}"
echo "CAT_PLUGIN_DATA=${CAT_PLUGIN_DATA}"
```

Stop if `SOURCE_ROOT/client/pom.xml` or `SOURCE_ROOT/client/plugin/.claude-plugin/plugin.json` does not exist.

### 2. Build the Flattened Artifact

Build the client runtime and flattened runtime-specific plugin artifacts from the source checkout:

```bash
PATH="${JAVA_HOME:+${JAVA_HOME}/bin:}${PATH}" \
  "${SOURCE_ROOT}/client/mvnw" -f "${SOURCE_ROOT}/client/pom.xml" verify -e -Djlink.extra.args=--enable-assertions
```

If `jlink` is not found, rerun with `PATH="$JAVA_HOME/bin:$PATH"` after confirming `JAVA_HOME` points at a full JDK,
not a JRE.

### 3. Reinstall Plugin Files

Install from the flattened Claude artifact, not from raw source directories:

```bash
FLATTENED_PLUGIN="${SOURCE_ROOT}/client/distribution/target/runtime/claude"
resolved_flattened="$(cd "${FLATTENED_PLUGIN}" && pwd -P)"
resolved_plugin_root="$(cd "${CAT_PLUGIN_ROOT}" && pwd -P)"

if [[ "${resolved_plugin_root}" != "${resolved_flattened}" ]]; then
  chmod -R u+w "${CAT_PLUGIN_ROOT}" 2>/dev/null || true
  find "${CAT_PLUGIN_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -R "${FLATTENED_PLUGIN}/." "${CAT_PLUGIN_ROOT}/"
  echo "Reinstalled CAT Claude plugin files at ${CAT_PLUGIN_ROOT}"
else
  echo "Plugin root already points at the flattened local artifact; reinstall skipped."
fi
```

### 4. Install Runtime Data

Claude hook registrations execute launchers from `${CLAUDE_PLUGIN_DATA}/client/bin`, so copy the bundled jlink image
from the flattened artifact into plugin data:

```bash
mkdir -p "${CAT_PLUGIN_DATA}"
chmod -R u+w "${CAT_PLUGIN_DATA}/client" 2>/dev/null || true
rm -rf "${CAT_PLUGIN_DATA}/client"
cp -R "${FLATTENED_PLUGIN}/client" "${CAT_PLUGIN_DATA}/client"
```

### 5. Verify

```bash
"${CAT_PLUGIN_DATA}/client/bin/java" -version
test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_DATA}/client/VERSION"
```

Restart Claude Code after updating plugin sources or runtime binaries so the session reloads skill metadata, hooks, and
the runtime cache.
