# Plan: render-diff-skill

## Goal
Create a `/cat:render-diff` skill that transforms raw git diff output into a 4-column table format with line numbers,
diff symbols, and content - optimized for approval gate reviews.

## Satisfies
- None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Word-level diff algorithm complexity; whitespace detection edge cases
- **Mitigation:** Start with simple heuristics, iterate based on usage

## Files to Create
- plugin/skills/render-diff/SKILL.md - Skill documentation
- plugin/scripts/render-diff.sh - Bash script to transform git diff

## Files to Modify
- None initially (skill is standalone, workflows reference it on demand)

## Configuration
The script reads settings from `.claude/cat/cat-config.json`:
- `terminalWidth` - Controls the total width of the diff table (default: 50)

The table layout adapts to the configured width:
- Fixed columns: Old (4), Symbol (3), New (4) = 11 chars
- Borders and padding: 6 chars
- Content column: remaining width (terminalWidth - 17)

## Output Format Specification

### Structure Overview
- Each **hunk** is rendered as a self-contained box
- File name is repeated in each hunk's header (provides context when scrolling)
- Hunks separated by empty lines (no `…` separator needed)
- Legend appears once at the end

### Hunk Box Format
```
╭────────────────────────────────────────────────╮
│ FILE: src/api.js                               │
├────┬───┬────┬──────────────────────────────────┤
│ Old│   │ New│ ⌁ function init()                │
├────┼───┼────┼──────────────────────────────────┤
│   6│   │   6│   const app = express();         │
│   7│   │   7│   app.use(cors());               │
│   8│ - │    │   app.use([bodyParser].json());  │
│    │ + │   8│   app.use([express].json());     │
│   9│   │   9│   return app;                    │
╰────┴───┴────┴──────────────────────────────────╯
```

### Column Header Row
The column header row includes hunk context (function/class name from git):
```
│ Old│   │ New│ ⌁ function doSomething()         │
```
- `Old`, `New` are column labels
- Symbol column is blank in header
- `⌁` (ELECTRIC ARROW) marks the hunk context
- Context extracted from `@@ ... @@ function_name` in diff

### Column Definitions
| Column | Width | Content |
|--------|-------|---------|
| Old | 4 chars | Line number in original file (blank for additions) |
| Symbol | 3 chars | `-` removed, `+` added, blank for context |
| New | 4 chars | Line number in new file (blank for deletions) |
| Content | remaining | The actual line text |

### Multiple Hunks in Same File
Each hunk gets its own complete box with file header repeated:
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
```

### Word-Level Highlighting
When a deletion is immediately followed by an addition, highlight changed portions with `[]`:
```
│   8│ - │    │   app.use([bodyParser].json());  │
│    │ + │   8│   app.use([express].json());     │
```
- Only applies to adjacent -/+ pairs
- Brackets surround the changed portion
- If entire line changed, no brackets (would be redundant)
- Skip word-diff for lines that wrap (too complex to read)

### Whitespace Visibility
Show markers only when whitespace IS the change:
```
│  16│ - │    │ →const indent = 1;               │
│    │ + │  16│ ····const indent = 1;            │
```
- `·` (middle dot) for spaces
- `→` for tabs
- Only shown for whitespace-only changes or trailing whitespace changes

### Line Continuation (long lines)
When content exceeds width, use `↩` and continue with all column separators:
```
│  46│ - │    │   logger.info(`Server running ↩  │
│    │   │    │ on port ${port}`);               │
```
- Continuation rows keep all `│` column separators
- Blank line number columns (previous numbers remain in force)
- No extra whitespace added - text continues exactly where cut
- `↩` appears at cut point

### Binary File Indicators
```
╭────────────────────────────────────────────────╮
│ FILE: image.png (binary)                       │
├────────────────────────────────────────────────┤
│ Binary file changed                            │
╰────────────────────────────────────────────────╯
```

### Renamed/Moved File Indicators
```
╭────────────────────────────────────────────────╮
│ FILE: old/path.js → new/path.js (renamed)      │
├────────────────────────────────────────────────┤
│ File renamed (no content changes)              │
╰────────────────────────────────────────────────╯
```
If content also changed, show normal diff boxes below.

### Legend (appears once at end)
```
╭────────────────────────────────────────────────╮
│ Legend                                         │
├────────────────────────────────────────────────┤
│  -  del    +  add    []  changed    ·  space   │
╰────────────────────────────────────────────────╯
```
Only includes symbols actually used in the diff.

## Acceptance Criteria

### Core Functionality
- [x] Script reads git diff (stdin or file argument)
- [x] Reads terminalWidth from cat-config.json
- [x] Outputs 4-column table format
- [x] Each hunk is self-contained box with file header
- [x] Shows 2-3 lines of context around each change
- [ ] Empty line between hunk boxes (replaces `…` separator)
- [x] Shows legend at end (only used symbols)

### New Features (v2)
- [ ] Hunk context in column header row (`⌁ function name`)
- [ ] Word-level diff highlighting with `[]`
- [ ] Whitespace visualization (`·` spaces, `→` tabs)
- [ ] Binary file indicators
- [ ] Renamed/moved file indicators

### Line Wrapping
- [ ] Long lines wrap with `↩` continuation marker
- [ ] Continuation rows keep all column separators `│`
- [ ] Blank line numbers on continuation rows

### Code Files (java, python, js, ts, sh, json, yaml)
- [x] Preserves original indentation exactly
- [x] Shows complete changed lines
- [x] Includes 2-3 lines of context around changes

## Execution Steps

1. **Step 1:** Restructure to hunk-per-box format
   - Remove task header box
   - Each hunk becomes complete box with file header
   - Add hunk context to column header row
   - Empty line between boxes
   - Verify: Multiple hunks render as separate boxes

2. **Step 2:** Implement word-level diff
   - Detect adjacent -/+ pairs
   - Find common prefix/suffix, bracket the difference
   - Skip for wrapped lines
   - Verify: Word changes highlighted correctly

3. **Step 3:** Implement whitespace visualization
   - Detect whitespace-only changes
   - Replace spaces with `·` and tabs with `→`
   - Only for whitespace-significant changes
   - Verify: Tab-to-space conversions visible

4. **Step 4:** Add binary/renamed indicators
   - Detect "Binary files differ" in diff
   - Detect "rename from/to" in diff
   - Render appropriate indicators
   - Verify: Binary and renamed files handled

5. **Step 5:** Update SKILL.md and test
   - Update documentation with new format
   - Test all new features
   - Verify: All acceptance criteria met
