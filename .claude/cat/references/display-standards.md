# Display Standards Reference

Standard visual elements for CAT workflows: status displays, progress bars, and visual hierarchy.

## Core Principle: Markdown Rendering Context {#markdown-rendering}

Claude Code CLI renders markdown in the main conversation output. However, markdown rendering
depends on context - some output contexts render markdown properly, others show raw syntax.

**Where markdown renders correctly:**
- Main conversation responses (direct assistant output)
- Multi-line formatted blocks with clear structure
- Text mixed with emojis and unicode box-drawing characters

**Where markdown shows raw syntax (M113/M125):**
- Inside triple-backtick code blocks (``` ... ```)
- Bash tool output
- Some terminal contexts with mixed emoji/unicode content

**Bold Text Rules:**
- `**bold**` works in main conversation output when NOT inside code blocks
- **CRITICAL**: Blank line + 4+ spaces = code block mode = bold breaks
- **SOLUTION**: Use zero-width space (U+200B) lines before deeply-indented content
  - ZWSP lines appear completely blank but aren't treated as "blank" by markdown
  - This allows 6+ space indentation while preserving bold rendering
- When bold might not render, use UPPERCASE instead: `CHECKPOINT` not `**Checkpoint**`

**Zero-Width Space (ZWSP) Spacer Lines:** {#zwsp-spacer}
A line containing only U+200B (zero-width space) is not treated as a "blank line" by CommonMark.
This means content after it can use 4+ space indentation without triggering code block mode.

```
Content line 1
<ZWSP>                   ← line with single U+200B (invisible, 0 width)
      Deeply indented - **bold works**
```

**How to type ZWSP:** Copy from here: `​` (between backticks) or use Unicode input.
The character is invisible but present. Editors may show it as a special marker.

Use ZWSP spacers in box displays where visual separation is needed before indented content.

**Guideline:** Output status displays directly as plain text (not inside code blocks) to ensure
markdown renders correctly. When in doubt about rendering context, use UPPERCASE for emphasis.

## Vertical Borders {#vertical-borders}

Use vertical borders (`│`) on both sides of box content to create complete enclosed boxes.

**Note**: Emoji display width varies across terminals. The `emoji-widths.json` file contains
measured widths for common OS/terminal combinations. When precise alignment is needed, use
these measurements to calculate padding.

## Box Display Format {#box-display-format}

Use single-line borders with rounded corners. Titles are embedded in the top border.

### Title Embedding

Titles go **inside** the top border, centered with dashes on both sides:

```
╭─── 🗺️ Title Text Here ────────────────────────────────╮
```

**Rules:**
- 3 dashes before title, space, title text, space, remaining dashes
- Emojis are allowed in titles
- Title should be centered visually

### Indentation Levels

Use 2-space indentation per nesting level:
- Level 0: Outer box border
- Level 1: Content inside outer box (2 spaces)
- Level 2: Nested box border (2 spaces)
- Level 3: Content inside nested box (4 spaces)
- Level 4: Sub-content (6 spaces)

### Primary Boxes (Status, Checkpoints, Forks)

```
╭─── 🗺️ TITLE TEXT HERE ────────────────────────────────╮
│                                                        │
│  Content line here                                     │
│  Another line with 🎯 emoji                            │
│                                                        │
╰────────────────────────────────────────────────────────╯
```

### Nested Boxes

Nested boxes use 2-space indentation. Size boxes to fit their content.

```
╭─── 🗺️ OUTER TITLE ────────────────────────────────────────────╮
│                                                                │
│  📊 Overall stats line                                         │
│                                                                │
│  ╭─── 📦 Nested Section ─────────────────────────────╮         │
│  │                                                   │         │
│  │  ☑️ Nested content here                           │         │
│  │  🔄 Another nested item                           │         │
│  │                                                   │         │
│  ╰───────────────────────────────────────────────────╯         │
│                                                                │
╰────────────────────────────────────────────────────────────────╯
```

### Section Dividers

For separating sections within a box (no title):

```
│  ──────────────────────────────────────────────────────────  │
│  Section content here                                        │
│  ──────────────────────────────────────────────────────────  │
```

### Border Characters

| Character | Purpose |
|-----------|---------|
| `─` | Horizontal border (single-line) |
| `│` | Vertical border (single-line) |
| `╭` `╮` | Top corners (rounded) - ALL boxes |
| `╰` `╯` | Bottom corners (rounded) - ALL boxes |

**Standard width**: 70 characters total for small boxes, 96 for full-width boxes.

### Border Alignment {#border-alignment}

**CRITICAL**: Top and bottom borders must have identical width.

For a box with total width W:
- **Top**: `╭` + (W-2) dashes + `╮` = W chars
- **Bottom**: `╰` + (W-2) dashes + `╯` = W chars

Since borders contain only dashes, alignment is trivial - use the same number of dashes.

## Status Box Examples {#status-box-examples}

**Task Blocked:**

Output format (do NOT wrap in ```):

╭─── ⏸️ NO EXECUTABLE TASKS AVAILABLE ──────────────────────────────╮
│                                                                    │
│  Task `task-name` is locked by another session.                    │
│                                                                    │
│  **Blocked tasks:**                                                │
│  - task-a                                                          │
│  - task-b                                                          │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯

**Checkpoint:**

Output format (do NOT wrap in ```):

╭─── ✅ CHECKPOINT: Task Complete ──────────────────────────────────╮
│                                                                    │
│  **Quest:** task-name                                              │
│  **Approach:** Selected approach description                       │
│                                                                    │
│  ────────────────────────────────────────────────────────────────  │
│  **Time:** 12 minutes | **Tokens:** 45,000 (22% of context)        │
│  ────────────────────────────────────────────────────────────────  │
│  **Branch:** task-branch-name                                      │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯

**Fork in the Road (Wizard-Style):** {#fork-in-the-road}

The fork-in-the-road display uses a wizard-style format that guides users through the decision.
It separates two types of recommendations that may differ:

- **⭐ Quick Win** - Best for immediate task completion (low risk, fast delivery)
- **🏆 Long-Term** - Best for project health over time (maintainability, patterns, architecture)

**CRITICAL: Output directly WITHOUT code blocks.** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Output format (do NOT wrap in ```):

═══════════════════════════════════════════════════════════════════
🔀 **FORK IN THE ROAD**
═══════════════════════════════════════════════════════════════════

**Task:** {task-name}
**Risk:** {LOW|MEDIUM|HIGH}

**Choose Your Path**
───────────────────────────────────────────────────────────────────

[A] 🛡️ **Conservative**
    {scope description}
    Risk: LOW | Scope: {N} files | ~{N}K tokens

[B] ⚖️ **Balanced**
    {scope description}
    Risk: MEDIUM | Scope: {N} files | ~{N}K tokens

[C] ⚔️ **Aggressive**
    {scope description}
    Risk: HIGH | Scope: {N} files | ~{N}K tokens

───────────────────────────────────────────────────────────────────
**Analysis**
───────────────────────────────────────────────────────────────────

⭐ **Quick Win:** [{letter}] {approach name}
   {1-2 sentence rationale for immediate completion}

🏆 **Long-Term:** [{letter}] {approach name}
   {1-2 sentence rationale for project health over time}

{Note if they differ, explaining why}

═══════════════════════════════════════════════════════════════════

**When Quick Win and Long-Term differ:**

This is common and expected. Quick Win optimizes for immediate task completion with minimal risk.
Long-Term optimizes for project maintainability, establishing good patterns, or addressing root
causes that prevent similar issues.

Example where they differ:
- Quick Win: Conservative (fixes this bug fast)
- Long-Term: Balanced (establishes pattern to prevent similar bugs)

Example where they're the same:
- Both: Balanced (targeted fix that also improves the codebase)

**Adventure Status (cat:status):**

Output format (do NOT wrap in ```):

╭─── 🗺️ YOUR ADVENTURE - Project Name ──────────────────────────────╮
│                                                                    │
│  📊 Progress: [████████████████░░░░] **78%**                       │
│  🏆 **72/92** tasks complete                                       │
│  ⚙️ Mode: Interactive                                              │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯

## Progress Bar Format {#progress-bar-format}

**MANDATORY** for all progress displays.

### Algorithm

1. Bar width: 20 characters inside brackets
2. Filled characters: `█` for filled portion
3. Empty characters: `░` for remaining
4. Format: `[{filled}{empty}] {percent}%`

### Calculation

```
filled_count = floor(percentage / 5)
empty_count = 20 - filled_count
bar = "█" * filled_count + "░" * empty_count
```

### Examples

| Percent | Progress Bar |
|---------|--------------|
| 0% | `[░░░░░░░░░░░░░░░░░░░░] 0%` |
| 25% | `[█████░░░░░░░░░░░░░░░] 25%` |
| 50% | `[██████████░░░░░░░░░░] 50%` |
| 75% | `[███████████████░░░░░] 75%` |
| 100% | `[████████████████████] 100%` |

### Usage Contexts

**Project-level progress:**
```
📊 Progress: [███████████████░░░░░] 75% (15/20 tasks)
```

**Minor version progress:**
```
v1.0: Description [█████░░░░░░░░░░░░░░░] 25% (1/4 tasks)
```

## Visual Hierarchy {#visual-hierarchy}

Use markdown formatting, emojis, and nested boxes for visual hierarchy.

**Indentation levels (2-space increments):**
- Level 0: Outer box border
- Level 1: Content inside outer box (2 spaces after `│`)
- Level 2: Nested box border (2 spaces after `│`)
- Level 3: Content inside nested box (4 spaces after outer `│`)
- Level 4: Sub-content (6 spaces after outer `│`)

Output format (do NOT wrap in ```):

╭─── 🗺️ PROJECT STATUS ─────────────────────────────────────────────╮
│                                                                    │
│  📊 Overall: [████████████████░░░░░░░░░░░░░░░░░░░░░░░] **38%**     │
│  🏆 **35/92** tasks complete                                       │
│                                                                    │
│  ╭─── 📦 v0: Major Version Name ────────────────────────╮          │
│  │                                                      │          │
│  │  ☑️ v0.1: Minor description (5/5)                    │          │
│  │  ☑️ v0.2: Another minor (9/9)                        │          │
│  │                                                      │          │
│  │  🔄 **v0.3: Current minor** (3/5)                    │          │
│  │    🔳 pending-task-1                                 │          │
│  │    🔳 pending-task-2                                 │          │
│  │  🔳 v0.4: Future minor (0/4)                         │          │
│  │                                                      │          │
│  ╰──────────────────────────────────────────────────────╯          │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯

## Status Symbols {#status-symbols}

| Symbol | Meaning |
|--------|---------|
| ☑️ | Completed |
| 🔄 | In Progress |
| 🔳 | Pending |
| 🚫 | Blocked |
| 🚧 | Gate Waiting |

## Anti-Patterns {#anti-patterns}

**Using double-line borders (WRONG):**
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎯 Title with emoji                                              ║
╚═══════════════════════════════════════════════════════════════════╝
```
Use single-line borders with rounded corners (`╭╮╰╯│─`) instead.

**Correct approach:**
```
╭─── 🎯 Title with emoji ───────────────────────────────────────────╮
│                                                                    │
│  Content here                                                      │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯
```

**Trying to align columns with emojis without measurement (WRONG):**
```
☑️ Task A     | Complete
🔄 Task B     | In Progress
```
Emoji widths vary by terminal. Use `emoji-widths.json` for padding or avoid tabular alignment.

**Correct approaches:**
```
☑️ Task A - Complete
🔄 Task B - In Progress
```
Or use measured emoji widths from `emoji-widths.json` for precise padding.

## Migration Notes

When updating existing displays:
1. Replace double-line borders (`═║╔╗╚╝`) with single-line (`─│╭╮╰╯`)
2. Use rounded corners (`╭╮╰╯`) for ALL boxes
3. Embed titles in top border: `╭─── 🎯 Title ───╮`
4. Add vertical borders (`│`) on both sides of content
5. Use 2-space indentation per nesting level
6. Size boxes to fit their content (not full-width)
