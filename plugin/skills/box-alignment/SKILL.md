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

**MANDATORY: Follow these steps in order. Do NOT output lines as you compose them.**

### Step 1: Get Emoji Widths

At session start, emoji widths are provided in the format:
```
Emoji display widths for box alignment: ☑️=2, 🔄=2, 🔳=2, ...
All other characters (ASCII, box-drawing │╭╮╰╯─) have width 1.
```

Use these values directly. No file lookup required.

### Step 2: Collect All Content

Build a list of all content lines BEFORE any output:

```
lines = [
  "Content line 1",
  "Content line 2",
  "Longer content line here"
]
```

### Step 3: Calculate Display Width

For each line, calculate its **display width** (not character count):

```python
def display_width(line, emoji_widths):
    width = 0
    for char in line:
        if char in emoji_widths:
            width += emoji_widths[char]  # e.g., 🐱=2
        else:
            width += 1  # ASCII, box-drawing, and other chars
    return width
```

### Step 4: Determine Box Width

Find the maximum display width across all lines:

```
max_display_width = max(display_width(line) for line in lines)
box_width = max_display_width + 2  # +1 left padding, +1 right padding
```

### Step 5: Pad Each Line

Pad each content line with spaces to reach `max_display_width`:

```
padded_lines = []
for line in lines:
    line_width = display_width(line)
    padding_needed = max_display_width - line_width
    padded_line = line + (" " * padding_needed)
    padded_lines.append(padded_line)
```

### Step 6: Output Complete Box

Only now output the box with consistent widths:

```
╭──────────────────────────╮
│ Content line 1           │
│ Content line 2           │
│ Longer content line here │
╰──────────────────────────╯
```

## Verification Checklist

Before outputting a closed box, verify:

- [ ] All content lines collected (not outputting incrementally)
- [ ] Display width calculated for each line (using emoji widths from session context)
- [ ] All lines padded to same display width
- [ ] Top border width matches content display width + 2 (for side borders)
- [ ] Bottom border width matches top border

## Common Mistakes

### Using len() Instead of Display Width for Emojis

```
# WRONG - len("🐱") = 1 but display width = 2
│ 🐱 Cat    │
│ Dog      │   <- misaligned because emoji width wasn't accounted for
```

### Outputting Lines Incrementally

```
# WRONG - outputs as you go, can't calculate max width
│ Short          │
│ Much longer line here│   <- misaligned!
```

### Forgetting Padding

```
# WRONG - no padding applied
│ Short│
│ Much longer line here│
```

## Example with Emojis

Given emoji widths: `🐱=2, ✅=2`

Content:
```
lines = [
  "🐱 CAT initialized",
  "✅ Trust: high",
  "Settings saved"
]
```

Display width calculation:
- "🐱 CAT initialized" = 2 + 1 + 3 + 1 + 11 = 18
- "✅ Trust: high" = 2 + 1 + 6 + 1 + 4 = 14
- "Settings saved" = 14

max_display_width = 18

Padding:
- "🐱 CAT initialized" + 0 spaces
- "✅ Trust: high" + 4 spaces
- "Settings saved" + 4 spaces

Output:
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
