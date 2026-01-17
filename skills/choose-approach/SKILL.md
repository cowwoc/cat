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
- User's `trust` setting is `low` or `medium`

**Auto-select (skip user prompt) when:**
- User's `trust` setting is `high` (high trust, autonomous decisions)
- For `high` trust: auto-select Balanced approach unless task risk is HIGH

**Present choice even with high trust when:**
- Task has HIGH risk level (user should confirm)
- Approaches have significantly different architectural implications

## MANDATORY: Respect User's Trust Setting

**The agent MUST respect the user's `trust` setting which controls when to present choices.**

- `low` trust = Present options frequently, user guides most decisions
- `medium` trust = Present options for meaningful trade-offs only
- `high` trust = Make autonomous decisions, only present for HIGH risk or significant architecture

**When presenting options (low/medium trust or high-risk task):**

If there IS a compelling technical reason to recommend one approach:

1. **Explain the specific technical issue** with concrete evidence
2. **Quantify the impact** where possible (e.g., "adds O(n) scan to every parenthesized expression")
3. **Present the choice to the user** with the recommendation explained
4. **Let the user decide** - they may accept the tradeoff

**Example of proper recommendation handling:**

```
This task has multiple valid approaches:

The Aggressive approach has a specific performance concern:
- Adds isLambdaExpression() lookahead to EVERY parenthesized expression
- In typical Java files, this adds ~950 unnecessary scans per 1000 parens
- Quantified overhead: O(n) per paren where n = tokens until )

The Balanced approach achieves the same correctness without this overhead.

Would you like to:
- Proceed with Aggressive (accept overhead for architectural clarity)
- Use Balanced (targeted fix without overhead)
```

**Anti-pattern:** Auto-selecting an approach when `trust: low` or `trust: medium` without
presenting the choice to the user.

## Workflow

### 1. Analyze Task & Trust Setting

```bash
# Load trust setting (trust level)
TRUST=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

Read PLAN.md and extract:
- Risk level (from Risk Assessment section)
- The three standard approaches: Conservative, Balanced, Aggressive
- Task complexity (estimated tokens, scope)

### 2. Determine if Choice Point Needed

| Risk Level | Trust Setting | Decision |
|------------|---------------|----------|
| LOW/MEDIUM | `high` | Auto-select Balanced, log to STATE.md |
| LOW/MEDIUM | `medium` | Present choice for meaningful trade-offs |
| LOW/MEDIUM | `low` | Present choice (user guides decisions) |
| HIGH | Any | Present choice (user must confirm for high-risk) |

### 3. Auto-Selection or Recommendation

**If auto-selecting (high trust with non-HIGH risk):**

```
✓ Approach: Balanced
  (Auto-selected: high trust setting, routine trade-off)
```

Update PLAN.md "Selected Approach" section and proceed to implementation.

**If presenting choice:**

Present all three approaches without bias. Let the user decide based on
their understanding of the project context.

### 4. Analyze Long-Term Interest

**MANDATORY: Evaluate which approach best serves the project's long-term health.**

Consider these factors for long-term analysis:

| Factor | Question | Favors |
|--------|----------|--------|
| Pattern establishment | Does this approach create a reusable pattern? | Balanced/Aggressive |
| Root cause | Does it address the underlying issue vs symptom? | Balanced/Aggressive |
| Maintainability | Will future developers understand and extend it? | Conservative/Balanced |
| Technical debt | Does it reduce or increase future work? | Balanced |
| Consistency | Does it follow existing codebase patterns? | Conservative/Balanced |
| Over-engineering | Is the scope justified by future benefit? | Conservative/Balanced |

**Determine two recommendations:**

1. **⭐ QUICK WIN** - Best for immediate task completion
   - Prioritizes: Low risk, fast delivery, minimal scope
   - Often: Conservative approach

2. **🏆 LONG-TERM** - Best for project health over time
   - Prioritizes: Maintainability, patterns, root cause fixes
   - Often: Balanced approach (but can be Conservative or Aggressive)

**When they differ:** Explain why. Common scenarios:
- Quick Win = Conservative (fast fix), Long-Term = Balanced (prevents recurrence)
- Quick Win = Balanced (good tradeoff), Long-Term = Balanced (same choice)
- Quick Win = Conservative (safe), Long-Term = Aggressive (architectural improvement needed)

### 5. Present Fork in the Road

Display with wizard-style formatting (see [display-standards.md § Fork in the Road](.claude/cat/references/display-standards.md#fork-in-the-road)).

**CRITICAL: Output directly WITHOUT code blocks (M125).** Markdown `**bold**` renders correctly
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
    {scope from PLAN.md}
    Risk: LOW | Scope: {N} files | ~{N}K tokens

[B] ⚖️ **Balanced**
    {scope from PLAN.md}
    Risk: MEDIUM | Scope: {N} files | ~{N}K tokens

[C] ⚔️ **Aggressive**
    {scope from PLAN.md}
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

Use AskUserQuestion with options: "Conservative", "Balanced", "Aggressive"

### 6. Record Choice and Resume Planning Agent

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

### High Complexity Task (Research Recommended)

Example output (do NOT wrap in ```):

═══════════════════════════════════════════════════════════════════
🔀 **FORK IN THE ROAD**
═══════════════════════════════════════════════════════════════════

**Task:** implement-incremental-parsing
**Risk:** HIGH

**Choose Your Path**
───────────────────────────────────────────────────────────────────

[A] 🏗️ **Full implementation**
    Build complete solution upfront
    Risk: HIGH | Scope: 12 files | ~45K tokens

[B] 📦 **Incremental approach**
    Start simple, expand as needed
    Risk: MEDIUM | Scope: 5 files | ~20K tokens

[C] 🔍 **Research first**
    Analyze existing parser architecture before committing
    Risk: LOW | Scope: 0 files | ~8K tokens

───────────────────────────────────────────────────────────────────
**Analysis**
───────────────────────────────────────────────────────────────────

⭐ **Quick Win:** [C] Research first
   Lowest risk. Gathers information before committing to an approach.

🏆 **Long-Term:** [B] Incremental approach
   Allows validation at each step. Prevents over-engineering by
   expanding scope only when needed.

Different recommendations: Research reduces immediate risk, but
incremental approach is better long-term as it builds understanding
while making progress.

═══════════════════════════════════════════════════════════════════

### Mechanical Refactor (Same Recommendation)

Example output (do NOT wrap in ```):

═══════════════════════════════════════════════════════════════════
🔀 **FORK IN THE ROAD**
═══════════════════════════════════════════════════════════════════

**Task:** rename-parser-methods-for-consistency
**Risk:** LOW

**Choose Your Path**
───────────────────────────────────────────────────────────────────

[A] ⚡ **Direct rename**
    Find-and-replace across codebase
    Risk: LOW | Scope: 8 files | ~6K tokens

[B] 🏗️ **Refactor with deprecation**
    Add new names, deprecate old, migrate gradually
    Risk: LOW | Scope: 12 files | ~15K tokens

[C] 🔍 **Research first**
    Check for dynamic references or reflection usage
    Risk: LOW | Scope: 0 files | ~3K tokens

───────────────────────────────────────────────────────────────────
**Analysis**
───────────────────────────────────────────────────────────────────

⭐ **Quick Win:** [A] Direct rename
   Mechanical change with clear scope. Fast and low risk.

🏆 **Long-Term:** [A] Direct rename
   No external consumers need migration. Deprecation adds complexity
   without benefit for internal-only code.

Same recommendation: Direct rename is both fastest and best for
project health. Deprecation would add unnecessary maintenance burden.

═══════════════════════════════════════════════════════════════════

### Architecture Decision (Different Recommendations)

Example output (do NOT wrap in ```):

═══════════════════════════════════════════════════════════════════
🔀 **FORK IN THE ROAD**
═══════════════════════════════════════════════════════════════════

**Task:** split-parser-into-multiple-classes
**Risk:** MEDIUM

**Choose Your Path**
───────────────────────────────────────────────────────────────────

[A] 🛡️ **Conservative**
    Extract to package-private classes, minimal API changes
    Risk: LOW | Scope: 4 files | ~12K tokens

[B] ⚖️ **Balanced**
    Create internal interface, extract implementations
    Risk: MEDIUM | Scope: 6 files | ~18K tokens

[C] ⚔️ **Aggressive**
    Full public API with plugin architecture
    Risk: HIGH | Scope: 10 files | ~35K tokens

───────────────────────────────────────────────────────────────────
**Analysis**
───────────────────────────────────────────────────────────────────

⭐ **Quick Win:** [A] Conservative
   Achieves immediate goal (smaller files) with minimal risk.
   No API changes required.

🏆 **Long-Term:** [B] Balanced
   Internal interface establishes pattern for future extraction.
   Plugin architecture ([C]) is over-engineering for internal parser.

Different recommendations: Conservative is fastest, but Balanced
prevents repeating this refactor when the next extraction is needed.

═══════════════════════════════════════════════════════════════════

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
