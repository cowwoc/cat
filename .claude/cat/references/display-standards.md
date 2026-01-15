# Display Standards Reference

Standard visual elements for CAT workflows: boxes, menus, progress bars, and status displays.

## Box Display Format {#box-display-format}

**MANDATORY** for all status boxes and checkpoints.

### Border Styles

Use **double-line borders** for primary status boxes (checkpoints, blockers, forks):

| Character | Purpose |
|-----------|---------|
| `╔` `╗` `╚` `╝` | Corners |
| `═` | Horizontal border |
| `║` | Vertical border |
| `╠` `╣` | T-junctions (horizontal dividers) |

Use **single-line borders** for nested content or secondary boxes:

| Character | Purpose |
|-----------|---------|
| `┌` `┐` `└` `┘` | Corners |
| `─` | Horizontal border |
| `│` | Vertical border |
| `├` `┤` | T-junctions |

**When to use each:**

| Style | Use For | Example Context |
|-------|---------|-----------------|
| Double-line (`╔═╗`) | Outer frame/main container | Status boxes, menus, checkpoints |
| Single-line (`┌─┐`) | Nested boxes inside double-line | Config sections, grouped values |
| Single-line divider (`─`) | Section dividers within boxes | Separating METRICS from CHANGES |

**Hierarchy pattern:**
```
╔═══════════════════════════════════════════════════════════════════╗
║  OUTER FRAME (double-line)                                        ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Section Title                                                    ║
║  ┌─────────────────────────────────────────────────────────────┐  ║
║  │  Nested content (single-line)                               │  ║
║  │  Value: something                                           │  ║
║  └─────────────────────────────────────────────────────────────┘  ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**Never nest double-line inside double-line** or single-line inside single-line.

### Box Dimensions

**Standard widths by context:**

| Context | Total Width | Internal Content | Use For |
|---------|-------------|------------------|---------|
| Full-width | 69 chars | 65 chars | Status boxes, checkpoints, forks |
| Menu/Config | 61 chars | 57 chars | Settings menus, selection dialogs |

**Full-width box (69 chars):**

| Element | Width |
|---------|-------|
| Total box width | 69 |
| Internal content | 65 (between `║` and padding) |
| Side padding | 2 spaces each side |

**Menu box (61 chars):**

| Element | Width |
|---------|-------|
| Total box width | 61 |
| Internal content | 57 (between `║` and padding) |
| Side padding | 2 spaces each side |

Use menu boxes for focused interactions (config wizards, mode selection).
Use full-width boxes for status displays and workflow checkpoints.

**Wider boxes (when needed):**
For content that requires more width (e.g., shell script alerts with long messages),
boxes can be wider. Calculate width based on longest content line + padding.
Maintain consistent width within each box.

### Emoji Width Handling

**CRITICAL**: Emojis display as 2 characters wide in most terminals.

When calculating padding for lines with emojis:
- Count each emoji as **2 characters**
- Subtract emoji display width from available content space

### Box Template

```
╔═══════════════════════════════════════════════════════════════════╗
║  TITLE TEXT HERE                                                  ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Content line here                                                ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**Character counts:**
- Top/bottom border: 1 `╔` + 67 `═` + 1 `╗` = 69
- Middle divider: 1 `╠` + 67 `═` + 1 `╣` = 69
- Content line: 1 `║` + 2 spaces + 63 content + 2 spaces + 1 `║` = 69
- Empty line: 1 `║` + 67 spaces + 1 `║` = 69

### Padding Calculation

```
CONTENT_WIDTH = 63  # Maximum content characters (excluding side padding)

# For plain text:
padding_needed = CONTENT_WIDTH - len(text)
line = f"║  {text}{' ' * padding_needed}  ║"

# For text with emoji (each emoji = 2 display chars):
emoji_count = count_emojis(text)
display_width = len(text) + emoji_count  # Each emoji adds 1 extra
padding_needed = CONTENT_WIDTH - display_width
line = f"║  {text}{' ' * padding_needed}  ║"
```

### Verification Requirement

**MANDATORY**: Use a display width calculator to verify box alignment before committing changes.

Visual inspection is insufficient because:
- Emojis render at different widths in editors vs terminals
- Variation selectors (U+FE0F) are invisible but affect width
- Copy-paste can introduce hidden characters

**Display width calculation:**
```python
import unicodedata

def display_width(s):
    """Calculate terminal display width of a string."""
    width = 0
    i = 0
    while i < len(s):
        c = s[i]
        # Skip variation selectors (handled by lookahead below)
        if c == '\uFE0F':
            i += 1
            continue
        # Check if next char is VS16 (emoji presentation selector)
        has_vs16 = (i + 1 < len(s) and s[i + 1] == '\uFE0F')
        # Emoji presentation (VS16) or high codepoint emoji = 2 columns
        if has_vs16 or ord(c) >= 0x1F300:
            width += 2
        elif unicodedata.east_asian_width(c) in ('F', 'W'):
            width += 2
        else:
            width += 1
        i += 1
    return width
```

**Verification**: Every line in a box MUST have the same display width (69 for full-width, 61 for menu).

### Common Status Boxes

**Task Blocked:**
```
╔═══════════════════════════════════════════════════════════════════╗
║  ⏸️ NO EXECUTABLE TASKS AVAILABLE                                 ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task `task-name` is locked by another session.                   ║
║                                                                   ║
║  Blocked tasks:                                                   ║
║  - task-a                                                         ║
║  - task-b                                                         ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**Checkpoint:**
```
╔═══════════════════════════════════════════════════════════════════╗
║  ✅ CHECKPOINT: Task Complete                                     ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: task-name                                                  ║
║  Status: SUCCESS                                                  ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```
Note: Header has 1 emoji (✅) = remove 1 space from padding.

**Fork in the Road:**
```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  [A] 🛡️ Option A                                                  ║
║  [B] ⚔️ Option B                                                  ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

### Anti-Patterns

**Variable width lines (WRONG):**
```
║  Short line          ║
║  Much longer line that extends past the border║
```

**Not accounting for emoji width (WRONG):**
```
║  ⏸️ NO EXECUTABLE TASKS                                           ║
```
The emoji displays as 2 chars but only counts as 1 in string length. Remove 1 space:
```
║  ⏸️ NO EXECUTABLE TASKS                                          ║
```

**Mixing border styles within one box (WRONG):**
```
╔═══════════════════════════════════════════════════════════════════╗
║  Title                                                            ║
├───────────────────────────────────────────────────────────────────┤
```
Use single-line divider for internal separators:
```
╔═══════════════════════════════════════════════════════════════════╗
║  Title                                                            ║
║  ─────────────────────────────────────────────────────────────────║
```

## Progress Bar Format {#progress-bar-format}

**MANDATORY** for all progress displays.

### Algorithm

1. Bar width: 20 characters inside brackets
2. Filled characters: `=` for each 5% of progress (e.g., 75% = 15 `=` chars)
3. Arrow head: `>` at the end of filled section (except at 100%)
4. Empty characters: spaces for remaining width
5. Format: `[{filled}{arrow}{empty}] {percent}% ({completed}/{total} {unit})`

### Calculation

```
filled_count = floor(percentage / 5)
arrow = ">" if percentage < 100 else ""
empty_count = 20 - filled_count - len(arrow)
```

### Examples

| Percent | Progress Bar                          |
|---------|---------------------------------------|
| 0%      | `[>                   ] 0% (0/20)`    |
| 10%     | `[==>                 ] 10% (2/20)`   |
| 25%     | `[=====>              ] 25% (5/20)`   |
| 50%     | `[==========>         ] 50% (10/20)`  |
| 75%     | `[===============>    ] 75% (15/20)`  |
| 90%     | `[==================> ] 90% (18/20)`  |
| 100%    | `[====================] 100% (20/20)` |

### Usage Contexts

**Project-level progress** (status command):
```
**Progress:** [===============>    ] 75% (15/20 tasks)
```

**Task-level progress** (execute-task display):
```
**Progress:** [==========>         ] 50%
```

**Minor version progress**:
```
### v1.0: Description [=====>              ] 25% (1/4 tasks)
```

## Step Progress Format {#step-progress-format}

For multi-step workflow execution (distinct from completion progress):

```
[Step N/T] Step description [=====>              ] P% (Xs | ~Ys remaining)
```

Where:
- `N` = current step number
- `T` = total steps
- Visual bar = same algorithm as completion progress (20 chars, based on P%)
- `P%` = percentage through workflow
- `Xs` = elapsed time (e.g., `45s`, `2m`, `1h5m`)
- `~Ys` = estimated remaining (e.g., `~30s`, `~3m`)

### Examples

```
[Step 1/14] Verifying structure    [>                   ] 7% (2s | ~28s remaining)
[Step 7/14] Executing task         [==========>         ] 50% (1m | ~1m remaining)
[Step 14/14] Suggesting next action [====================] 100% (2m15s | done)
```
