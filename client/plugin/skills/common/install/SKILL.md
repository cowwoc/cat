---
name: install
description: Install or update CAT from the GitHub Release artifact for the active runtime.
model: gpt-5.4-mini
effort: medium
argument-hint: "[latest|version]"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Install CAT

Install or update CAT by downloading the active runtime's release artifact from `cowwoc/cat` GitHub Releases,
unpacking it, and replacing the installed plugin files plus bundled client runtime.

This skill is shared by Claude Code and Codex. Claude Code must not install or expose `cat:uninstall`; Codex keeps
`cat:uninstall` because Codex project-scoped agent copies need explicit cleanup before the built-in uninstaller runs.

## Inputs

Use the optional skill argument as the requested version:

- Empty or `latest`: install the latest release.
- Bare version such as `2.1.0`: normalize to `v2.1.0`.
- Tag such as `v2.1.0`: use as-is.

The expected release assets are:

- `cat-claude-<release-tag>.tar.gz`
- `cat-codex-<release-tag>.tar.gz`
- `SHA256SUMS.txt` when available.

## Steps

### 1. Resolve Runtime and Paths

Run:

```bash
REQUESTED_VERSION="${ARGUMENTS:-latest}"
case "${REQUESTED_VERSION}" in
  ""|"latest") RELEASE_TAG="latest" ;;
  v*) RELEASE_TAG="${REQUESTED_VERSION}" ;;
  *) RELEASE_TAG="v${REQUESTED_VERSION}" ;;
esac

if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
  CAT_RUNTIME="claude"
  CAT_PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT}"
  CAT_PLUGIN_DATA="${CLAUDE_PLUGIN_DATA:-${HOME}/.claude/plugins/data/cat}"
elif [[ -n "${CAT_PLUGIN_ROOT:-}" || -n "${CODEX_HOME:-}" || -d "${HOME}/.codex" ]]; then
  CAT_RUNTIME="codex"
  CODEX_HOME="${CODEX_HOME:-${HOME}/.codex}"
  CAT_PLUGIN_DATA="${CAT_PLUGIN_DATA:-${CODEX_HOME}/plugins/data/local-cat}"
else
  echo "ERROR: Cannot determine runtime. CLAUDE_PLUGIN_ROOT or CODEX_HOME/CAT_PLUGIN_ROOT must be set." >&2
  exit 1
fi

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
```

For Codex, if `CAT_PLUGIN_ROOT` is not set, resolve it from the generated local marketplace when possible:

```bash
if [[ "${CAT_RUNTIME}" == "codex" && -z "${CAT_PLUGIN_ROOT:-}" ]]; then
  marketplace="${HOME}/.agents/plugins/marketplace.json"
  if [[ -f "${marketplace}" ]]; then
    CAT_PLUGIN_ROOT="$(awk '
      /"name"[[:space:]]*:[[:space:]]*"cat"/ { in_cat = 1 }
      in_cat && /"source"[[:space:]]*:[[:space:]]*"local"/ { in_local = 1 }
      in_cat && in_local && /"path"[[:space:]]*:/ {
        line = $0
        sub(/^.*"path"[[:space:]]*:[[:space:]]*"/, "", line)
        sub(/".*$/, "", line)
        print line
        exit
      }
    ' "${marketplace}")"
    if [[ "${CAT_PLUGIN_ROOT}" == "~/"* ]]; then
      CAT_PLUGIN_ROOT="${HOME}/${CAT_PLUGIN_ROOT#~/}"
    fi
    if [[ -n "${CAT_PLUGIN_ROOT}" ]]; then
      CAT_PLUGIN_ROOT="$(cd "${CAT_PLUGIN_ROOT}" && pwd -P)"
    fi
  fi
fi
```

### 2. Download and Verify the Artifact

Run:

```bash
INSTALL_TMP="$(mktemp -d)"
trap 'rm -rf "${INSTALL_TMP}"' EXIT

curl -fsSL --max-time 300 -o "${INSTALL_TMP}/${ASSET_NAME}" "${ASSET_URL}"
curl -fsSL --max-time 60 -o "${INSTALL_TMP}/SHA256SUMS.txt" \
  "https://github.com/cowwoc/cat/releases/download/${RELEASE_TAG}/SHA256SUMS.txt" || true

if [[ -s "${INSTALL_TMP}/SHA256SUMS.txt" ]] && grep -F "  ${ASSET_NAME}" "${INSTALL_TMP}/SHA256SUMS.txt" >/dev/null; then
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
```

### 3. Install Plugin Files

For Claude Code, overlay the installed plugin cache root:

```bash
if [[ "${CAT_RUNTIME}" == "claude" ]]; then
  if [[ -z "${CAT_PLUGIN_ROOT:-}" ]]; then
    echo "ERROR: CLAUDE_PLUGIN_ROOT is required for Claude Code installation." >&2
    exit 1
  fi
  chmod -R u+w "${CAT_PLUGIN_ROOT}" 2>/dev/null || true
  find "${CAT_PLUGIN_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -R "${FLATTENED_PLUGIN}/." "${CAT_PLUGIN_ROOT}/"
fi
```

For Codex, install through a generated local marketplace and overlay the active plugin root when it is separate:

```bash
if [[ "${CAT_RUNTIME}" == "codex" ]]; then
  LOCAL_MARKETPLACE_ROOT="${LOCAL_MARKETPLACE_ROOT:-${CODEX_HOME}/plugins/cat-local-marketplace}"
  rm -rf "${LOCAL_MARKETPLACE_ROOT}"
  mkdir -p "${LOCAL_MARKETPLACE_ROOT}/plugins/cat"
  cp -R "${FLATTENED_PLUGIN}/." "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/"
  mkdir -p "${LOCAL_MARKETPLACE_ROOT}/.agents/plugins"
  cat > "${LOCAL_MARKETPLACE_ROOT}/.agents/plugins/marketplace.json" <<'JSON'
{
  "name": "cat-local",
  "interface": {
    "displayName": "CAT Local"
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

  codex plugin marketplace remove cat-local 2>/dev/null || true
  codex plugin marketplace add "${LOCAL_MARKETPLACE_ROOT}"
  CAT_PLUGIN_ROOT="${CAT_PLUGIN_ROOT:-${LOCAL_MARKETPLACE_ROOT}/plugins/cat}"

  resolved_source_plugin="$(cd "${LOCAL_MARKETPLACE_ROOT}/plugins/cat" && pwd -P)"
  mkdir -p "${CAT_PLUGIN_ROOT}"
  resolved_plugin_root="$(cd "${CAT_PLUGIN_ROOT}" && pwd -P)"
  if [[ "${resolved_plugin_root}" != "${resolved_source_plugin}" ]]; then
    chmod -R u+w "${CAT_PLUGIN_ROOT}" 2>/dev/null || true
    find "${CAT_PLUGIN_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    cp -R "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/." "${CAT_PLUGIN_ROOT}/"
  fi
fi
```

### 4. Install Runtime and Verify

Run:

```bash
mkdir -p "${CAT_PLUGIN_DATA}"
chmod -R u+w "${CAT_PLUGIN_DATA}/client" 2>/dev/null || true
rm -rf "${CAT_PLUGIN_DATA}/client"
cp -R "${FLATTENED_PLUGIN}/client" "${CAT_PLUGIN_DATA}/client"

"${CAT_PLUGIN_DATA}/client/bin/java" -version
test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_DATA}/client/VERSION"

echo "Installed CAT ${RELEASE_TAG} for ${CAT_RUNTIME}. Restart ${CAT_RUNTIME} so the updated plugin is loaded."
```
