# Display Standards Reference

Standard visual elements for CAT workflows: status displays, progress bars, and visual hierarchy.

## Core Principle: Markdown Rendering Context {#markdown-rendering}

Claude Code CLI renders markdown in the main conversation output. However, markdown rendering
depends on context - some output contexts render markdown properly, others show raw syntax.

**Where markdown renders correctly:**
- Main conversation responses (direct assistant output)
- Multi-line formatted blocks with clear structure

**Where markdown may show raw syntax:**
- Progress indicators and step counters
- Single-line status updates
- Output mixed with special characters or emojis

**Guideline:** For status displays, progress bars, and structured output, prefer plain text
or UPPERCASE for emphasis rather than relying on markdown bold:

```
═══════════════════════════════════════════════════════════════════
TASK DETAILS
═══════════════════════════════════════════════════════════════════

Status: pending
Progress: [=====>    ] 50%
```

**Why this works:** Box borders and UPPERCASE provide visual hierarchy without depending on
markdown rendering. This ensures consistent display across all output contexts.

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

**Fork in the Road (Wizard-Style):** {#fork-in-the-road}

The fork-in-the-road display uses a wizard-style format that guides users through the decision.
It separates two types of recommendations that may differ:

- **⭐ QUICK WIN** - Best for immediate task completion (low risk, fast delivery)
- **🏆 LONG-TERM** - Best for project health over time (maintainability, patterns, architecture)

```
═══════════════════════════════════════════════════════════════════
🔀 FORK IN THE ROAD
═══════════════════════════════════════════════════════════════════

Task: {task-name}
Risk: {LOW|MEDIUM|HIGH}

CHOOSE YOUR PATH
───────────────────────────────────────────────────────────────────

[A] 🛡️ Conservative
    {scope description}
    Risk: LOW | Scope: {N} files | ~{N}K tokens

[B] ⚖️ Balanced
    {scope description}
    Risk: MEDIUM | Scope: {N} files | ~{N}K tokens

[C] ⚔️ Aggressive
    {scope description}
    Risk: HIGH | Scope: {N} files | ~{N}K tokens

───────────────────────────────────────────────────────────────────
ANALYSIS
───────────────────────────────────────────────────────────────────

⭐ QUICK WIN: [{letter}] {approach name}
   {1-2 sentence rationale for immediate completion}

🏆 LONG-TERM: [{letter}] {approach name}
   {1-2 sentence rationale for project health over time}

{Note if they differ, explaining why}

═══════════════════════════════════════════════════════════════════
```

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
