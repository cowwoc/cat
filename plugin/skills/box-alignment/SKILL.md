# Box Alignment Skill

## Purpose

Provide explicit procedures for rendering properly-aligned box output. This skill ensures right-side
borders align vertically by calculating content width and applying padding before output.

## When to Use

Reference this skill when rendering any box output with closed borders (right-side `│`).

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

Open borders have no right-side border, so varying content lengths don't cause misalignment.

### Closed Border (Alignment Required)

```
╭────────────────────────────╮
│ Content line 1             │
│ Content line 2             │
│ Longer content line here   │
╰────────────────────────────╯
```

Closed borders require all lines to have equal width so right-side `│` characters align.

## Procedure for Closed Borders

**MANDATORY: Follow these steps in order. Do NOT output lines as you compose them.**

### Step 1: Collect All Content

Build a list of all content lines BEFORE any output. Do not output anything yet.

```
lines = [
  "Content line 1",
  "Content line 2",
  "Longer content line here"
]
```

### Step 2: Calculate Maximum Width

Find the longest line's character count:

```
max_width = max(len(line) for line in lines)
# Example: "Longer content line here" = 24 characters
```

### Step 3: Determine Box Width

Add padding for visual spacing (typically 1 space on each side):

```
box_width = max_width + 2  # +1 left padding, +1 right padding
# Example: 24 + 2 = 26
```

### Step 4: Pad Each Line

For each content line, pad with spaces to reach `max_width`:

```
padded_lines = []
for line in lines:
    padding_needed = max_width - len(line)
    padded_line = line + (" " * padding_needed)
    padded_lines.append(padded_line)
```

Result:
```
"Content line 1          "  (24 chars)
"Content line 2          "  (24 chars)
"Longer content line here"  (24 chars)
```

### Step 5: Output Complete Box

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
- [ ] Maximum width calculated
- [ ] All lines padded to same length
- [ ] Top border width matches content width + 2 (for side borders)
- [ ] Bottom border width matches top border

## Common Mistakes

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

### Inconsistent Border Width

```
# WRONG - top/bottom don't match content
╭────────╮
│ Content line here │
╰────────╯
```

## Special Cases

### Content with Emojis

Emojis typically display as 2 characters wide but count as 1 in string length.
For emoji-heavy content, use display width calculation:

- Most emojis: display width = 2
- ASCII characters: display width = 1
- Box-drawing characters (│╭╮╰╯─): display width = 1

When content contains emojis, calculate `display_width` instead of `len()`:

```
display_width = sum(2 if is_emoji(c) else 1 for c in line)
```

### Nested Boxes

For boxes inside boxes, the inner box content follows the same procedure.
Calculate inner box dimensions first, then treat the rendered inner box as content for the outer box.

## Recommendation

**Prefer open-border format** when possible - it avoids alignment complexity entirely.
Use closed borders only when visual containment is specifically required.
