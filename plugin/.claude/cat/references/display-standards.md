# Display Standards Reference

Standard visual elements for CAT workflows: status displays, progress bars, and visual hierarchy.

## Design Principles

1. **Open borders with emojis** - For single-column content (status displays, checkpoints, messages)
2. **Closed borders without emojis** - For multi-column tables (token reports, comparison tables)
3. **No empty line before closing** - Open border format ends directly with `╰─`

## Display Formats {#display-formats}

### Open-Border Format (single column, emojis OK)

For status displays, checkpoints, and informational boxes. Uses left-side borders only, eliminating
the need for padding calculation. Emojis are allowed since no alignment calculation is needed.

```
╭─ 📊 Title
│
│  Content with emojis is fine here
│  No right border means no padding needed
╰─
```

**Nested open-border:**
```
╭─
│ Outer content
│
│ ╭─ Nested section
│ │  Nested content
│ ╰─
╰─
```

### Closed-Border Tables (multi-column, no emojis in cells)

For tabular data like token reports. Uses full borders with fixed column widths.
Emojis should NOT appear in table cells - use ASCII indicators instead for reliable alignment.
Warning emojis can be placed OUTSIDE the table on the right side.

```
╭─────────────────┬──────────────────────────────┬────────┬──────────────┬──────────╮
│ Type            │ Description                  │ Tokens │ Context      │ Duration │
├─────────────────┼──────────────────────────────┼────────┼──────────────┼──────────┤
│ Explore         │ Explore codebase             │ 68.4k  │ 34%          │ 1m 7s    │
│ general-purpose │ Implement fix                │ 45.0k  │ 45% [HIGH]   │ 43s      │
╰─────────────────┴──────────────────────────────┴────────┴──────────────┴──────────╯
```

**Table rules:**
- Use fixed column widths defined by each skill
- Use ASCII indicators (`[HIGH]`, `[EXCEEDED]`, etc.) instead of emojis in cells
- Place warning emojis OUTSIDE the table if needed (after the closing `│`)
- Truncate content with `...` if it exceeds column width

**Truncation:** Keep first N-3 characters, append `...`
- Example: "very-long-task-na..." for a 20-character limit

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

## Border Characters {#border-characters}

| Character | Purpose |
|-----------|---------|
| `─` | Horizontal border (single-line) |
| `│` | Vertical border (single-line) |
| `╭` `╮` | Top corners (rounded) |
| `╰` `╯` | Bottom corners (rounded) |
| `├` `┤` | Internal divider connectors (left/right tee) |
| `┬` `┴` | Table column separators (top/bottom tee) |
| `┼` | Table intersection |

## Progress Bar Format {#progress-bar-format}

**MANDATORY** for all progress displays.

### Algorithm

1. Bar width: 20-25 characters inside brackets
2. Filled characters: `█` for filled portion
3. Empty characters: `░` for remaining
4. Format: `[{filled}{empty}] {percent}%`

### Calculation

```
filled_count = floor(percentage / 4)  # for 25-char bar
empty_count = 25 - filled_count
bar = "█" * filled_count + "░" * empty_count
```

### Examples

| Percent | Progress Bar |
|---------|--------------|
| 0% | `[░░░░░░░░░░░░░░░░░░░░░░░░░] 0%` |
| 25% | `[██████░░░░░░░░░░░░░░░░░░░] 25%` |
| 50% | `[████████████░░░░░░░░░░░░░] 50%` |
| 75% | `[██████████████████░░░░░░░] 75%` |
| 100% | `[█████████████████████████] 100%` |

## Status Symbols {#status-symbols}

**For open-border displays (emojis allowed):**

| Symbol | Meaning |
|--------|---------|
| ☑️ | Completed |
| 🔄 | In Progress |
| 🔳 | Pending |
| 🚫 | Blocked |
| 🚧 | Gate Waiting |

**For table columns (ASCII indicators):**

| Indicator | Meaning |
|-----------|---------|
| `[HIGH]` | Warning threshold reached |
| `[EXCEEDED]` | Critical threshold reached |
| `[REJECTED]` | Review rejected |
| `[APPLIED]` | Action applied |

## Status Box Examples {#status-box-examples}

**Task Blocked (open-border):**

```
╭─ ⏸️ NO EXECUTABLE TASKS AVAILABLE
│
│  Task `task-name` is locked by another session.
│
│  Blocked tasks:
│  - task-a
│  - task-b
╰─
```

**Checkpoint (open-border):**

```
╭─ ✅ CHECKPOINT: Task Complete
│
│  Task: task-name
│
│  Time: 12 minutes | Tokens: 45,000 (22% of context)
│  Branch: task-branch-name
╰─
```

**Status Display (open-border with nested sections):**

```
╭─
│ 📊 Overall: [████████░░░░░░░░░░░░░░░░░] 38%
│ 🏆 35/92 tasks complete
│
│ ╭─ 📦 v0: Major Version Name
│ │
│ │  ☑️ v0.1: Minor description (5/5)
│ │  ☑️ v0.2: Another minor (9/9)
│ │
│ │  🔄 v0.3: Current minor (3/5)
│ │    🔳 pending-task-1
│ │    🔳 pending-task-2
│ ╰─
│
│ 🎯 Active: v0.3
╰─
```

## Fork in the Road (Wizard-Style) {#fork-in-the-road}

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

## Anti-Patterns {#anti-patterns}

**Using double-line borders (WRONG):**
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎯 Title with emoji                                              ║
╚═══════════════════════════════════════════════════════════════════╝
```
Use single-line borders with rounded corners (`╭╮╰╯│─`) instead.

**Putting emojis in table columns (WRONG):**
```
│ ⚠️ Warning  │ Status here │
│ ✅ Success  │ Status here │
```
Emoji widths vary by terminal. Use ASCII indicators in table cells, emojis outside.

**Correct approach for tables:**
```
│ Status       │ [HIGH]    │ ⚠️
│ Status       │ [OK]      │
```
Warning emoji outside the table on the right.

## Migration Notes

When updating existing displays:
1. Replace enclosed boxes with open-border format where possible
2. Use ASCII indicators (`[HIGH]`, `[EXCEEDED]`) in table columns instead of emojis
3. Place warning emojis outside tables on the right side
4. Use fixed column widths for tables
5. Truncate long content with `...`
