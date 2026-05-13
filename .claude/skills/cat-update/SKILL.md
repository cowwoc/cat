---
description: Rebuild and reinstall CAT for local Claude Code development from the current source checkout
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

# Update CAT

## Purpose

Rebuild CAT from the current source checkout and install the generated Claude Code runtime artifact into the local
Claude plugin cache and plugin data runtime directory.

## Procedure

Run the workflow from the repository root or a CAT issue worktree.

```bash
# IMPORTANT: Do not use `set -u` here. Claude shell snapshots may reference unset shell variables.
set -eo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/workspace}"
STARTUP_PLUGIN_DATA="${CLAUDE_PLUGIN_DATA:-}"
TEMP_PLUGIN_DATA="$(mktemp -d)"
export CLAUDE_PLUGIN_DATA="${TEMP_PLUGIN_DATA}"

cleanup_temp_plugin_data() {
  if [[ "$(pwd)" == "${TEMP_PLUGIN_DATA}"* ]]; then
    cd "${PROJECT_DIR}"
  fi
  rm -rf "${TEMP_PLUGIN_DATA}"
}

trap cleanup_temp_plugin_data EXIT

mvn -f "${PROJECT_DIR}/client/pom.xml" verify -Djlink.extra.args=--enable-assertions

RELEASE_ARTIFACT="${PROJECT_DIR}/client/distribution/target/runtime/claude"
test -f "${RELEASE_ARTIFACT}/client/VERSION"
test -f "${RELEASE_ARTIFACT}/.claude-plugin/plugin.json"

PLUGIN_VERSION="$(sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  "${RELEASE_ARTIFACT}/.claude-plugin/plugin.json" | head -1)"
if [[ -z "${PLUGIN_VERSION}" ]]; then
  echo "ERROR: Could not determine CAT plugin version." >&2
  exit 1
fi

INSTALL_PLUGIN_DATA="${STARTUP_PLUGIN_DATA:-${HOME}/.claude/plugins/data/cat-cat}"
CLAUDE_PLUGIN_CACHE_ROOT="${HOME}/.claude/plugins/cache/cat/cat"
CLAUDE_PLUGIN_CACHE="${CLAUDE_PLUGIN_CACHE_ROOT}/${PLUGIN_VERSION}"

mkdir -p "${CLAUDE_PLUGIN_CACHE_ROOT}" "${INSTALL_PLUGIN_DATA}"
chmod -R u+w "${CLAUDE_PLUGIN_CACHE_ROOT}" "${INSTALL_PLUGIN_DATA}/plugin" "${INSTALL_PLUGIN_DATA}/client" \
  2>/dev/null || true

rm -rf "${CLAUDE_PLUGIN_CACHE}" "${INSTALL_PLUGIN_DATA}/plugin" "${INSTALL_PLUGIN_DATA}/client"
cp -R "${RELEASE_ARTIFACT}" "${CLAUDE_PLUGIN_CACHE}"
cp -R "${RELEASE_ARTIFACT}" "${INSTALL_PLUGIN_DATA}/plugin"
cp -R "${RELEASE_ARTIFACT}/client" "${INSTALL_PLUGIN_DATA}/client"

export CLAUDE_PLUGIN_DATA="${INSTALL_PLUGIN_DATA}"

"${CLAUDE_PLUGIN_DATA}/client/bin/java" -version
test -x "${CLAUDE_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CLAUDE_PLUGIN_DATA}/client/VERSION"
test -f "${INSTALL_PLUGIN_DATA}/plugin/.claude-plugin/plugin.json"
test -f "${INSTALL_PLUGIN_DATA}/plugin/skills/add/SKILL.md"
test -f "${CLAUDE_PLUGIN_CACHE}/.claude-plugin/plugin.json"
test -f "${CLAUDE_PLUGIN_CACHE}/skills/add/SKILL.md"
```

After the command succeeds, tell the user to restart Claude Code to complete the installation.

## Verification

- The Maven build exits with code 0.
- `${HOME}/.claude/plugins/cache/cat/cat/{version}/skills/add/SKILL.md` exists.
- `${CLAUDE_PLUGIN_DATA}/plugin/.claude-plugin/plugin.json` exists.
- `${CLAUDE_PLUGIN_DATA}/client/bin/java -version` runs successfully.
- `${CLAUDE_PLUGIN_DATA}/client/bin/pre-bash` is executable.
