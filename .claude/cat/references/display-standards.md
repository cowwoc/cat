# Display Standards Reference

Standard visual elements for CAT workflows: status displays, progress bars, and visual hierarchy.

## Core Principle: No Markdown Bold in CLI Output {#no-markdown-bold}

**MANDATORY**: Never use `**bold**` markdown syntax in CLI output text.

**Rationale**: Claude Code CLI renders output in a terminal. Terminals do not interpret `**text**` as
bold - users see the raw asterisks. Use plain text for emphasis, or UPPERCASE for headers/labels.

**Instead of:**
```
**Status:** pending
**Progress:** [=====>    ] 50%
```

**Use:**
```
Status: pending
Progress: [=====>    ] 50%
```

For section headers, use box borders or UPPERCASE instead of bold:
```
═══════════════════════════════════════════════════════════════════
TASK DETAILS
═══════════════════════════════════════════════════════════════════
```

## Core Principle: No Vertical Borders {#no-vertical-borders}

**MANDATORY**: Avoid vertical borders (`║`, `│`) in all displays.

**Rationale**: Emoji display width varies across terminals and editors. Vertical borders require
precise padding calculation which is unreliable with emojis. Horizontal-only borders eliminate
this problem entirely.

## Box Display Format {#box-display-format}

Use horizontal borders only. Content flows freely without padding constraints.

### Primary Boxes (Status, Checkpoints, Forks)

```
═══════════════════════════════════════════════════════════════════
🗺️ TITLE TEXT HERE
═══════════════════════════════════════════════════════════════════

Content line here
Another line with 🎯 emoji - no padding needed

═══════════════════════════════════════════════════════════════════
```

### Secondary Boxes (Nested Content)

```
───────────────────────────────────────────────────────────────────
Section Title
───────────────────────────────────────────────────────────────────

Nested content here

───────────────────────────────────────────────────────────────────
```

### Border Characters

| Character | Purpose |
|-----------|---------|
| `═` | Primary horizontal border (double-line) |
| `─` | Secondary horizontal border (single-line) |

**Standard width**: 67 characters for horizontal borders.

### When to Use Each

| Style | Use For |
|-------|---------|
| Double-line (`═══`) | Main containers, status boxes, checkpoints |
| Single-line (`───`) | Section dividers, nested content |

## Status Box Examples {#status-box-examples}

**Task Blocked:**
```
═══════════════════════════════════════════════════════════════════
⏸️ NO EXECUTABLE TASKS AVAILABLE
═══════════════════════════════════════════════════════════════════

Task `task-name` is locked by another session.

Blocked tasks:
- task-a
- task-b

═══════════════════════════════════════════════════════════════════
```

**Checkpoint:**
```
═══════════════════════════════════════════════════════════════════
✅ CHECKPOINT: Task Complete
═══════════════════════════════════════════════════════════════════

Task: task-name
Status: SUCCESS

═══════════════════════════════════════════════════════════════════
```

**Fork in the Road:**
```
═══════════════════════════════════════════════════════════════════
🔀 FORK IN THE ROAD
═══════════════════════════════════════════════════════════════════

[A] 🛡️ Option A - Description here
[B] ⚔️ Option B - Description here

═══════════════════════════════════════════════════════════════════
```

**Adventure Status (cat:status):**
```
═══════════════════════════════════════════════════════════════════
🗺️ YOUR ADVENTURE - Project Name
═══════════════════════════════════════════════════════════════════

📊 Progress: [████████████████░░░░] 78%
🏆 72/92 tasks complete
⚙️ Mode: Interactive

═══════════════════════════════════════════════════════════════════
```

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

Use markdown formatting and emojis for hierarchy instead of box nesting:

```
═══════════════════════════════════════════════════════════════════
🗺️ PROJECT STATUS
═══════════════════════════════════════════════════════════════════

┌─ 📦 v0: Major Version Name ─────────────────────────────────────┐

☑️ v0.1: Minor description (5/5)
☑️ v0.2: Another minor (9/9)
🔄 **v0.3: Current minor** (3/5)
   🔳 pending-task-1
   🔳 pending-task-2
🔳 v0.4: Future minor (0/4)

└─────────────────────────────────────────────────────────────────┘
```

Note: The `┌─` and `└─` create visual grouping without requiring padding alignment.

## Status Symbols {#status-symbols}

| Symbol | Meaning |
|--------|---------|
| ☑️ | Completed |
| 🔄 | In Progress |
| 🔳 | Pending |
| 🚫 | Blocked |
| 🚧 | Gate Waiting |

## Anti-Patterns {#anti-patterns}

**Using vertical borders (WRONG):**
```
╔═══════════════════════════════════════════════════════════════════╗
║  🎯 Title with emoji                                              ║
╚═══════════════════════════════════════════════════════════════════╝
```
This requires padding calculation that breaks with emoji width variations.

**Correct approach:**
```
═══════════════════════════════════════════════════════════════════
🎯 Title with emoji
═══════════════════════════════════════════════════════════════════
```

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
1. Remove all `║` and `│` vertical borders
2. Keep horizontal borders (`═`, `─`)
3. Remove padding calculations
4. Let content flow naturally
