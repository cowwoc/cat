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
FLATTENED_PLUGIN="${INSTALL_TMP}/artifact"
if [[ ! -f "${FLATTENED_PLUGIN}/client/VERSION" ]]; then
  nested="$(find "${INSTALL_TMP}/artifact" -mindepth 1 -maxdepth 2 -type f -path '*/client/VERSION' -print -quit)"
  if [[ -n "${nested}" ]]; then
    FLATTENED_PLUGIN="$(dirname "$(dirname "${nested}")")"
  fi
fi
test -f "${FLATTENED_PLUGIN}/client/VERSION"

LOCAL_MARKETPLACE_ROOT="${LOCAL_MARKETPLACE_ROOT:-${CODEX_HOME}/plugins/cat-marketplace}"
rm -rf "${LOCAL_MARKETPLACE_ROOT}"
mkdir -p "${LOCAL_MARKETPLACE_ROOT}/plugins/cat"
cp -R "${FLATTENED_PLUGIN}/." "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/"
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
cp -R "${FLATTENED_PLUGIN}/client" "${CAT_PLUGIN_DATA}/client"

"${CAT_PLUGIN_DATA}/client/bin/java" -version
test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_DATA}/client/VERSION"
grep -F '[plugins."cat@cat"]' "${CODEX_CONFIG}" >/dev/null
awk '
  /^\[.*\]$/ { in_cat_plugin = ($0 == "[plugins.\"cat@cat\"]"); next }
  in_cat_plugin && /^[[:space:]]*enabled[[:space:]]*=[[:space:]]*true[[:space:]]*$/ { found_enabled = 1 }
  END { exit(found_enabled ? 0 : 1) }
' "${CODEX_CONFIG}"

echo "Restart Codex to complete the installation."
```

After the command succeeds, say only: `Restart Codex to complete the installation.` Run `/cat:init` only when the
user wants to create a new CAT project or wrap an existing project.
