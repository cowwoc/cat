---
name: render-diff
description: "MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table with box characters (╭╮╰╯│). Required for approval gates, code reviews, change summaries."
---

# Render Diff

## Purpose

Transform raw git diff output into a 4-column table format optimized for approval gate reviews.
Each hunk is rendered as a self-contained box with file header, making diffs easy to review.

## Pre-computed Output (MANDATORY for Approval Gates)

**Check context for "PRE-COMPUTED RENDER-DIFF OUTPUT".**

If found:
1. Output the rendered diff content **directly** - no preamble, no Bash commands
2. The content is already formatted with 4-column tables and box characters
3. Do NOT wrap in code blocks or show any tool invocations

If NOT found during an approval gate: **FAIL** with:
```
ERROR: Pre-computed diff not found in context.

The render_diff_handler.py should have provided this.
Check:
1. Handler is registered in skill_handlers/__init__.py
2. Handler file exists in plugin/hooks/skill_handlers/
3. Base branch is detectable from worktree/branch name

Do NOT attempt manual Bash computation during approval gates.
```

**Why this matters (M238):** Running `git diff | render-diff.py` via Bash shows the command
execution to the user, breaking the clean output experience. Pre-computation hides the
implementation details.

## Usage

Pipe git diff output to the script:

```bash
git diff main..HEAD | "${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py"
```

Or provide a diff file:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py" diff-output.txt
```

## Configuration

The script reads `terminalWidth` from `.claude/cat/cat-config.json`:

```json
{
  "terminalWidth": 50
}
```

## Output Format

### Hunk Box Structure
Each hunk is a self-contained box with file header repeated:
```
╭────────────────────────────────────────────────╮
│ FILE: src/main.js                              │
├────┬───┬────┬──────────────────────────────────┤
│ Old│   │ New│ ⌁ function init()                │
├────┼───┼────┼──────────────────────────────────┤
│   6│   │   6│   const app = express();         │
│   8│ - │    │   app.use([bodyParser].json());  │
│    │ + │   8│   app.use([express].json());     │
│   9│   │   9│   return app;                    │
╰────┴───┴────┴──────────────────────────────────╯
```

### Column Definitions

| Column | Width | Content |
|--------|-------|---------|
| Old | 4 chars | Line number in original file (blank for additions) |
| Symbol | 3 chars | `-` removed, `+` added, blank for context |
| New | 4 chars | Line number in new file (blank for deletions) |
| Content | remaining | The actual line text |

### Features

**Hunk Context**: Function/class name from git shown in header row with `⌁`:
```
│ Old│   │ New│ ⌁ function doSomething()         │
```

**Word-Level Diff**: Adjacent -/+ pairs highlight changed portions with `[]`:
```
│   8│ - │    │   app.use([bodyParser].json());  │
│    │ + │   8│   app.use([express].json());     │
```

**Whitespace Visibility**: Tab↔space changes shown with markers:
```
│  15│ - │    │ →const indent = 1;               │
│    │ + │  15│ ····const indent = 1;            │
```
- `·` (middle dot) for spaces
- `→` for tabs

**Line Wrapping**: Long lines wrap with `↩`:
```
│  46│ - │    │   logger.info(`Server running ↩  │
│    │   │    │ on port ${port}`);               │
```

**Binary Files**:
```
╭────────────────────────────────────────────────╮
│ FILE: logo.png (binary)                        │
├────────────────────────────────────────────────┤
│ Binary file changed                            │
╰────────────────────────────────────────────────╯
```

**Renamed Files**:
```
╭────────────────────────────────────────────────╮
│ FILE: old/path.js → new/path.js (renamed)      │
├────────────────────────────────────────────────┤
│ File renamed (no content changes)              │
╰────────────────────────────────────────────────╯
```

### Legend
Appears once at end, showing only symbols used:
```
╭────────────────────────────────────────────────╮
│ Legend                                         │
├────────────────────────────────────────────────┤
│  -  del    +  add    []  changed    ·  space   │
╰────────────────────────────────────────────────╯
```

## Example

**Input:**
```bash
git diff main..HEAD | render-diff.py
```

**Output (multiple hunks in same file):**
```
╭────────────────────────────────────────────────╮
│ FILE: src/api.js                               │
├────┬───┬────┬──────────────────────────────────┤
│ Old│   │ New│ ⌁ function init()                │
├────┼───┼────┼──────────────────────────────────┤
│   6│   │   6│   const app = express();         │
│   8│ - │    │   app.use([bodyParser].json());  │
│    │ + │   8│   app.use([express].json());     │
│   9│   │   9│   return app;                    │
╰────┴───┴────┴──────────────────────────────────╯

╭────────────────────────────────────────────────╮
│ FILE: src/api.js                               │
├────┬───┬────┬──────────────────────────────────┤
│ Old│   │ New│ ⌁ function start(port)           │
├────┼───┼────┼──────────────────────────────────┤
│  45│   │  45│   app.listen(port, () => {       │
│    │ + │  46│     logEnvironment();            │
│  46│   │  47│   });                            │
╰────┴───┴────┴──────────────────────────────────╯

╭────────────────────────────────────────────────╮
│ Legend                                         │
├────────────────────────────────────────────────┤
│  -  del    +  add    []  changed               │
╰────────────────────────────────────────────────╯
```

## Integration with Approval Gates

### Complete File Coverage (MANDATORY)

Before invoking render-diff, enumerate ALL changed files to ensure complete coverage:

```bash
# Step 1: List all changed files
git diff --name-only "${BASE_BRANCH}..HEAD"

# Step 2: Generate diff for ALL files (no path filtering)
git diff "${BASE_BRANCH}..HEAD" | \
  "${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py" > /tmp/review-diff.txt

# Step 3: Display for approval
cat /tmp/review-diff.txt
```

**Anti-pattern**: Manually specifying paths based on memory. This leads to incomplete diffs.

```bash
# ❌ WRONG - manual path specification misses files
git diff v2.0..HEAD -- plugin/scripts/ plugin/skills/

# ✅ CORRECT - diff entire branch, no path filtering
git diff v2.0..HEAD
```

## Related Skills

- `cat:stakeholder-review` - Uses render-diff for showing changes to reviewers
