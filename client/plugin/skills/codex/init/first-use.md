<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Initialize CAT

Initialize CAT planning files and rules for the current engine.

## Steps

1. Verify the project is not already initialized.

```bash
[ -f .cat/project.md ] && echo "ERROR: CAT already initialized" && exit 1
[ -d .git ] || git init
```

2. Create the portable and engine-specific directories.

```bash
mkdir -p .cat/issues .cat/rules/common .cat/rules/codex .codex
```

3. Copy project templates from the installed plugin.

```bash
<!-- cat:include ../../include/codex-home-bootstrap.md -->
: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}"
cp "${CAT_PLUGIN_ROOT}/templates/project.md" .cat/project.md
cp "${CAT_PLUGIN_ROOT}/templates/roadmap.md" .cat/roadmap.md
cp "${CAT_PLUGIN_ROOT}/templates/config.json" .cat/config.json
```

4. Install bundled portable and engine-specific rules when present.

```bash
<!-- cat:include ../../include/codex-home-bootstrap.md -->
: "${CAT_PLUGIN_ROOT:?CAT_PLUGIN_ROOT is required}"
if [ -d "${CAT_PLUGIN_ROOT}/rules/common" ]; then
  cp -R "${CAT_PLUGIN_ROOT}/rules/common/." .cat/rules/common/
fi
if [ -d "${CAT_PLUGIN_ROOT}/rules/codex" ]; then
  cp -R "${CAT_PLUGIN_ROOT}/rules/codex/." .cat/rules/codex/
fi
```

5. Stage the initialized files.

```bash
git add .cat/ .codex/
```
