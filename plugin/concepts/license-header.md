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

### Python Files (*.py)

Hash comments at the top of the file. If the file has a shebang line, place the header after it.

```python
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
import sys
```

With shebang:

```python
#!/usr/bin/env python3
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
import sys
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

**Skill files:** For CAT plugin skills, the license header goes in `first-use.md` only, NOT in `SKILL.md`. The
`SKILL.md` file is a metadata file and is exempt from license headers.

### JSON Files (*.json)

JSON does not support comments. JSON files are **exempt** from license headers.

## Exemptions

The following files do not require license headers:

- `*.json` files (no comment syntax)
- `*.xml` files (configuration files, no semantic code)
- `SKILL.md` files in plugin skills (license goes in `first-use.md` instead)
- Files in `.claude/cat/issues/` (planning artifacts)
- Files in `.claude/cat/` config directory
- `LICENSE.md` itself
- Build artifacts (`target/`, `node_modules/`, etc.)
- Project root documentation (`README.md`, `CHANGELOG.md`)
