---
name: box-alignment
description: "MANDATORY: Load BEFORE rendering any box output"
---

# Box Alignment

## Purpose

All right-side `│` characters in the box align vertically.

---

## Constants

```
BORDER_LEFT  = "│ "  (display width: 2)
BORDER_RIGHT = " │"  (display width: 2)
BORDER_WIDTH = 4     (BORDER_LEFT + BORDER_RIGHT)

WIDTH_2_EMOJIS = [☑️, 🔄, 🔳, 🚫, 🚧, 📊, 📦, 🎯, 📋, ⚙️, 🏆, 🧠, 🐱, 🧹, 🤝, ✅, 🔍, 👀, 🔭, ⏳, ⚡, 🔒, ✨, ⚠️, ✦]
WIDTH_1_SYMBOLS = [✓, ✗, →, •, ▸, ▹, ◆, ⚠]
All other characters (ASCII, box-drawing │╭╮╰╯─) = width 1
```

---

## Functions

### display_width(text) → integer

Calculate the terminal display width of a string.

**Definition**:
```
width = 0
for each character c in text:
  if c in WIDTH_2_EMOJIS: width += 2
  else: width += 1
return width
```

**Derivation** (min-case → increment → generalize):
```
Length 1: "A" → 1 (not emoji)
Length 1: "📊" → 2 (is emoji)
Length 2: "Hi" → 1 + 1 = 2
Length 2: "📊A" → 2 + 1 = 3
Length N: sum of individual character widths
```

**Examples**:
```
display_width("Hello") = 5
display_width("📊 Overall") = 2 + 1 + 7 = 10
display_width("☑️ Done") = 2 + 1 + 4 = 7
```

---

### max_content_width(contents[]) → integer

Find the maximum display width among all content items.

**Definition**:
```
return max(display_width(c) for c in contents)
```

**Derivation**:
```
Length 1: ["Hi"] → display_width("Hi") = 2
Length 2: ["Hi", "Bye"] → max(2, 3) = 3
Length N: largest display_width among all items
```

---

### build_line(content, max_width) → string

Construct a single content line with correct padding.

**Definition**:
```
padding = max_width - display_width(content)
return "│ " + content + " "×padding + " │"
```

**Invariant**: All lines built with the same `max_width` have identical display width.

---

### build_border(max_width, is_top) → string

Construct the top or bottom border.

**Definition**:
```
dash_count = max_width + 2
dashes = "─"×dash_count
if is_top: return "╭" + dashes + "╮"
else: return "╰" + dashes + "╯"
```

---

### build_box(contents[]) → string[]

Construct a complete box with aligned borders. Handles nested boxes recursively.

**Definition**:
```
# If any content item is itself a box structure, build it first (recursion)
processed = []
for each item in contents:
  if item is a box structure:
    inner_lines = build_box(item.contents)
    processed.extend(inner_lines)
  else:
    processed.append(item)

# Now build the box around processed contents
max_width = max_content_width(processed)
top = build_border(max_width, is_top=true)
bottom = build_border(max_width, is_top=false)

lines = []
for each content in processed:
  lines.append(build_line(content, max_width))

return [top] + lines + [bottom]
```

**For nested boxes**: Inner boxes are built first (inside-out), then their complete line
strings become content items in the outer box.

---

### build_sibling_boxes(box_structures[], shared_width?) → string[][]

Build multiple inner boxes at the same nesting level with consistent width.

**Critical Rule**: Sibling inner boxes (boxes at the same level) MUST share a common width
so their right borders align with each other.

**Definition**:
```
# Step 1: Collect ALL content from ALL sibling boxes
all_inner_contents = []
for each box in box_structures:
  all_inner_contents.extend(box.contents)

# Step 2: Calculate the GLOBAL max width across all siblings
global_inner_max = max(display_width(c) for c in all_inner_contents)

# Step 3: Build each sibling box using the SAME global_inner_max
result = []
for each box in box_structures:
  # Force this box to use global_inner_max, not its own max
  box_lines = build_box_with_width(box.contents, global_inner_max)
  result.append(box_lines)

return result
```

**Why this matters**: If sibling boxes are built independently, they may have different
widths based on their individual content. This causes their right `│` borders to be in
different columns, breaking visual alignment.

---

## Procedure

### Step 1: Identify all content items

List every string that will appear inside the box.

For nested boxes: identify which items are themselves box structures.

### Step 2: Build inner boxes first (if any)

**For sibling inner boxes (multiple boxes at same nesting level):**

1. Collect ALL content items from ALL sibling boxes
2. Calculate the GLOBAL max_content_width across all siblings
3. Build each sibling box using the SAME global width
4. The returned lines become content items for the outer box

**For single inner box:**
1. Call `build_box(inner_contents)` recursively
2. The returned lines become content items for the outer box

### Step 3: Calculate max_content_width

Call `max_content_width(all_content_items)` including any inner box lines.

### Step 4: Build all lines

For each content item:
  Call `build_line(item, max_width)`

### Step 5: Build borders

```
top = build_border(max_width, is_top=true)
bottom = build_border(max_width, is_top=false)
```

### Step 6: Assemble and output

```
output = [top] + [all content lines] + [bottom]
```

---

## Verification

After rendering, confirm:
- [ ] All right-side `│` characters are in the same column
- [ ] For nested boxes: inner box borders are fully contained within outer box
- [ ] **For sibling inner boxes**: All sibling boxes have IDENTICAL width (their right `│` align with each other)

---

## Example: Simple Box

**Step 1: Content items**
```
contents = ["📊 Overall: 45%", "🏆 10/22 tasks"]
```

**Step 3: Calculate max width**
```
display_width("📊 Overall: 45%") = 2 + 14 = 16
display_width("🏆 10/22 tasks") = 2 + 12 = 14
max_content_width = 16
```

**Step 4: Build lines**
```
build_line("📊 Overall: 45%", 16) = "│ 📊 Overall: 45%  │"  (padding: 0)
build_line("🏆 10/22 tasks", 16) = "│ 🏆 10/22 tasks   │"  (padding: 2)
```

**Step 5: Build borders**
```
dash_count = 16 + 2 = 18
top = "╭──────────────────╮"
bottom = "╰──────────────────╯"
```

**Step 6: Output**
```
╭──────────────────╮
│ 📊 Overall: 45%  │
│ 🏆 10/22 tasks   │
╰──────────────────╯
```

---

## Example: Nested Box with Sibling Alignment

**Step 1: Content items**
```
outer_contents = [
  "📊 Overall: 50%",
  {box: ["☑️ v1.0: Done (1/1)"], header: "📦 v1"},
  {box: ["🔄 v2.0: In Progress (5/10)"], header: "📦 v2"}
]
```

**Step 2: Build sibling inner boxes with SHARED width**

*First: Calculate GLOBAL max across ALL sibling box contents:*
```
v1 contents: ["☑️ v1.0: Done (1/1)"]
  display_width("☑️ v1.0: Done (1/1)") = 2 + 18 = 20

v2 contents: ["🔄 v2.0: In Progress (5/10)"]
  display_width("🔄 v2.0: In Progress (5/10)") = 2 + 25 = 27

GLOBAL_INNER_MAX = max(20, 27) = 27
```

*Then: Build BOTH inner boxes using global_inner_max = 27:*

*Inner box 1 (v1) - using shared width 27:*
```
Inner lines (all width 31 = 27 + 4 borders):
  "╭─ 📦 v1 ──────────────────────╮"
  "│ ☑️ v1.0: Done (1/1)         │"   (padding: 7)
  "╰─────────────────────────────╯"
```

*Inner box 2 (v2) - using same shared width 27:*
```
Inner lines (all width 31 = 27 + 4 borders):
  "╭─ 📦 v2 ──────────────────────╮"
  "│ 🔄 v2.0: In Progress (5/10) │"   (padding: 0)
  "╰─────────────────────────────╯"
```

**Both inner boxes now have identical width (31), so their right borders align!**

**Step 3: Calculate outer max width**
```
Processed contents:
  "📊 Overall: 50%"                     → width: 16
  "╭─ 📦 v1 ──────────────────────╮"    → width: 31
  "│ ☑️ v1.0: Done (1/1)         │"    → width: 31
  "╰─────────────────────────────╯"    → width: 31
  "╭─ 📦 v2 ──────────────────────╮"    → width: 31
  "│ 🔄 v2.0: In Progress (5/10) │"    → width: 31
  "╰─────────────────────────────╯"    → width: 31

max_content_width = 31
```

**Step 4-6: Build and assemble**
```
╭─────────────────────────────────╮
│ 📊 Overall: 50%                 │
│ ╭─ 📦 v1 ──────────────────────╮│
│ │ ☑️ v1.0: Done (1/1)         ││
│ ╰─────────────────────────────╯│
│ ╭─ 📦 v2 ──────────────────────╮│
│ │ 🔄 v2.0: In Progress (5/10) ││
│ ╰─────────────────────────────╯│
╰─────────────────────────────────╯
```

**Key observation**: All inner box right borders (`│`) are in the same column, and
they align with the outer box right border.
