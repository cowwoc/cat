<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Uninstall CAT

Remove CAT-owned Codex agent files from the current project before delegating to Codex's built-in
plugin uninstaller.

```bash
set -euo pipefail

removed=0
if [[ -d .codex/agents ]]; then
  while IFS= read -r agent_file; do
    rm -f "$agent_file"
    removed=$((removed + 1))
    echo "Removed $agent_file"
  done < <(find .codex/agents -maxdepth 1 \( -type f -o -type l \) -name 'cat-*.toml' | sort)
fi

if [[ "$removed" -eq 0 ]]; then
  echo "No CAT-owned Codex agent entries found in .codex/agents."
fi

if codex plugin uninstall --help >/dev/null 2>&1; then
  codex plugin uninstall cat
elif codex plugin remove --help >/dev/null 2>&1; then
  codex plugin remove cat
else
  cat <<'EOF'
Codex CLI does not expose a plugin uninstall command in this version.
Use Codex's built-in plugin browser/uninstaller to uninstall CAT.
EOF
fi
```
