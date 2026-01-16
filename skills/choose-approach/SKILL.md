---
name: choose-approach
description: Present approach options at task forks with smart recommendations
---

# Choose Approach Skill

**Purpose**: Present implementation approach options when a task has multiple viable paths,
with intelligent recommendations based on task characteristics and user preferences.

## When This Skill Activates

**Show choice point when ALL conditions are met:**
- PLAN.md has the three standard approaches (Conservative/Balanced/Aggressive)
- User preference is `balanced` (no auto-selection possible)

**Auto-select (skip user prompt) when:**
- User preference is `conservative` → auto-select Conservative approach
- User preference is `aggressive` → auto-select Aggressive approach

**Present choice even with preference when:**
- Approaches have unusually different tradeoffs
- HIGH risk task (user should confirm)

## MANDATORY: Respect User Preferences

**The agent MUST follow the user's approach preference unless there is a compelling technical reason
to deviate.** The preference in `cat-config.json` represents a standing instruction from the user.

**Compelling technical reasons include:**
- The preferred approach is technically infeasible (e.g., API doesn't exist)
- The preferred approach has severe performance implications (quantified, not speculative)
- The preferred approach would break existing functionality
- The preferred approach would create security vulnerabilities

**NOT compelling reasons:**
- Agent judgment that another approach is "cleaner" or "simpler"
- Agent preference for a different coding style
- Speculation about future maintainability without evidence
- Vague concerns like "might be overkill"

**If there IS a compelling technical reason to deviate:**

1. **Explain the specific technical issue** with concrete evidence
2. **Quantify the impact** where possible (e.g., "adds O(n) scan to every parenthesized expression")
3. **Present the choice to the user** with the deviation explained
4. **Let the user decide** - they may accept the tradeoff

**Example of proper deviation handling:**

```
Your config shows `approach: aggressive`. However, the aggressive approach here
has a specific performance concern:

- Adds isLambdaExpression() lookahead to EVERY parenthesized expression
- In typical Java files, this adds ~950 unnecessary scans per 1000 parens
- Quantified overhead: O(n) per paren where n = tokens until )

The balanced approach achieves the same correctness without this overhead.

Would you like to:
- Proceed with Aggressive (accept overhead for architectural clarity)
- Use Balanced (targeted fix without overhead)
```

**Anti-pattern:** "I selected Balanced because Aggressive seemed like overkill for this task."
This overrides user preference based on agent judgment, not technical necessity.

## Workflow

### 1. Analyze Task & Preferences

```bash
# Load approach preference
APPROACH=$(jq -r '.approach // "balanced"' .claude/cat/cat-config.json)
```

Read PLAN.md and extract:
- Risk level (from Risk Assessment section)
- The three standard approaches: Conservative, Balanced, Aggressive
- Task complexity (estimated tokens, scope)

### 2. Determine if Choice Point Needed

| Risk Level | User Preference | Decision |
|------------|-----------------|----------|
| LOW/MEDIUM | `conservative` | Auto-select Conservative, log to STATE.md |
| LOW/MEDIUM | `aggressive` | Auto-select Aggressive, log to STATE.md |
| LOW/MEDIUM | `balanced` | Present choice, no recommendation |
| HIGH | Any | Present choice (user must confirm for high-risk) |

### 3. Auto-Selection or Recommendation

**If auto-selecting (non-HIGH risk with clear preference):**

```
✓ Approach: [Conservative|Aggressive]
  (Auto-selected: matches your "{preference}" style)
```

Update PLAN.md "Selected Approach" section and proceed to implementation.

**If presenting choice:**

Generate recommendation based on task characteristics:

| User Preference | Recommended Option | Note |
|-----------------|-------------------|------|
| `conservative` | Conservative | "Matches your preference" |
| `aggressive` | Aggressive | "Matches your preference" |
| `balanced` | None | User decides |

### 4. Present Fork in the Road

Display with visual formatting (see display-standards.md for box standards):

```
╔═══════════════════════════════════════════════════════════════════╗
║  🔀 FORK IN THE ROAD                                              ║
╠═══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  Task: [task-name]                                                ║
║  Risk: [HIGH - requires confirmation]                             ║
║                                                                   ║
║  [A] 🛡️ Conservative  [⭐ if preference matches]                  ║
║      [scope from PLAN.md]                                         ║
║      Risk: LOW | Tradeoff: [from PLAN.md]                         ║
║                                                                   ║
║  [B] ⚖️ Balanced                                                   ║
║      [scope from PLAN.md]                                         ║
║      Risk: MEDIUM | Tradeoff: [from PLAN.md]                      ║
║                                                                   ║
║  [C] ⚔️ Aggressive  [⭐ if preference matches]                     ║
║      [scope from PLAN.md]                                         ║
║      Risk: HIGH | Tradeoff: [from PLAN.md]                        ║
║                                                                   ║
║  Your style: [preference]                                         ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

Use AskUserQuestion with options: "Conservative", "Balanced", "Aggressive"

### 5. Record Choice and Resume Planning Agent

Update STATE.md with selected approach:
```yaml
- **Approach Selected:** [approach name]
- **Selection Reason:** [user choice | auto-selected based on preferences]
- **Planning Agent ID:** [agent_id from Stage 1]
```

**Resume planning agent for Stage 2 (detailed spec):**

```
Use the Task tool with resume parameter:
- resume: {agent_id from Stage 1}
- prompt: "User selected the [Conservative|Balanced|Aggressive] approach.
   Now produce the DETAILED implementation spec with:
   - Specific files to modify
   - Exact code changes
   - Step-by-step execution plan
   - Verification commands
   The implementation subagent must be able to execute this mechanically
   without making any decisions."
```

The resumed agent has full context from Stage 1 exploration, so it can produce
a comprehensive spec without re-reading the codebase.

**Why resume instead of new agent:**
- Preserves ~10-20K tokens of codebase context from exploration
- No need to re-explain the problem or findings
- Faster and more accurate detailed planning

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
║  [C] 🔍 Research first  ⭐ RECOMMENDED                            ║
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
║  [A] ⚡ Direct rename  ⭐ RECOMMENDED                             ║
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
