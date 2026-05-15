<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex CAT Install Prompt

You are installing CAT for Codex. CAT may not be installed yet, so do not rely on any `/cat:*` command.

This copy of the prompt installs CAT `v2.1`.

Install CAT directly from the Codex release artifact. This creates a local Codex marketplace from the release artifact,
adds it to Codex, and installs the bundled CAT client runtime.

Codex plugins currently install CAT skills, hooks, marketplace metadata, and client runtime files. Codex does not
currently support plugin-provided custom slash commands, and CAT does not install Codex command wrappers. For Codex,
invoke CAT with the corresponding dollar-prefixed skill mention, such as `$cat:init`, `$cat:status`, or `$cat:work`.

Run:

```bash
set -euo pipefail

REQUESTED_VERSION="v2.1"
case "${REQUESTED_VERSION}" in
  ""|"latest") RELEASE_TAG="latest" ;;
  v*) RELEASE_TAG="${REQUESTED_VERSION}" ;;
  *) RELEASE_TAG="v${REQUESTED_VERSION}" ;;
esac

CAT_RUNTIME="codex"
CODEX_HOME="${CODEX_HOME:-${HOME}/.codex}"
CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CODEX_HOME}/plugins/data/cat-cat}"
CAT_CONFIG_DIR="${CAT_CONFIG_DIR:-${CODEX_HOME}}"

if [[ "${RELEASE_TAG}" == "latest" ]]; then
  RELEASE_TAG="$(curl -fsSL https://api.github.com/repos/cowwoc/cat/releases/latest |
    sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
  if [[ -z "${RELEASE_TAG}" ]]; then
    echo "ERROR: Could not determine latest CAT release tag." >&2
    exit 1
  fi
fi

ASSET_NAME="cat-${CAT_RUNTIME}-${RELEASE_TAG}.tar.gz"
ASSET_URL="https://github.com/cowwoc/cat/releases/download/${RELEASE_TAG}/${ASSET_NAME}"

echo "CAT_RUNTIME=${CAT_RUNTIME}"
echo "RELEASE_TAG=${RELEASE_TAG}"
echo "ASSET_URL=${ASSET_URL}"
echo "CAT_PLUGIN_DATA=${CAT_PLUGIN_DATA}"
echo "CAT_CONFIG_DIR=${CAT_CONFIG_DIR}"

INSTALL_TMP="$(mktemp -d)"
trap 'rm -rf "${INSTALL_TMP}"' EXIT

curl -fsSL --max-time 300 -o "${INSTALL_TMP}/${ASSET_NAME}" "${ASSET_URL}"
curl -fsSL --max-time 60 -o "${INSTALL_TMP}/SHA256SUMS.txt" \
  "https://github.com/cowwoc/cat/releases/download/${RELEASE_TAG}/SHA256SUMS.txt" || true

if [[ -s "${INSTALL_TMP}/SHA256SUMS.txt" ]] &&
  grep -F "  ${ASSET_NAME}" "${INSTALL_TMP}/SHA256SUMS.txt" >/dev/null; then
  (cd "${INSTALL_TMP}" && grep -F "  ${ASSET_NAME}" SHA256SUMS.txt | sha256sum -c -)
fi

mkdir -p "${INSTALL_TMP}/artifact"
tar -xzf "${INSTALL_TMP}/${ASSET_NAME}" -C "${INSTALL_TMP}/artifact"
RELEASE_ARTIFACT="${INSTALL_TMP}/artifact"
if [[ ! -f "${RELEASE_ARTIFACT}/client/VERSION" ]]; then
  nested="$(find "${INSTALL_TMP}/artifact" -mindepth 1 -maxdepth 2 -type f -path '*/client/VERSION' -print -quit)"
  if [[ -n "${nested}" ]]; then
    RELEASE_ARTIFACT="$(dirname "$(dirname "${nested}")")"
  fi
fi
test -f "${RELEASE_ARTIFACT}/client/VERSION"
test -f "${RELEASE_ARTIFACT}/.codex-plugin/plugin.json"

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
CAT_PLUGIN_ROOT="${CAT_PLUGIN_ROOT:-${CODEX_PLUGIN_CACHE}}"
rm -rf "${CODEX_PLUGIN_CACHE_ROOT}"

try_codex_plugin_browser_install() {
  local marketplace_json escaped_marketplace_json
  marketplace_json="${LOCAL_MARKETPLACE_ROOT}/.agents/plugins/marketplace.json"
  escaped_marketplace_json="${marketplace_json//\\/\\\\}"
  escaped_marketplace_json="${escaped_marketplace_json//\"/\\\"}"
  printf '{"id":1,"method":"plugin/install","params":{"marketplacePath":"%s","pluginName":"cat","remoteMarketplaceName":null}}\n' \
    "${escaped_marketplace_json}" | codex app-server proxy >/dev/null 2>&1
}

if ! try_codex_plugin_browser_install || [[ ! -f "${CODEX_PLUGIN_CACHE}/skills/add/SKILL.md" ]]; then
  mkdir -p "${CODEX_PLUGIN_CACHE_ROOT}"
  cp -R "${RELEASE_ARTIFACT}" "${CODEX_PLUGIN_CACHE}"
fi

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
mkdir -p "${CAT_CONFIG_DIR}"

"${CAT_PLUGIN_ROOT}/client/bin/java" -version
test -x "${CAT_PLUGIN_ROOT}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_ROOT}/client/VERSION"
test -f "${CODEX_PLUGIN_CACHE}/.codex-plugin/plugin.json"
test -f "${CODEX_PLUGIN_CACHE}/skills/add/SKILL.md"
grep -F '[plugins."cat@cat"]' "${CODEX_CONFIG}" >/dev/null
awk '
  /^\[.*\]$/ { in_cat_plugin = ($0 == "[plugins.\"cat@cat\"]"); next }
  in_cat_plugin && /^[[:space:]]*enabled[[:space:]]*=[[:space:]]*true[[:space:]]*$/ { found_enabled = 1 }
  END { exit(found_enabled ? 0 : 1) }
' "${CODEX_CONFIG}"

echo "Restart Codex to complete the installation."
```

After the command succeeds, say only: `Restart Codex to complete the installation.` After restart, use `$cat:init` only
when the user wants to create a new CAT project or wrap an existing project.
