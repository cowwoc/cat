# License Headers

All source files in the CAT project must include a license header referencing the CAT Commercial License.

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
package io.github.cowwoc.cat.hooks;
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

**Skill files:** `SKILL.md` files are exempt from license headers; SkillLoader processes these files and license headers
would waste tokens. `first-use.md` companion files are NOT exempt and require headers.

### JSON Files (*.json)

JSON does not support comments. JSON files are **exempt** from license headers.

## Exemptions

The following files do not require license headers:

- `*.json` files (no comment syntax)
- `*.xml` files (configuration files, no semantic code)
- All `SKILL.md` files in plugin skills (`first-use.md` companions are NOT exempt and require headers)
- All `*.md` files in `plugin/agents/` (injected into subagent context as prompts; same rationale as SKILL.md)
- Files in `.claude/cat/` (planning artifacts, config, runtime data)
- `LICENSE.md` itself
- Build artifacts (`target/`, `node_modules/`, etc.)
- Project root documentation (`README.md`, `CHANGELOG.md`)
