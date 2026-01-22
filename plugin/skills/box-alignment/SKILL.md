---
name: box-alignment
description: "MANDATORY: Load BEFORE rendering any box output"
---

# Box Alignment Skill

## Purpose

Render properly-aligned box output by calculating content width and applying padding before output.

## When to Use

Use this skill when rendering any box output with closed borders (right-side `│`).

**Open-border format** (left border only) does not require this skill - no alignment needed.

## Box Types

### Open Border (No Alignment Needed)

```
╭─
│ Content line 1
│ Content line 2
│ Longer content line here
╰─
```

### Closed Border (Alignment Required)

```
╭────────────────────────────╮
│ Content line 1             │
│ Content line 2             │
│ Longer content line here   │
╰────────────────────────────╯
```

Closed borders require all lines to have equal display width so right-side `│` characters align.

## Procedure for Closed Borders

**MANDATORY: Build a calculation table before outputting any box.**

Work through this procedure step-by-step. Do NOT output calculations to the user—only output the
final box. The table is for your internal use to ensure correct alignment.

### Step 1: Build the Calculation Table (internal)

Create this table with EVERY line that will appear in the box:

| # | Content (exact) | Chars | Emojis | Width | Pad |
|---|-----------------|-------|--------|-------|-----|
| 1 | `📊 Overall: 45%` | 14 | 📊(2) | 16 | 0 |
| 2 | `🏆 10/22 tasks` | 12 | 🏆(2) | 14 | 2 |
| 3 | `Settings saved` | 14 | — | 14 | 2 |

**Column definitions:**
- **Content**: Exact text between `│ ` and ` │` (copy-paste, don't paraphrase)
- **Chars**: Count of non-emoji characters (letters, digits, spaces, punctuation)
- **Emojis**: List each emoji with its width from SessionStart
- **Width**: Chars + sum of emoji widths
- **Pad**: max_width − this_width (fill in after finding max)

**Max width: 16** (largest value in Width column)

### Step 2: Verify Line Count (internal)

Before proceeding, verify completeness:

- Lines in table: **3**
- Content lines in final box: **3**
- **Match? YES** → proceed / **NO** → add missing lines to table

### Step 3: Output the Box (this is the only visible output)

Apply padding from the Pad column to each line:

```
╭──────────────────╮
│ 📊 Overall: 45%  │
│ 🏆 10/22 tasks   │
│ Settings saved   │
╰──────────────────╯
```

**Border width:** max_width + 4 (for `│ ` prefix and ` │` suffix)

---

**Why use a table? (M175, M176)**

Previous failures occurred when calculations were done "mentally" or estimated.
The table format:
- Forces enumeration of every line (omissions become obvious)
- Makes width calculations systematic and precise
- Catches errors before output, not after

The table is internal work—only output the final box to the user.

**Debugging:** When extended thinking is enabled, the calculation table is visible in the thinking
trace and can be reviewed to diagnose alignment issues.

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

### Copy-Paste Workflow (M177)

Use this workflow to ensure table calculations match final output:

1. **Write final content first** - Draft the exact text that will appear in each line
2. **Copy-paste into table** - Select and paste each line into the Content column
3. **Calculate from pasted content** - Count characters in the pasted strings
4. **Output the same content** - Use the exact strings from your table in the final box

**Why copy-paste matters:** The Content column and final output must be identical strings.
Typing similar content separately risks differences (e.g., `v1.1: (8/8)` vs
`v1.1: Core Rewrite (8/8)` → 11-character width difference).

## Example with Emojis

Given SessionStart widths: `🐱=2, ✅=2`

**Build the calculation table (internal work, not shown to user):**

| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `🐱 CAT initialized` | 16 | 🐱(2) | 18 | 0 |
| 2 | `✅ Trust: high` | 12 | ✅(2) | 14 | 4 |
| 3 | `Settings saved` | 14 | — | 14 | 4 |

**Max width: 18**

**Line count check:** 3 table rows, 3 box lines ✓

**Output:**

```
╭────────────────────╮
│ 🐱 CAT initialized │
│ ✅ Trust: high     │
│ Settings saved     │
╰────────────────────╯
```

## Recommendation

**Prefer open-border format** when possible - it avoids alignment complexity entirely.
Use closed borders only when visual containment is specifically required.
