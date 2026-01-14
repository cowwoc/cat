---
name: choose-approach
description: Present approach options at task forks with smart recommendations
---

# Choose Approach Skill

**Purpose**: Present implementation approach options when a task has multiple viable paths,
with intelligent recommendations based on task characteristics and user preferences.

## When This Skill Activates

**Show choice point when ALL conditions are met:**
- PLAN.md has 2+ genuinely different approaches
- Approaches have meaningfully different tradeoffs
- User's stored preferences don't clearly favor one path

**Auto-proceed (skip this skill) when ANY condition is true:**
- Only one viable approach exists
- User's style clearly indicates the path (e.g., "conservative" → safer option)
- Approaches are similar enough that choice doesn't matter
- Low-risk task with obvious solution

## Workflow

### 1. Analyze Task & Preferences

```bash
# Load user preferences
PREFS=$(cat .claude/cat/cat-config.json | jq -r '.adventureMode.preferences')
APPROACH=$(echo "$PREFS" | jq -r '.approach')
```

Read PLAN.md and extract:
- Risk level (from Risk Assessment section)
- Available approaches (from Approach or Alternatives section)
- Task complexity (estimated tokens, scope)
- Whether task crosses module boundaries

### 2. Determine if Choice Point Needed

| Task Characteristic | User Style | Decision |
|---------------------|------------|----------|
| Single approach | Any | Auto-proceed |
| Low risk, simple | Any | Auto-proceed |
| Multiple approaches | Conservative | Recommend safer, offer choice |
| Multiple approaches | Aggressive | Recommend comprehensive, offer choice |
| Multiple approaches | Balanced | Must ask - no clear preference |
| High complexity | Any | Recommend research, offer choice |

### 3. Generate Recommendation

Based on task characteristics:

| Task Pattern | Recommendation | Why |
|--------------|----------------|-----|
| High complexity / architectural | Research first | Understand before committing |
| Mechanical / clear scope | Fast/direct path | Low risk, clear scope |
| Cross-module dependencies | Research or comprehensive | Wider impact |
| Bugfix with known root cause | Direct fix | Clear path |
| Bugfix with unclear cause | Research first | Need investigation |
| User is "conservative" | Safer/incremental | Matches preference |
| User is "aggressive" | Comprehensive | Matches preference |
| Genuine toss-up | No recommendation | User decides |

### 4. Present Fork in the Road

Display with visual formatting:

```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: [task-name]                                                ║
║                                                                   ║
║  [A] [emoji] [Approach Name]  [⭐ RECOMMENDED if applicable]       ║
║      [1-line description]                                         ║
║      [If recommended: Why: reason based on task characteristics]  ║
║                                                                   ║
║  [B] [emoji] [Approach Name]                                      ║
║      [1-line description]                                         ║
║                                                                   ║
║  [C] 🔍 Research first                                            ║
║      Analyze the codebase before committing                       ║
║                                                                   ║
║  [footer based on situation]                                      ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**Footer variations:**
- If recommendation exists: blank (recommendation speaks for itself)
- If toss-up: "Your project style is '[style]' - either fits. Which path calls to you?"
- If no preference stored: "No style preference set. Use /cat:update-preferences to set one."

### 5. Record Choice

Update STATE.md with selected approach:
```yaml
- **Approach Selected:** [approach name]
- **Selection Reason:** [user choice | auto-selected based on preferences]
```

Pass approach to subagent prompt for execution.

## Example Presentations

### High Complexity Task (Recommend Research)

```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: implement-incremental-parsing                              ║
║                                                                   ║
║  [A] 🏗️ Full implementation                                       ║
║      Build complete solution upfront                              ║
║                                                                   ║
║  [B] 📦 Incremental approach                                      ║
║      Start simple, expand as needed                               ║
║                                                                   ║
║  [C] 🔍 Research first  ⭐ RECOMMENDED                             ║
║      Analyze existing parser architecture before committing       ║
║      Why: High complexity task with architectural implications    ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

### Mechanical Refactor (Recommend Fast Path)

```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: rename-parser-methods-for-consistency                      ║
║                                                                   ║
║  [A] ⚡ Direct rename  ⭐ RECOMMENDED                               ║
║      Find-and-replace across codebase                             ║
║      Why: Mechanical change, low risk, clear scope                ║
║                                                                   ║
║  [B] 🏗️ Refactor with deprecation                                 ║
║      Add new names, deprecate old, migrate gradually              ║
║                                                                   ║
║  [C] 🔍 Research first                                            ║
║      Check for dynamic references or reflection usage             ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

### Genuine Toss-up (No Recommendation)

```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: split-parser-into-multiple-classes                         ║
║                                                                   ║
║  [A] 🏗️ Interface-based extraction                                ║
║      Cleaner abstraction, more upfront work                       ║
║      Best for: Long-term maintainability                          ║
║                                                                   ║
║  [B] 📦 Package-private access                                    ║
║      Faster to implement, tighter coupling                        ║
║      Best for: Quick delivery, internal-only use                  ║
║                                                                   ║
║  [C] 🔍 Research first                                            ║
║      Analyze usage patterns before deciding                       ║
║                                                                   ║
║  Your project style is "Balanced" - either A or B fits.           ║
║  Which path calls to you?                                         ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

## Integration with execute-task

This skill is called by `execute-task` after loading the task but before spawning the subagent:

```
execute-task flow:
  1. Load task (STATE.md, PLAN.md)
  2. Check size (decompose if needed)
  3. → choose-approach skill ← (this skill)
  4. Create worktree
  5. Spawn subagent with selected approach
  6. ... rest of flow
```

The selected approach is passed to the subagent prompt to guide implementation.

## Success Criteria

- [ ] Preferences loaded from cat-config.json
- [ ] Task characteristics analyzed (risk, complexity, approaches)
- [ ] Recommendation generated based on task + preferences
- [ ] Visual fork displayed (if choice needed)
- [ ] User selection captured
- [ ] Approach recorded in STATE.md
- [ ] Approach passed to subagent
