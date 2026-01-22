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

**MANDATORY: The table IS the output source. Build it in steps, then output directly from it.**

Work through these steps in order. The table is internal work—only show the final box to the user.

### Step 1: Fill Content, Chars, Emojis, Width (leave Pad empty)

Create the table with ALL rows. Fill columns Content through Width. Leave Pad blank.

| # | Content (exact) | Chars | Emojis | Width | Pad |
|---|-----------------|-------|--------|-------|-----|
| 1 | `📊 Overall: 45%` | 14 | 📊(2) | 16 | |
| 2 | `🏆 10/22 tasks` | 12 | 🏆(2) | 14 | |
| 3 | `Settings saved` | 14 | — | 14 | |

**Column definitions:**
- **Content**: Exact text that will appear between `│ ` and ` │`
- **Chars**: Count of non-emoji characters (letters, digits, spaces, punctuation)
- **Emojis**: List each emoji with its width from SessionStart
- **Width**: Chars + sum of emoji widths

### Step 2: Find max_width

Scan the Width column. The largest value is max_width.

**Max width: 16** (from row 1)

### Step 3: Fill Pad column

For each row: Pad = max_width − Width

| # | Content (exact) | Chars | Emojis | Width | Pad |
|---|-----------------|-------|--------|-------|-----|
| 1 | `📊 Overall: 45%` | 14 | 📊(2) | 16 | 0 |
| 2 | `🏆 10/22 tasks` | 12 | 🏆(2) | 14 | 2 |
| 3 | `Settings saved` | 14 | — | 14 | 2 |

### Step 4: MANDATORY Verification Gate (M180, M181, M182)

**STOP. Before ANY output, verify:**

1. **Table is COMPLETE** — every line that will appear in the box has a row
2. **All Pad values ≥ 0** — if any are negative, max_width is wrong
3. **Max width is stated** — explicitly written after the table
4. **No "Let me recalculate"** — if you need to recalculate, the table is not done
5. **Verify ALL lines (M181, M182)** — for EVERY row, verify Chars by listing each character with
   its position number. This is the ONLY way to guarantee alignment.

**BLOCKING CONDITION:** Do not proceed to Step 5 until all five checks pass.

**Verification format (compact):**
```
Row 1: `🏆 v1 Multi-Agent (8/8)` → _ v 1 _ M u l t i - A g e n t _ ( 8 / 8 ) = 21 chars + 🏆(2) = 23 ✓
Row 2: `☑️ v1.0 (1/1)` → _ v 1 . 0 _ ( 1 / 1 ) = 12 chars + ☑️(2) = 14 ✓
...
```

**Why ALL lines (M182):** Sampling (checking only some lines) still allows errors in unchecked
lines. Character counting errors can occur anywhere. The only way to guarantee correct alignment
is to verify every single line. Yes, this is tedious for large tables—that's the cost of correct
output.

**Why this gate exists (M180):** Iterative table-building causes errors. When the table is built
incrementally with recalculations mid-stream, content drifts between iterations. Complete the ENTIRE
table first, verify it, THEN output.

### Step 5: Output Directly From Table

For each row, output: `│ ` + Content + (Pad spaces) + ` │`

**COPY-PASTE WORKFLOW (M179):**
1. Look at row N in your table
2. Copy the EXACT Content value (character-for-character)
3. Append exactly Pad spaces (count them: 1, 2, 3...)
4. Wrap with `│ ` prefix and ` │` suffix
5. Repeat for every row

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

### Nested Boxes (M184, M186)

For boxes containing inner boxes (e.g., status display with major version sections), build from
inside out using separate tables.

**MANDATORY: Build inner boxes FIRST, then embed completed inner box lines into outer table.**

#### Two-Pass Execution (M186)

**CRITICAL: Nested boxes require TWO SEPARATE PASSES. Complete Pass 1 entirely before starting Pass 2.**

| Pass | Purpose | Output |
|------|---------|--------|
| **Pass 1: PLAN** | Build ALL tables, calculate ALL widths, construct ALL inner box lines | Tables only (no box output) |
| **Pass 2: RENDER** | Copy-paste from completed tables to produce final output | Final box display |

**Why two passes (M186):** Single-pass attempts fail because:
- Inner box widths aren't known until inner tables are complete
- Outer table can't be built until inner boxes are fully constructed
- Attempting to calculate and render simultaneously causes width mismatches

**Pass 1 Checklist (complete ALL before any output):**
- [ ] Built table for inner box 1, found max_width_1
- [ ] Constructed ALL inner box 1 lines (top, content rows, bottom) - verified same width
- [ ] Built table for inner box 2, found max_width_2
- [ ] Constructed ALL inner box 2 lines (top, content rows, bottom) - verified same width
- [ ] (Repeat for each inner box)
- [ ] Built outer table with inner box lines as Content entries
- [ ] Found outer max_width, filled all Pad values
- [ ] Verified ALL rows character-by-character

**BLOCKING GATE:** Pass 2 cannot begin until every checkbox above is checked.

**Pass 2:** Mechanically output from the completed outer table. No calculations during this pass.

#### Step N1: Build Inner Box Table

For EACH inner box, create a complete table following Steps 1-5:

```
Inner Box 1 Table:
| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `☑️ v1.0 (1/1)` | 12 | ☑️(2) | 14 | 6 |
| 2 | `☑️ v1.1 (8/8)` | 12 | ☑️(2) | 14 | 6 |
| 3 | `🔄 v1.2 (3/5)` | 12 | 🔄(2) | 14 | 6 |

Inner box max_width: 20
```

#### Step N2: Construct Inner Box Lines (M187)

Using the inner box max_width, construct ALL lines of the inner box **as explicit strings**.

```
inner_width = 20 (from inner table)
inner_border_width = inner_width + 4 = 24  (for │ + space + content + space + │)
inner_dash_count = inner_width + 2 = 22
```

**MANDATORY OUTPUT (M187): List every inner box line as a complete string:**

```
Inner Box 1 Complete Lines (all 24 display width):
Line 1: `╭─ 📦 v1: Title ──────╮`
Line 2: `│ ☑️ v1.0 (1/1)       │`
Line 3: `│ ☑️ v1.1 (8/8)       │`
Line 4: `│ 🔄 v1.2 (3/5)       │`
Line 5: `╰──────────────────────╯`
```

**BLOCKING GATE (M187):** You MUST show the complete string for EVERY line of EVERY inner box.
If you have not written out the actual strings (with backticks showing exact content), you have
NOT completed Step N2. Do NOT proceed to Step N3 until every inner box line is listed.

**Why this gate exists (M187):** Showing only tables without constructing the actual line strings
causes width mismatches. The table shows *content* but the outer table needs *complete lines
including borders*. Without explicit string construction, the border characters get miscounted.

**Verification:** Count display width of each line (chars + emoji widths). ALL lines in one inner
box must have identical display width. Top line = content lines = bottom line.

#### Step N3: Embed Inner Box Lines as Outer Content

Each complete inner box line becomes ONE Content entry in the outer table:

```
Outer Table:
| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `📊 Overall: 45%` | 14 | 📊(2) | 16 | 10 |
| 2 | `` | 0 | — | 0 | 26 |
| 3 | `╭─ 📦 v1: Title ──────╮` | 24 | 📦(2) | 26 | 0 |
| 4 | `│ ☑️ v1.0 (1/1)       │` | 24 | ☑️(2) | 26 | 0 |
| 5 | `│ ☑️ v1.1 (8/8)       │` | 24 | ☑️(2) | 26 | 0 |
| 6 | `╰──────────────────────╯` | 24 | — | 24 | 2 |

Outer max_width: 26
```

**Key insight:** The inner box borders (`│`, `╭`, `╮`, `╰`, `╯`, `─`) are part of the Content
string in the outer table. They consume character width just like any other character.

#### Step N4: Verify Inner Box Self-Consistency

Before proceeding to outer box output, verify:

1. **Inner box top width = inner box bottom width** (dash counts match)
2. **All inner content lines have same width** (from inner table verification)
3. **Inner border lines width = inner content lines width** (borders add │ + space on each side)

If any mismatch: STOP and rebuild inner box table.

#### Example: Status Display with Two Major Versions

```
Step 1: Build inner box for v1 (determine v1's content max_width = 20)
Step 2: Construct v1 inner box lines (all 24 chars wide including borders)
Step 3: Build inner box for v2 (determine v2's content max_width = 22)
Step 4: Construct v2 inner box lines (all 26 chars wide including borders)
Step 5: Build outer table with:
        - Header lines (📊, 🏆)
        - Blank line
        - All v1 inner box lines (as Content entries)
        - Blank line
        - All v2 inner box lines (as Content entries)
        - Footer lines (🎯, 📋)
Step 6: Find outer max_width (may be v2's inner box width if it's widest)
Step 7: Apply outer padding to all rows
Step 8: Output with outer borders
```

**Anti-pattern (M184):** Building one flat table mixing inner content and outer content. This fails
because inner box borders won't align—the inner box top/bottom use `─` characters while content
lines use `│`, and they end up with different widths.

**Anti-pattern (M186):** Attempting to calculate and render in a single pass. Signs of this mistake:
- Inner box tables shown but inner box lines never explicitly constructed
- Jumping to "Outer Table" without showing the actual inner box line strings
- Output appears immediately after tables without a clear "Pass 2: RENDER" transition
- Inner box right-side `│` characters don't align with each other

**Anti-pattern (M187):** Skipping Step N2 string construction. Signs of this mistake:
- Inner table shown with Content/Chars/Width columns
- Immediately followed by "Outer Table" or "Step N3"
- No explicit list of complete inner box line strings between inner table and outer table
- Result: inner box borders misaligned because border chars weren't counted in outer table Content


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

### Why Table-as-Source Exists (M177, M178, M179)

Previous approach: build table, then write output separately, then verify they match.

This failed because content would diverge (e.g., table had `v1.1 (8/8)` but output had
`v1.1: Core Rewrite (8/8)` — 11 chars longer). Verification steps were skipped or ineffective.

Current approach makes divergence impossible: the table Content column IS the output.

### Negative Pad Values Mean Calculation Error (M179)

If any Pad value is negative, the calculation is wrong. Pad = max_width - this_width, and max_width
is the LARGEST width, so Pad can never be negative.

```
# WRONG - negative Pad indicates error
| 4 | `📦 v1: Multi-Agent Architecture (IN PROGRESS)` | 44 | 📦(2) | 46 | -8 |

# This means max_width was set to 38, but this line has width 46
# Either max_width is wrong, or content needs truncation
```

When you see negative Pad: STOP. Re-examine all Width values and find the true maximum.

## Example with Emojis

Given SessionStart widths: `🐱=2, ✅=2`

**Steps 1-3: Build the table (this IS the output source):**

| # | Content | Chars | Emojis | Width | Pad |
|---|---------|-------|--------|-------|-----|
| 1 | `🐱 CAT initialized` | 16 | 🐱(2) | 18 | 0 |
| 2 | `✅ Trust: high` | 12 | ✅(2) | 14 | 4 |
| 3 | `Settings saved` | 14 | — | 14 | 4 |

**Max width: 18** (from row 1)

**Step 5: Output directly from table (Content + Pad spaces):**

```
╭────────────────────╮
│ 🐱 CAT initialized │   ← Row 1 Content + 0 spaces
│ ✅ Trust: high     │   ← Row 2 Content + 4 spaces
│ Settings saved     │   ← Row 3 Content + 4 spaces
╰────────────────────╯
```

