# License Headers

All source files in the CAT project must include a license header referencing the CAT Commercial License.

## Source vs Release Files

- **Source tree:** All source files must carry a license header unless they are explicitly listed under Exemptions.
  This includes plugin instruction sources that are later read by agents.
- **Flattened release artifacts:** Agent-facing generated files must not carry license headers. The release processor
  strips source headers from files under `client/distribution/target/engine/**/{agents,concepts,rules,skills}/` before
  users install the plugin.
- **Do not solve release token waste by omitting source headers.** Add the source header, then strip it during release
  processing when the file is agent-facing.

## Header Text

The standard header text is:

```
Copyright (c) 2026 Gili Tzabari. All rights reserved.

Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
```

The copyright year (2026) is the year the source code was first written. Do not update it over time. When code is moved
or renamed, the copyright year remains the year the source code was originally written.

**IMPORTANT:** No blank line should appear between the license header and the first line of code (package declaration,
imports, etc.). The header should be immediately followed by the code.

## File Type Formats

### Java Files (*.java)

Block comment before `package` declaration. For `module-info.java`, place at the top of the file.

```java
/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;
```

### Shell Scripts (*.sh)

Hash comments after the shebang line.

```bash
#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail
```

### Markdown Files (*.md)

HTML comments at the top of the file. For files with YAML frontmatter, the license header goes AFTER the frontmatter
block.

**Standard placement (no frontmatter):**

```markdown
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Document Title
```

**With YAML frontmatter:**

```markdown
---
description: Some description
user-invocable: true
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Document Title
```

### TOML Files (*.toml)

Hash comments at the top of the file.

```toml
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
name = "cat-example"
```

**Agent-facing source files:** Plugin instruction sources carry license headers in the source tree. The flattened
release processor strips these headers from agent-facing release files before installation so engine context does not
waste tokens on license boilerplate.

### JSON Files (*.json)

JSON does not support comments. JSON files are **exempt** from license headers.

## Exemptions

The following files do not require license headers:

- `*.json` files (no comment syntax)
- `*.xml` files (configuration files, no semantic code)
- Engine-loaded project instruction files
- All `*.md` files under engine-specific project rule directories outside `.cat/rules/**`
- Agent-facing files in flattened release artifacts under `client/distribution/target/engine/**` (generated files; the
  release processor strips source license headers before installation)
- Files under `.cat/rules/**` (project rule files loaded into agent context)
- Files in `.cat/` (planning artifacts, config, engine data)
- `LICENSE.md` itself
- Build artifacts (`target/`, `node_modules/`, etc.)
- Project root documentation (`README.md`, `changelog.md`)
