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

**MANDATORY: Use your thinking block to execute these steps. Do NOT output the box until all
calculations are complete in thinking.**

### Step 1: List All Content Lines (in thinking)

Write out every line that will appear in the box:

```
Line 1: "📊 Overall: 45%"
Line 2: "🏆 10/22 tasks"
Line 3: "Settings saved"
```

### Step 2: Calculate Display Width for Each Line (in thinking)

**Formula:** `len(non-emoji chars) + sum(emoji widths from SessionStart)`

```
Line 1: "📊 Overall: 45%" → 14 chars + 📊(2) = 16
Line 2: "🏆 10/22 tasks"  → 12 chars + 🏆(2) = 14
Line 3: "Settings saved"  → 14 chars + 0    = 14
```

**Alternative formula:** `len(string) + (emoji_width - 1)` per emoji
(Equivalent result, use whichever is easier to compute.)

### Step 3: Find Maximum Width (in thinking)

```
max_display_width = max(16, 14, 14) = 16
```

### Step 4: Calculate Padding for Each Line (in thinking)

```
Line 1: 16 - 16 = 0 spaces
Line 2: 16 - 14 = 2 spaces
Line 3: 16 - 14 = 2 spaces
```

### Step 5: Output Complete Box

Only NOW output the box, applying the calculated padding:

```
╭──────────────────╮
│ 📊 Overall: 45%  │
│ 🏆 10/22 tasks   │
│ Settings saved   │
╰──────────────────╯
```

## Verification Checklist

Before outputting, verify in your thinking block:

- [ ] All content lines listed explicitly
- [ ] Display width calculated for EVERY line using SessionStart emoji widths
- [ ] Maximum width identified from calculations
- [ ] Padding calculated for each line: `max - line_width`

## Common Mistakes

### Forgetting Emoji Width from SessionStart

```
# WRONG - counted emoji as width 1
"🐱 Cat" → 5 chars, but 🐱=2 from SessionStart
Correct: 4 non-emoji chars + 🐱(2) = 6

│ 🐱 Cat │
│ Dog    │   <- misaligned because emoji width wasn't used
```

### Skipping the Thinking Block (M175)

```
# WRONG - calculating "in your head" while outputting
"I'll just eyeball which line is longest..."

# Result: misaligned box because you didn't systematically calculate
```

**MUST use thinking block** to write out each line's calculation explicitly.
This prevents estimation errors, especially when:
- Lines have different emoji counts
- Lines have similar visual length
- There are many lines

## Example with Emojis

Given SessionStart widths: `🐱=2, ✅=2`

**In thinking block, calculate:**

```
Lines:
1. "🐱 CAT initialized"
2. "✅ Trust: high"
3. "Settings saved"

Display widths (non-emoji chars + emoji widths):
1. 16 chars + 🐱(2) = 18
2. 12 chars + ✅(2) = 14
3. 14 chars + 0     = 14

max = 18

Padding:
1. 18 - 18 = 0 spaces
2. 18 - 14 = 4 spaces
3. 18 - 14 = 4 spaces
```

**Then output:**

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
