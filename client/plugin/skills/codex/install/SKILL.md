---
name: install
description: Install or update the Codex CAT release artifact and bundled runtime. Use from the Codex installer plugin for release installation, or after changing client Java code, launcher generation, or plugin source files that should be reflected in an installed local CAT plugin.
model: gpt-5.4-mini
effort: medium
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

# Install CAT

Use this skill to update a local Codex-installed CAT plugin from a source checkout or issue worktree.

Codex release users invoke the release's installer plugin first. That installer plugin provides `/cat:install` before
the full CAT plugin is available, then installs the full Codex release artifact from `cowwoc/cat-artifacts`.

The workflow builds one generated artifact and updates the active local installation from it:

- **Build output**: `${SOURCE_ROOT}/client/distribution/target/runtime/codex`.
- **Plugin installation**: a local Codex marketplace containing a copy of the build output, or the supplied plugin root.
- **Runtime binaries**: the installed CAT plugin data directory, under `client/`, copied from the build output.

Use Codex's plugin installation/marketplace layer. Codex does not expose the same writable installed-plugin root
workflow as Claude Code, so local updates bootstrap a generated marketplace entry that points at a copied release
artifact. This is a Codex platform constraint; the shared product flow is still: build the release artifact, then
install that artifact through the runtime's plugin mechanism.

## Required Inputs

Infer these paths when possible, otherwise ask for the missing value:

- `SOURCE_ROOT`: the CAT source checkout containing `client/plugin/` and `client/cli/`. Default to the current git
  worktree root.
- `CAT_PLUGIN_ROOT`: the installed local CAT plugin root. Prefer the local marketplace entry for plugin `cat`, then
  `${SOURCE_ROOT}/client/distribution/target/runtime/codex`.
- `CAT_PLUGIN_DATA`: the runtime data directory. Default to `${HOME}/.codex/plugins/data/local-cat`.
- `LOCAL_MARKETPLACE_ROOT`: generated local Codex marketplace root. Default to
  `${SOURCE_ROOT}/client/plugin/target/local-marketplace/codex`.

The generated local marketplace uses a real directory copy of the release artifact, not symlinks. Rerun this skill
after changing source files so Codex can reinstall from the updated local marketplace copy.

## Steps

### 1. Resolve Paths

Run from the CAT source checkout or issue worktree:

```bash
SOURCE_ROOT="$(git rev-parse --show-toplevel)"
CAT_PLUGIN_ROOT=""
CAT_PLUGIN_DATA="${HOME}/.codex/plugins/data/local-cat"

if [[ -z "${CAT_PLUGIN_ROOT}" && -f "${HOME}/.agents/plugins/marketplace.json" ]]; then
  CAT_PLUGIN_ROOT="$(python3 - <<'PY'
import json
import os
from pathlib import Path

marketplace = Path.home() / ".agents" / "plugins" / "marketplace.json"
data = json.loads(marketplace.read_text())
for plugin in data.get("plugins", []):
    if plugin.get("name") != "cat":
        continue
    source = plugin.get("source", {})
    if source.get("source") != "local":
        continue
    path = source.get("path", "")
    if path.startswith("~/"):
        path = str(Path.home() / path[2:])
    print(Path(path).expanduser().resolve())
    break
PY
)"
fi

CAT_PLUGIN_ROOT="${CAT_PLUGIN_ROOT:-${SOURCE_ROOT}/client/distribution/target/runtime/codex}"

echo "SOURCE_ROOT=${SOURCE_ROOT}"
echo "CAT_PLUGIN_ROOT=${CAT_PLUGIN_ROOT}"
echo "CAT_PLUGIN_DATA=${CAT_PLUGIN_DATA}"
```

Stop if `SOURCE_ROOT/client/pom.xml` or `SOURCE_ROOT/client/plugin/.codex-plugin/plugin.json` does not exist.

### 2. Build the Release Artifact

Build the client runtime and release artifacts from the source checkout:

```bash
PATH="${JAVA_HOME:+${JAVA_HOME}/bin:}${PATH}" \
  "${SOURCE_ROOT}/client/mvnw" -f "${SOURCE_ROOT}/client/pom.xml" verify -e -Djlink.extra.args=--enable-assertions
```

If `jlink` is not found, rerun with `PATH="$JAVA_HOME/bin:$PATH"` after confirming `JAVA_HOME` points at a full JDK,
not a JRE.

### 3. Reinstall Plugin Files

Prefer reinstalling through Codex's local marketplace mechanism. This registers a generated local marketplace whose
`cat` plugin contains a copy of the Codex release artifact:

```bash
FLATTENED_PLUGIN="${SOURCE_ROOT}/client/distribution/target/runtime/codex"
LOCAL_MARKETPLACE_ROOT="${LOCAL_MARKETPLACE_ROOT:-${SOURCE_ROOT}/client/plugin/target/local-marketplace/codex}"

rm -rf "${LOCAL_MARKETPLACE_ROOT}"
mkdir -p "${LOCAL_MARKETPLACE_ROOT}/plugins/cat"
cp -R "${FLATTENED_PLUGIN}/." "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/"
python3 - <<'PY' "${LOCAL_MARKETPLACE_ROOT}/.agents/plugins/marketplace.json"
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps({
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
}, indent=2) + "\n")
PY

codex plugin marketplace remove cat-local 2>/dev/null || true
codex plugin marketplace add "${LOCAL_MARKETPLACE_ROOT}"
```

If the active plugin root is a separate writable directory, overlay it from the same release artifact.
Skip this step when `CAT_PLUGIN_ROOT` resolves to `${LOCAL_MARKETPLACE_ROOT}/plugins/cat`.

```bash
resolved_source_plugin="$(cd "${LOCAL_MARKETPLACE_ROOT}/plugins/cat" && pwd -P)"
mkdir -p "${CAT_PLUGIN_ROOT}"
resolved_plugin_root="$(cd "${CAT_PLUGIN_ROOT}" && pwd -P)"

if [[ "${resolved_plugin_root}" != "${resolved_source_plugin}" ]]; then
  chmod -R u+w "${CAT_PLUGIN_ROOT}" 2>/dev/null || true
  find "${CAT_PLUGIN_ROOT}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -R "${LOCAL_MARKETPLACE_ROOT}/plugins/cat/." "${CAT_PLUGIN_ROOT}/"
  echo "Reinstalled CAT Codex plugin files at ${CAT_PLUGIN_ROOT}"
else
  echo "Plugin root already contains the generated local marketplace copy; reinstall skipped."
fi
```

### 4. Install Runtime

```bash
mkdir -p "${CAT_PLUGIN_DATA}"
chmod -R u+w "${CAT_PLUGIN_DATA}/client" 2>/dev/null || true
rm -rf "${CAT_PLUGIN_DATA}/client"
cp -R "${FLATTENED_PLUGIN}/client" "${CAT_PLUGIN_DATA}/client"

echo "Installed CAT client runtime to ${CAT_PLUGIN_DATA}/client"
```

### 5. Verify

```bash
"${CAT_PLUGIN_DATA}/client/bin/java" -version
test -x "${CAT_PLUGIN_DATA}/client/bin/pre-bash"
test -f "${CAT_PLUGIN_DATA}/client/VERSION"
```

Restart Codex after updating plugin sources or runtime binaries so the session reloads skill metadata and any cached
plugin state.
