---
name: cat-update
description: >-
  Rebuild and reinstall CAT for local Codex development from the current source checkout. Use when the user asks to
  update local CAT, rebuild the CAT plugin, refresh the Codex plugin cache, or install current source changes into the
  local Codex marketplace.
---

# Update CAT

## Purpose

Rebuild CAT from the current source checkout and install the generated Codex runtime artifact into the local Codex
marketplace, plugin cache, and plugin data runtime directory.

## Procedure

Run the workflow from the repository root.

```bash
set -euo pipefail

PROJECT_DIR="/workspace"
CODEX_HOME="${CODEX_HOME:-${HOME}/.codex}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CODEX_HOME}/plugins/data/cat-cat}"

# Resolve CAT workPath from config (default: ${CAT_PROJECT_DIR}/.cat/work),
# then use it to discover session lock files.
CONFIG_PATH="${PROJECT_DIR}/.cat/config.json"
WORK_PATH_TEMPLATE='${CAT_PROJECT_DIR}/.cat/work'
if [[ -f "${CONFIG_PATH}" ]]; then
  CONFIG_WORK_PATH="$(sed -n 's/.*"workPath"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${CONFIG_PATH}" | head -1)"
  if [[ -n "${CONFIG_WORK_PATH}" ]]; then
    WORK_PATH_TEMPLATE="${CONFIG_WORK_PATH}"
  fi
fi
WORK_PATH="${WORK_PATH_TEMPLATE//'${CAT_PROJECT_DIR}'/${PROJECT_DIR}}"
WORK_PATH="${WORK_PATH//'${CLAUDE_PROJECT_DIR}'/${PROJECT_DIR}}"
LOCKS_DIR="${WORK_PATH}/locks"

# If this session currently holds a CAT issue lock, build/install from that
# worktree instead of the main workspace.
SESSION_IDS=(
  "${CAT_SESSION_ID:-}"
  "${CODEX_THREAD_ID:-}"
  "${CODEX_SESSION_ID:-}"
  "${CLAUDE_SESSION_ID:-}"
  "${SESSION_ID:-}"
)
if [[ -d "${LOCKS_DIR}" ]]; then
  for SID in "${SESSION_IDS[@]}"; do
    [[ -z "${SID}" ]] && continue
    while IFS= read -r -d '' LOCK_FILE; do
      LOCK_JSON="$(tr -d '\n' < "${LOCK_FILE}")"
      if [[ "${LOCK_JSON}" =~ \"session_id\"[[:space:]]*:[[:space:]]*\"${SID}\" ]]; then
        LOCKED_WORKTREE="$(echo "${LOCK_JSON}" | sed -n 's/.*"worktrees"[[:space:]]*:[[:space:]]*{[[:space:]]*"\([^"]*\)".*/\1/p')"
        if [[ -n "${LOCKED_WORKTREE}" && -f "${LOCKED_WORKTREE}/client/pom.xml" ]]; then
          PROJECT_DIR="${LOCKED_WORKTREE}"
          break 2
        fi
      fi
    done < <(find "${LOCKS_DIR}" -maxdepth 1 -type f -name '*.lock' -print0 2>/dev/null)
  done
fi
echo "Using PROJECT_DIR=${PROJECT_DIR}"

mvn -f "${PROJECT_DIR}/client/pom.xml" verify -Djlink.extra.args=--enable-assertions

BATS_BIN="${PROJECT_DIR}/client/plugin/node_modules/.bin/bats"
if [[ ! -x "${BATS_BIN}" ]]; then
  npm ci --prefix "${PROJECT_DIR}/client/plugin"
fi
BATS_PROJECT="$(mktemp -d)"
trap 'rm -rf "${BATS_PROJECT}"' EXIT
cp -a "${PROJECT_DIR}/." "${BATS_PROJECT}/"
rm -rf "${BATS_PROJECT}/.git" "${BATS_PROJECT}"/client/*/target
npm run --prefix "${BATS_PROJECT}/client/plugin" test

RELEASE_ARTIFACT="${PROJECT_DIR}/client/distribution/target/engine/codex"
test -f "${RELEASE_ARTIFACT}/client/VERSION"
test -f "${RELEASE_ARTIFACT}/.codex-plugin/plugin.json"
test -d "${RELEASE_ARTIFACT}/agents"

PLUGIN_VERSION="$(sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  "${RELEASE_ARTIFACT}/.codex-plugin/plugin.json" | head -1)"
if [[ -z "${PLUGIN_VERSION}" ]]; then
  echo "ERROR: Could not determine CAT plugin version." >&2
  exit 1
fi

LOCAL_MARKETPLACE_ROOT="${LOCAL_MARKETPLACE_ROOT:-${CODEX_HOME}/plugins/cat-marketplace}"
rm -rf "${LOCAL_MARKETPLACE_ROOT}"
mkdir -p "${LOCAL_MARKETPLACE_ROOT}/plugins/cat"
cp -R "${RELEASE_ARTIFACT}/." "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/"
mkdir -p "${LOCAL_MARKETPLACE_ROOT}/.agents/plugins"
cat > "${LOCAL_MARKETPLACE_ROOT}/.agents/plugins/marketplace.json" <<'JSON'
{
  "name": "cat",
  "interface": {
    "displayName": "CAT"
  },
  "plugins": [
    {
      "name": "cat",
      "source": {
        "source": "local",
        "path": "./plugins/cat"
      },
      "policy": {
        "installation": "INSTALLED_BY_DEFAULT",
        "authentication": "ON_INSTALL"
      },
      "category": "Productivity"
    }
  ]
}
JSON

codex plugin marketplace remove cat 2>/dev/null || true
codex plugin marketplace add "${LOCAL_MARKETPLACE_ROOT}"

CODEX_PLUGIN_CACHE_ROOT="${CODEX_HOME}/plugins/cache/cat/cat"
CODEX_PLUGIN_CACHE="${CODEX_PLUGIN_CACHE_ROOT}/${PLUGIN_VERSION}"
rm -rf "${CODEX_PLUGIN_CACHE_ROOT}"

try_codex_plugin_browser_install() {
  local marketplace_json escaped_marketplace_json
  marketplace_json="${LOCAL_MARKETPLACE_ROOT}/.agents/plugins/marketplace.json"
  escaped_marketplace_json="${marketplace_json//\\/\\\\}"
  escaped_marketplace_json="${escaped_marketplace_json//\"/\\\"}"
  printf '%s\n' \
    '{"id":1,"method":"plugin/install","params":{"marketplacePath":"'"${escaped_marketplace_json}"'",'\
'"pluginName":"cat","remoteMarketplaceName":null}}' | codex app-server proxy >/dev/null 2>&1
}

if ! try_codex_plugin_browser_install || [[ ! -f "${CODEX_PLUGIN_CACHE}/skills/add/SKILL.md" ]]; then
  mkdir -p "${CODEX_PLUGIN_CACHE_ROOT}"
  cp -R "${RELEASE_ARTIFACT}" "${CODEX_PLUGIN_CACHE}"
fi

PROJECT_CODEX_AGENTS_DIR="${PROJECT_DIR}/.codex/agents"
mkdir -p "${PROJECT_CODEX_AGENTS_DIR}"
find "${PROJECT_CODEX_AGENTS_DIR}" -maxdepth 1 -type f -name 'cat-*.toml' -delete
while IFS= read -r -d '' AGENT_FILE; do
  cp "${AGENT_FILE}" "${PROJECT_CODEX_AGENTS_DIR}/cat-$(basename "${AGENT_FILE}")"
done < <(find "${RELEASE_ARTIFACT}/agents" -maxdepth 1 -type f -name '*.toml' -print0)

CODEX_CONFIG="${CODEX_CONFIG:-${CODEX_HOME}/config.toml}"
mkdir -p "$(dirname "${CODEX_CONFIG}")"
touch "${CODEX_CONFIG}"
CONFIG_TMP="$(mktemp)"
awk '
  /^\[.*\]$/ {
    if (in_cat_plugin && ! wrote_enabled)
      print "enabled = true"
    in_cat_plugin = ($0 == "[plugins.\"cat@cat\"]")
    wrote_enabled = 0
    found_cat_plugin = found_cat_plugin || in_cat_plugin
    print
    next
  }
  in_cat_plugin && /^[[:space:]]*enabled[[:space:]]*=/ {
    print "enabled = true"
    wrote_enabled = 1
    next
  }
  { print }
  END {
    if (in_cat_plugin && ! wrote_enabled)
      print "enabled = true"
    if (! found_cat_plugin) {
      print ""
      print "[plugins.\"cat@cat\"]"
      print "enabled = true"
    }
  }
' "${CODEX_CONFIG}" > "${CONFIG_TMP}"
mv "${CONFIG_TMP}" "${CODEX_CONFIG}"

mkdir -p "${CAT_PLUGIN_DATA}"
chmod -R u+w "${CAT_PLUGIN_DATA}/client" 2>/dev/null || true
rm -rf "${CAT_PLUGIN_DATA}/client"
cp -R "${RELEASE_ARTIFACT}/client" "${CAT_PLUGIN_DATA}/client"

"${CAT_PLUGIN_DATA}/client/bin/java" -version
test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_DATA}/client/VERSION"
test -f "${CODEX_PLUGIN_CACHE}/.codex-plugin/plugin.json"
test -f "${CODEX_PLUGIN_CACHE}/skills/add/SKILL.md"
test -f "${PROJECT_CODEX_AGENTS_DIR}/cat-stakeholder-architecture.toml"
grep -F 'name = "cat-stakeholder-architecture"' \
  "${PROJECT_CODEX_AGENTS_DIR}/cat-stakeholder-architecture.toml" >/dev/null
grep -F '[plugins."cat@cat"]' "${CODEX_CONFIG}" >/dev/null
awk '
  /^\[.*\]$/ { in_cat_plugin = ($0 == "[plugins.\"cat@cat\"]"); next }
  in_cat_plugin && /^[[:space:]]*enabled[[:space:]]*=[[:space:]]*true[[:space:]]*$/ { found_enabled = 1 }
  END { exit(found_enabled ? 0 : 1) }
' "${CODEX_CONFIG}"
```

After the command succeeds, tell the user to restart Codex to complete the installation.

## Verification

- The Maven build exits with code 0.
- The Bats test suite exits with code 0.
- `${CODEX_HOME}/plugins/cat-marketplace/plugins/cat/.codex-plugin/plugin.json` exists.
- `${CODEX_HOME}/plugins/cache/cat/cat/{version}/skills/add/SKILL.md` exists.
- `${CAT_PLUGIN_DATA}/client/bin/java -version` runs successfully.
- `${PROJECT_DIR}/.codex/agents/cat-stakeholder-architecture.toml` exists and declares `cat-stakeholder-architecture`.
- `${CODEX_HOME}/config.toml` enables `[plugins."cat@cat"]`.
