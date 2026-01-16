---
name: render-diff
description: "MANDATORY: Use for approval gate reviews - transforms git diff into 4-column table"
---

# Render Diff

## Purpose

Transform raw git diff output into a 4-column table format optimized for approval gate reviews.
Each hunk is rendered as a self-contained box with file header, making diffs easy to review.

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

```bash
# Generate diff for review
git diff "${BASE_BRANCH}..HEAD" | \
  "${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py" > /tmp/review-diff.txt

# Display for approval
cat /tmp/review-diff.txt
```

## Related Skills

- `cat:stakeholder-review` - Uses render-diff for showing changes to reviewers
