---
description: >
  Guide for formatting Claude-facing documentation with proper line wrapping and YAML multi-line
  syntax
tags:
  - documentation
  - formatting
  - yaml
---

# Format Documentation Skill

Use this skill when editing Claude-facing documentation files to ensure proper formatting.

## Maximum Line Length

**110 characters** for Claude-facing documentation files

## Format-Safe Wrapping Rules

| Format | Technique | Example |
|--------|-----------|---------|
| **YAML frontmatter** | Use `>` or `|` for multi-line | `description: >` + indented continuation |
| **Markdown prose** | Break at word boundaries | Natural paragraph flow |
| **Code blocks** | Do NOT wrap | Leave as-is (preserve formatting) |
| **URLs** | Do NOT wrap | Leave as-is (would break link) |
| **Tables** | Do NOT wrap | Leave as-is (would break structure) |
| **Inline code** | Do NOT wrap within backticks | Leave as-is |

## YAML Multi-line String Syntax

**Problem**: Long YAML values exceed 110 character limit

**Solution**: Use folded (`>`) or literal (`|`) style operators

**Example - Folded Style** (for prose):
```yaml
# BEFORE (unsafe - long line):
description: This is a very long description that exceeds 110 characters and would cause readability issues

# AFTER (safe - using folded style >):
description: >
  This is a very long description that exceeds 110 characters
  and would cause readability issues
```

**Key YAML Operators**:
- `>` (folded): Newlines become spaces (use for prose)
- `|` (literal): Newlines preserved (use for code/commands)
- `>-` / `|-`: Same but strips trailing newline

**Example - Literal Style** (for code):
```yaml
# For commands or code that must preserve line breaks
command: |
  git commit -m "$(cat <<'EOF'
  Multi-line commit message
  with preserved formatting
  EOF
  )"
```

## Markdown Wrapping

**Technique**: Break at word boundaries, maintain natural paragraph flow

**Example**:
```markdown
# BEFORE (exceeds 110 chars):
This is a very long line of markdown prose that exceeds the 110 character limit and should be wrapped for readability.

# AFTER (wrapped at word boundary):
This is a very long line of markdown prose that exceeds the 110 character
limit and should be wrapped for readability.
```

**Tips**:
- Break after complete phrases when possible
- Maintain sentence flow across line breaks
- Avoid breaking in middle of inline code spans
- Preserve blank lines between paragraphs

## Applies To

**Claude-facing documentation** (110 char limit):
- `CLAUDE.md`
- `.claude/` configuration files (hooks, skills, commands)
- `docs/project/` protocol documentation
- `docs/code-style/*-claude.md` detection patterns

**Human-facing documentation** (no strict limit):
- `README.md`
- `changelog.md`
- `docs/code-style/*-human.md` explanations

## Workflow

When editing Claude-facing docs:

1. **Check line length**: Look for lines exceeding 110 characters
2. **Identify format**: YAML frontmatter, markdown prose, code block, etc.
3. **Apply appropriate technique**:
   - YAML: Use `>` or `|` operators
   - Prose: Break at word boundaries
   - Code/URLs/Tables: Leave unwrapped
4. **Verify readability**: Ensure wrapped text flows naturally
5. **Test if YAML**: Validate YAML syntax if you modified frontmatter

## Common Mistakes

❌ **Wrapping URLs**: Breaks links
❌ **Wrapping code blocks**: Breaks formatting
❌ **Forgetting YAML operator**: Long description without `>` still violates limit
❌ **Breaking mid-word**: Use word boundaries only
❌ **Inconsistent indentation**: YAML multi-line requires consistent indent (usually 2 spaces)

## Examples

**YAML Frontmatter - Before/After**:
```yaml
# BEFORE (violation):
description: Comprehensive guide for formatting markdown documentation with proper line wrapping and YAML syntax

# AFTER (correct):
description: >
  Comprehensive guide for formatting markdown documentation with proper line
  wrapping and YAML syntax
```

**Markdown Prose - Before/After**:
```markdown
# BEFORE (violation):
This mandatory protocol ensures that all agents follow the complete backup-verify-cleanup workflow when performing git operations to prevent data loss.

# AFTER (correct):
This mandatory protocol ensures that all agents follow the complete
backup-verify-cleanup workflow when performing git operations to prevent data
loss.
```

**Leave Unwrapped - Code Block**:
```markdown
# CORRECT (code block not wrapped even if long):
```bash
git commit -m "$(cat <<'EOF'
Very long commit message that exceeds 110 characters but must be preserved exactly as-is
EOF
)"
\```
```

## Validation

After formatting:
- Lines ≤110 chars (except unwrappable content)
- YAML parses correctly (test with `yq` or `python -c "import yaml"`)
- Markdown renders properly
- URLs work
- Code blocks execute correctly
