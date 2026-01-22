---
name: box-alignment
description: "MANDATORY: Load BEFORE rendering any box output"
---

# Box Alignment Skill

## Purpose

Render properly-aligned box output by calculating content width and applying padding before output.

## When to Use

Use this skill when rendering any box output with borders (right-side `│`).

## Box Format

```
╭────────────────────────────╮
│ Content line 1             │
│ Content line 2             │
│ Longer content line here   │
╰────────────────────────────╯
```

Boxes require all lines to have equal display width so right-side `│` characters align.

## Procedure for Closed Borders

**MANDATORY: The table IS the output source. Build it first, then output directly from it.**

Work through this procedure step-by-step. The table is internal work—only show the final box to the
user.

### Step 1: Build the Table (internal) — This IS Your Output

Create this table with EVERY line that will appear in the box:

| # | Content (exact) | Chars | Emojis | Width | Pad |
|---|-----------------|-------|--------|-------|-----|
| 1 | `📊 Overall: 45%` | 14 | 📊(2) | 16 | 0 |
| 2 | `🏆 10/22 tasks` | 12 | 🏆(2) | 14 | 2 |
| 3 | `Settings saved` | 14 | — | 14 | 2 |

**Column definitions:**
- **Content**: Exact text that will appear between `│ ` and ` │`
- **Chars**: Count of non-emoji characters (letters, digits, spaces, punctuation)
- **Emojis**: List each emoji with its width from SessionStart
- **Width**: Chars + sum of emoji widths
- **Pad**: max_width − this_width (fill in after finding max)

**Max width: 16** (largest value in Width column)

### Step 2: Output Directly From Table

For each row, output: `│ ` + Content + (Pad spaces) + ` │`

```
╭──────────────────╮
│ 📊 Overall: 45%  │   ← Row 1: Content + 0 spaces
│ 🏆 10/22 tasks   │   ← Row 2: Content + 2 spaces
│ Settings saved   │   ← Row 3: Content + 2 spaces
╰──────────────────╯
```

**Border construction:**
- Total line width: max_width + 4 (for `│ ` prefix and ` │` suffix)
- Top/bottom dash count: max_width + 2 (between corner characters `╭` and `╮`)

---

**Why the table IS the source (M175, M176, M178)**

Previous failures occurred when:
- Calculations were done "mentally" (M175, M176)
- Table content differed from actual output (M178)

The table is not a calculation aid—it is the single source of truth. Output is mechanically
generated from it: take each Content value, append Pad spaces, wrap with borders.

**Key insight:** If you write output that doesn't come directly from the table's Content column,
alignment will fail. There is no "verify output matches table" step because output IS the table.

**Debugging:** When extended thinking is enabled, the calculation table is visible in the thinking
trace and can be reviewed to diagnose alignment issues.

## Special Cases

### Blank Lines

For visual separators within a box, use an empty Content value:

| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `Header` | 6 | — | 6 | 0 |
| 2 | `` | 0 | — | 0 | 6 |
| 3 | `Footer` | 6 | — | 6 | 0 |

Output: `│ ` + (max_width spaces) + ` │` for blank lines.

### Nested Boxes

For boxes containing inner boxes (e.g., status display with major version sections):

1. **Calculate inner box width first** — determine max content width for inner box
2. **Inner box lines become outer content** — each inner box line (including its borders) is one
   Content entry in the outer table
3. **Outer padding applies to entire inner lines**

Example: inner box is 30 chars wide → outer table has Content entries of 30 chars each for those
lines, plus whatever prefix/indent you want.


## Common Mistakes

### Forgetting Emoji Width from SessionStart

```
# WRONG - counted emoji as width 1
"🐱 Cat" → 5 chars, but 🐱=2 from SessionStart
Correct: 4 non-emoji chars + 🐱(2) = 6

│ 🐱 Cat │
│ Dog    │   <- misaligned because emoji width wasn't used
```

### Skipping the Calculation Table (M175, M176)

```
# WRONG - estimating without systematic calculation
"The second line looks longest, I'll use that as max..."

# WRONG - showing partial calculations
"Line 1 is about 16 chars, line 2 is about 14..."

# Result: misaligned box because widths weren't precisely calculated
```

**Always build the full table.** This catches errors that estimation misses, especially when:
- Lines have different emoji counts
- Lines have similar visual length
- There are many lines (easy to miss one)

### Why Table-as-Source Exists (M177, M178)

Previous approach: build table, then write output separately, then verify they match.

This failed because content would diverge (e.g., table had `v1.1 (8/8)` but output had
`v1.1: Core Rewrite (8/8)` — 11 chars longer). Verification steps were skipped or ineffective.

Current approach makes divergence impossible: the table Content column IS the output.

## Example with Emojis

Given SessionStart widths: `🐱=2, ✅=2`

**Step 1: Build the table (this IS the output source):**

| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `🐱 CAT initialized` | 16 | 🐱(2) | 18 | 0 |
| 2 | `✅ Trust: high` | 12 | ✅(2) | 14 | 4 |
| 3 | `Settings saved` | 14 | — | 14 | 4 |

**Max width: 18**

**Step 2: Output directly from table (Content + Pad spaces):**

```
╭────────────────────╮
│ 🐱 CAT initialized │   ← Row 1 Content + 0 spaces
│ ✅ Trust: high     │   ← Row 2 Content + 4 spaces
│ Settings saved     │   ← Row 3 Content + 4 spaces
╰────────────────────╯
```

