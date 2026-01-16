---
name: cat:status
description: Show hierarchy status with visual tree
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---

<objective>

Display the complete CAT hierarchy status with a visual tree showing all major versions, minor versions,
and tasks with their current status.

Provides situational awareness for project progress.

</objective>

<process>

<step name="verify">

**Verify planning structure exists:**

```bash
[ ! -d .claude/cat ] && echo "No planning structure found. Run /cat:new-project to start." && exit 1
```

</step>

<step name="load">

**Load project context:**

- Read `.claude/cat/PROJECT.md` for project name and overview
- Read `.claude/cat/ROADMAP.md` for version structure
- Read `.claude/cat/cat-config.json` for configuration and user preferences:
  - `mode` (yolo/interactive)
  - `adventureMode.preferences.approach` (conservative/balanced/aggressive)
  - `adventureMode.preferences.stakeholderReview` (always/high-risk-only/never)
  - `adventureMode.preferences.refactoring` (avoid/opportunistic/eager)

</step>

<step name="scan">

**Scan all STATE.md files:**

```bash
# Find all major versions
ls -1d .claude/cat/v[0-9]* 2>/dev/null | sort -V

# Find all minor versions under each major
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | sort -V

# Find all tasks under each minor
find .claude/cat/v*/v*.*/task -maxdepth 1 -type d 2>/dev/null | sort
```

For each STATE.md found, extract:
- Status (pending, in-progress, completed, blocked)
- Progress percentage
- Dependencies

</step>

<step name="calculate">

**Calculate overall progress:**

1. Count total tasks across all major/minor versions
2. Count completed tasks
3. Calculate percentage: (completed / total) * 100
4. Identify current position (first in-progress or pending task)

</step>

<step name="render">

**Render adventure-style visual tree:**

**Progress Bar Generation (MANDATORY):**

Use `scripts/lib/progress.sh` library:

```bash
source "$(dirname "$0")/../scripts/lib/progress.sh"
# Generate bar for percentage (0-100)
_progress_bar 75  # Returns: [██████████████████░░] with gradient color
```

Features: 24-bit gradient (red→yellow→green), fractional blocks for precision, respects NO_COLOR.

```
╔═══════════════════════════════════════════════════════════════════╗
║  🗺️  YOUR ADVENTURE - [Project Name]                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Progress: [██████████████████████████████████████░░░░░░░░░░░░░░] ║
║            75% complete (15/20 tasks)                             ║
║                                                                   ║
║  Style: Balanced │ Mode: Interactive                              ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝

┌─ v1: [Name from ROADMAP] ───────────────────────────────────────┐
│                                                                  │
│  v1.0: [Description]                                             │
│    ✓ parse-tokens                                                │
│    → build-ast ← YOU ARE HERE                                    │
│    ○ validate-ast                                                │
│                                                                  │
│  v1.1: [Description]                                             │
│    ○ generate-ir                                                 │
│    ○ optimize-ir (depends: generate-ir)                          │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌─ v2: [Name from ROADMAP] ───────────────────────────────────────┐
│                                                                  │
│  v2.0: [Description]                                             │
│    ○ emit-code                                                   │
│    ○ format-output                                               │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

───────────────────────────────────────────────────────────────────
Current Quest: v1.0, Task: build-ast
Next: validate-ast (after build-ast completes)
───────────────────────────────────────────────────────────────────
```

**Status symbols (MANDATORY for every task line):**
- `✓` - completed
- `→` with `← YOU ARE HERE` - in-progress (current task)
- `○` - pending
- `🚫` - blocked (dependencies not met)

**CRITICAL**: Do NOT use list dash prefix with symbols. Use `✓ task` not `- ✓ task`.
The dash triggers markdown list rendering which strips symbols in CLI output.

**Color hints (if terminal supports):**
- Green for completed
- Yellow for in-progress
- Gray for pending
- Red for blocked

</step>

<step name="blockers">

**Identify blocked tasks:**

List any tasks that are blocked:
- Show which dependencies are incomplete
- Calculate when they could become unblocked

```
BLOCKED:
🚫 v1.1/optimize-ir - waiting on: generate-ir
🚫 v2.0/emit-code - waiting on: v1 completion
```

</step>

<step name="next">

**Suggest next action (adventure style):**

Based on current state, suggest the most appropriate next command:

| State | Suggestion |
|-------|------------|
| Has executable task | `/cat:execute-task` |
| All tasks complete for minor | `/cat:add-task` or `/cat:add-minor-version` |
| All minors complete for major | `/cat:add-major-version` |
| All complete | "Quest complete!" |

```
╔═══════════════════════════════════════════════════════════════════╗
║  🎯 NEXT STEPS                                                    ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  [A] Continue quest                                               ║
║      /cat:execute-task 1.0-build-ast                              ║
║                                                                   ║
║  [B] Add new task                                                 ║
║      /cat:add-task 1.0                                            ║
║                                                                   ║
║  [C] Update preferences                                           ║
║      /cat:update-preferences                                      ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

</step>

</process>

<output_format>

The status output should be:
1. Compact but informative
2. Easy to scan visually
3. Show progress at all levels
4. Highlight current position
5. Suggest next action

</output_format>

<success_criteria>

- [ ] All major versions displayed
- [ ] All minor versions under each major displayed
- [ ] All tasks with correct status symbols
- [ ] Progress bar accurate
- [ ] Current position identified
- [ ] Next action suggested
- [ ] Blocked tasks explained

</success_criteria>
