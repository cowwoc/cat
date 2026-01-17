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
- When bold might not render, use UPPERCASE instead: `CHECKPOINT` not `**Checkpoint**`
- For emphasis in headers, prefer: emojis + UPPERCASE over markdown bold

**Guideline:** Output status displays directly as plain text (not inside code blocks) to ensure
markdown renders correctly. When in doubt about rendering context, use UPPERCASE for emphasis.

## Core Principle: No Vertical Borders {#no-vertical-borders}

**MANDATORY**: Avoid vertical borders (`║`, `│`) in content areas.

**Rationale**: Emoji display width varies across terminals and editors. Vertical borders require
precise padding calculation which is unreliable with emojis. Content areas use indentation only.

**Exception**: The `│` character may be used as a separator between metrics on a single line
when connected to horizontal borders with `┬` and `┴` characters.

## Box Display Format {#box-display-format}

Use single-line borders with rounded corners. Emojis go OUTSIDE the box (before the title line).

### Primary Boxes (Status, Checkpoints, Forks)

```
🗺️ TITLE TEXT HERE
╭──────────────────────────────────────────────────────────────────╮
   Content line here
   Another line with 🎯 emoji - no padding needed
╰──────────────────────────────────────────────────────────────────╯
```

### Nested Boxes

Use rounded corners for nested boxes too. Indent nested boxes by 3 spaces.

```
🗺️ OUTER TITLE
╭──────────────────────────────────────────────────────────────────╮

   📦 NESTED TITLE
   ╭───────────────────────────────────────────────────────────╮
      Nested content here
   ╰───────────────────────────────────────────────────────────╯

╰──────────────────────────────────────────────────────────────────╯
```

### Section Dividers

For separating sections within a box (no title):

```
   ──────────────────────────────────────────────────────────────
   Section content here
   ──────────────────────────────────────────────────────────────
```

### Border Characters

| Character | Purpose |
|-----------|---------|
| `─` | Horizontal border (single-line) |
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

⏸️ **NO EXECUTABLE TASKS AVAILABLE**
╭──────────────────────────────────────────────────────────────────────────────────────────────╮
   Task `task-name` is locked by another session.

   **Blocked tasks:**
   - task-a
   - task-b
╰──────────────────────────────────────────────────────────────────────────────────────────────╯

**Checkpoint:**

Output format (do NOT wrap in ```):

✅ **CHECKPOINT: Task Complete**
╭──────────────────────────────────────────────────────────────────────────────────────────────╮
   **Quest:** task-name
   **Approach:** Selected approach description

   ────────────────────────────────────────────────────────────────────────────────────────────
   **Time:** 12 minutes | **Tokens:** 45,000 (22% of context)
   ────────────────────────────────────────────────────────────────────────────────────────────
   **Branch:** task-branch-name
╰──────────────────────────────────────────────────────────────────────────────────────────────╯

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

🗺️ YOUR ADVENTURE - Project Name
╭──────────────────────────────────────────────────────────────────────────────────────────────╮
   📊 Progress: [████████████████░░░░] **78%**
   🏆 **72/92** tasks complete
   ⚙️ Mode: Interactive
╰──────────────────────────────────────────────────────────────────────────────────────────────╯

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

Use markdown formatting and emojis for hierarchy instead of box nesting.

Output format (do NOT wrap in ```):

🗺️ PROJECT STATUS
╭──────────────────────────────────────────────────────────────────────────────────────────────╮

   📦 v0: Major Version Name
   ╭───────────────────────────────────────────────────────────────────────────────────────────╮
      ☑️ v0.1: Minor description (5/5)
      ☑️ v0.2: Another minor (9/9)
      🔄 **v0.3: Current minor** (3/5)
         🔳 pending-task-1
         🔳 pending-task-2
      🔳 v0.4: Future minor (0/4)
   ╰───────────────────────────────────────────────────────────────────────────────────────────╯

╰──────────────────────────────────────────────────────────────────────────────────────────────╯

Note: Nested boxes use rounded corners (`╭╮╰╯`) and are indented by 3 spaces.

## Status Symbols {#status-symbols}

| Symbol | Meaning |
|--------|---------|
| ☑️ | Completed |
| 🔄 | In Progress |
| 🔳 | Pending |
| 🚫 | Blocked |
| 🚧 | Gate Waiting |

## Anti-Patterns {#anti-patterns}

**Using vertical borders in content areas (WRONG):**
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎯 Title with emoji                                              ║
╚═══════════════════════════════════════════════════════════════════╝
```
This requires padding calculation that breaks with emoji width variations.

**Putting emojis on border lines (WRONG):**
```
╭─ 🎯 Title with emoji ────────────────────────────────────────────╮
   Content here
╰──────────────────────────────────────────────────────────────────╯
```
Emoji width affects dash count needed, making alignment fragile.

**Correct approach:**
```
🎯 Title with emoji
╭──────────────────────────────────────────────────────────────────╮
   Content without vertical borders
╰──────────────────────────────────────────────────────────────────╯
```
Emoji is outside the box - borders are pure dashes.

**Trying to align columns with emojis (WRONG):**
```
☑️ Task A     | Complete
🔄 Task B     | In Progress
```
Emoji widths vary, so columns won't align.

**Correct approach:**
```
☑️ Task A - Complete
🔄 Task B - In Progress
```

## Migration Notes

When updating existing displays:
1. Replace double-line borders (`═`) with single-line (`─`)
2. Use rounded corners (`╭╮╰╯`) for ALL boxes (outer and nested)
3. Move emojis/titles INSIDE the box, not on border lines
4. Remove vertical borders from content areas
5. Indent content with spaces instead of padding
6. Borders should be pure dashes (no embedded text)
