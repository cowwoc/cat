# Plan: add-license-headers

## Goal
Add a license header referencing the CAT Source-Available Commercial License to all source files in the project, and
establish a project convention to ensure all future files include the same header.

## Satisfies
None - infrastructure/compliance issue

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Large number of files (~375) to modify; header format must be compatible with all file types
- **Mitigation:** Use file-type-appropriate comment syntax; verify no files break after modification

## License Header

The header text references the LICENSE.md and copyright holder:

```
Copyright (c) 2026 Gili Tzabari. All rights reserved.

Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
```

### Comment Syntax by File Type

| File Type | Comment Syntax | Header Format |
|-----------|----------------|---------------|
| Java (*.java) | `/* */` | Block comment before package declaration |
| Python (*.py) | `#` | Hash comments at top of file (after shebang if present) |
| Shell (*.sh) | `#` | Hash comments after shebang line |
| Markdown (*.md) | `<!-- -->` | HTML comment at top of file |
| JSON (*.json) | N/A | **Skip** - JSON does not support comments |
| XML (*.xml) | `<!-- -->` | XML comment after XML declaration if present |

### Java Header
```java
/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Source-Available Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
```

### Python Header
```python
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Source-Available Commercial License.
# See LICENSE.md in the project root for license terms.
```

### Shell Header (after shebang)
```bash
#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Source-Available Commercial License.
# See LICENSE.md in the project root for license terms.
```

### Markdown Header
```markdown
<!-- Copyright (c) 2026 Gili Tzabari. All rights reserved. -->
<!-- Licensed under the CAT Source-Available Commercial License. -->
<!-- See LICENSE.md in the project root for license terms. -->
```

### XML Header (after XML declaration)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (c) 2026 Gili Tzabari. All rights reserved. -->
<!-- Licensed under the CAT Source-Available Commercial License. -->
<!-- See LICENSE.md in the project root for license terms. -->
```

## Files to Modify

### Java Files (~160 files)
- All `*.java` files under `hooks/src/main/java/` and `hooks/src/test/java/`
- Insert block comment before `package` declaration
- Include `module-info.java` files (these have no `package` declaration - insert at top of file)

### Python Files (~17 files)
- All `*.py` files under `plugin/scripts/` and `plugin/hooks/`
- Insert hash comments at top (after shebang if present)

### Shell Scripts (~48 files)
- All `*.sh` files under `plugin/` and `hooks/`
- Insert hash comments after shebang line

### Markdown Files (~138 files)
- All `*.md` files under `plugin/skills/`, `plugin/concepts/`, `plugin/commands/`
- Insert HTML comment at top of file
- **Skip:** `LICENSE.md`, `README.md`, `CHANGELOG.md` in project root
- **Skip:** `PLAN.md`, `STATE.md` files in `.claude/cat/issues/` (planning artifacts)
- **Skip:** Files in `.claude/cat/` directory (planning/config, not distributed source)

### XML Files (~4 files)
- `hooks/config/checkstyle.xml`, `hooks/config/pmd-main.xml`, `hooks/config/pmd-test.xml`
- `hooks/pom.xml`
- Insert XML comment after XML declaration

### Files to Skip
- `*.json` files (JSON does not support comments)
- Files in `.claude/cat/issues/` (planning artifacts)
- Files in `.claude/cat/` config directory
- `LICENSE.md` itself
- `node_modules/`, `target/`, build artifacts

## Convention for Future Files

### File: CLAUDE.md
Add to the Project Instructions section:

```markdown
## License Headers

**MANDATORY:** All new source files must include a license header at the top.

Use the appropriate comment syntax for the file type. The header text is:

    Copyright (c) 2026 Gili Tzabari. All rights reserved.

    Licensed under the CAT Commercial License.
    See LICENSE.md in the project root for license terms.

The copyright year is the year the file was first created. Do not update it over time.

See `plugin/concepts/license-header.md` for file-type-specific formats.

JSON files are exempt (no comment syntax).
```

### File: plugin/concepts/license-header.md
Create a reference document with all header formats by file type (Java, Python, Shell, Markdown, XML).

## Acceptance Criteria
- [ ] All Java files have license header block comment before package/module declaration
- [ ] All Python files have license header hash comments at top (after shebang if present)
- [ ] All Shell scripts have license header hash comments after shebang line
- [ ] All Markdown source files (skills, concepts, commands) have HTML comment header
- [ ] All XML files have comment header after XML declaration
- [ ] JSON files are not modified (no comment syntax)
- [ ] CLAUDE.md updated with license header convention for future files
- [ ] plugin/concepts/license-header.md created with all header formats
- [ ] All tests pass after modifications
- [ ] No files broken by header insertion

## Execution Steps
1. **Create license-header.md reference:** Create `plugin/concepts/license-header.md` with all header formats by file
   type
   - Files: `plugin/concepts/license-header.md`
2. **Add convention to CLAUDE.md:** Add License Headers section to project instructions
   - Files: `CLAUDE.md`
3. **Add headers to Java files:** Insert block comment before package/module declaration in all `*.java` files under
   `hooks/src/`
   - Files: All `hooks/src/**/*.java` (~160 files)
4. **Add headers to Python files:** Insert hash comment header at top of all `*.py` files under `plugin/`
   - Files: All `plugin/**/*.py` (~17 files)
5. **Add headers to Shell scripts:** Insert hash comment header after shebang in all `*.sh` files
   - Files: All `plugin/**/*.sh` and `hooks/*.sh` (~48 files)
6. **Add headers to Markdown source files:** Insert HTML comment header at top of all `*.md` files in plugin/skills/,
   plugin/concepts/, plugin/commands/
   - Files: All `plugin/skills/**/*.md`, `plugin/concepts/*.md`, `plugin/commands/*.md` (~138 files)
7. **Add headers to XML files:** Insert XML comment after XML declaration
   - Files: `hooks/config/*.xml`, `hooks/pom.xml` (~4 files)
8. **Run tests:** Execute `mvn -f hooks/pom.xml verify` to verify no breakage
   - Files: None (validation step)

## Success Criteria
- [ ] 100% of Java source files contain the license header
- [ ] 100% of Python source files contain the license header
- [ ] 100% of Shell scripts contain the license header
- [ ] 100% of Markdown source files (skills/concepts/commands) contain the license header
- [ ] 100% of XML config files contain the license header
- [ ] CLAUDE.md contains the license header convention
- [ ] license-header.md reference document exists
- [ ] All tests pass (mvn -f hooks/pom.xml verify exits 0)
