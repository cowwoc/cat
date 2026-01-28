---
user-invocable: false
---

# Work Command: Anti-Patterns Reference

Reference tables for anti-patterns and main agent boundaries.

## Background Task Behavior (M293)

**CRITICAL: Claude does NOT automatically wake up when background tasks complete.**

| Scenario | Correct Approach |
|----------|------------------|
| Background tasks running | Continue other work, OR tell user to check back |
| All tasks backgrounded | Tell user: "Prompt me when ready to continue" |
| trust=high expected | Use `TaskOutput` with `block: true`, OR avoid background |

**Anti-pattern (M293):**
```
❌ "I'll wait for subagents to complete and notify me"
✅ "Subagents running. Prompt me for status update."
```

---

## Main Agent Boundaries

**MANDATORY: Main agent delegates ALL work phases to subagents.**

The main agent is an ORCHESTRATOR. All phases MUST be delegated to subagents:

| Phase | Delegate To | Anti-Pattern |
|-------|-------------|--------------|
| Exploration | Exploration subagent | M088: Main agent reading source files directly |
| Planning | Planning subagent | M091: Main agent making architectural decisions |
| Implementation | Implementation subagent | M063: Main agent editing source code |

**Main agent responsibilities (ONLY these):**
- Read orchestration files (STATE.md, PLAN.md, CHANGELOG.md)
- Run diagnostic commands (git status, build output, test results)
- Edit orchestration files (STATE.md, PLAN.md, CHANGELOG.md)
- Present summaries and ask questions to user
- Invoke skills and spawn subagents
- Aggregate and present subagent findings

**Subagent responsibilities (ALL substantive work):**
- **Exploration subagent:** Read and analyze source code, report findings
- **Planning subagent (two stages):**
  - Stage 1: Produce high-level outlines for three approaches (lightweight)
  - Stage 2: (resumed) Produce detailed spec for selected approach
- **Implementation subagent:** Write/edit source code, tests, fix bugs

**Orchestration Enforcement (A014):**

Before any file read or code analysis, ask: "Should a subagent do this?"

| Action | Main Agent OK? | Why |
|--------|----------------|-----|
| Read STATE.md | ✅ Yes | Orchestration file |
| Read Parser.java | ❌ No | Delegate to exploration subagent |
| Decide which API to use | ❌ No | Delegate to planning subagent |
| Write test code | ❌ No | Delegate to implementation subagent |
| Present approval gate | ✅ Yes | Orchestration action |

**Config settings drive behavior directly:**
- Trust: Controls approval gates and auto-selection thresholds
- Verify: Controls pre-commit verification scope
- Curiosity: Controls issue discovery breadth
- Patience: Controls when discovered issues are addressed

### Pre-Edit Checkpoint

**MANDATORY Pre-Edit Self-Check (M088):**

BEFORE using the Edit tool on ANY source file (.java, .md code docs, etc.), STOP and verify:

1. **Am I the main agent?** (orchestrating a CAT task)
2. **Is this a source/documentation file?** (not STATE.md, PLAN.md, CHANGELOG.md)
3. **Is a subagent already running or could one be spawned?**

If answers are YES/YES/YES → **SPAWN SUBAGENT INSTEAD**

**This applies even for "simple" changes:**
- Variable renaming → subagent
- Comment updates → subagent
- Style fixes → subagent
- Convention updates to style guides → subagent

**Rationale:** "Simple" edits bypass the delegation boundary. If it touches code, delegate it.

---

## Anti-Pattern Index

### Configuration-Driven Behavior Summary

| Config | Setting | Behavior |
|--------|---------|----------|
| trust | high | Skip approval gate, auto-continue, auto-select approach |
| trust | medium | Auto-continue, auto-fix on rejection (3 iterations) |
| trust | low | Wait for user, ask on rejection |
| curiosity | low | "Focus ONLY on assigned task" |
| curiosity | medium | "NOTE obvious issues, report in .completion.json" |
| curiosity | high | "Actively look for issues, report ALL findings" |
| patience | high | Future version backlog |
| patience | medium | Current version backlog |
| patience | low | Resume planner, re-execute |
| verify | none | Skip verification |
| verify | changed | Test changed modules only |
| verify | all | Full project verification |

### M### Series (Mistake Codes)

| Code | Issue | Prevention |
|------|-------|------------|
| M034 | Silent plan changes | Announce BEFORE implementing |
| M035 | No user review | Always pause for review (unless trust:high) |
| M047 | Non-linear merge | Default to --ff-only |
| M063 | Main agent implementing | Delegate to subagent |
| M072 | Approval without commit | Verify commit exists first |
| M076 | Separate STATE.md commit | Include in implementation commit |
| M085 | STATE.md not in commit | Verify before approval |
| M088 | Main agent reading source | Delegate to exploration subagent |
| M089 | Subagent branch in approval | Show task branch |
| M091 | Main agent deciding patterns | Delegate to planning subagent |
| M092 | Missing resolution field | Require for completed status |
| M094 | Exit gate task runs early | Check all non-gate tasks complete |
| M097 | No lock check before offer | Acquire lock first |
| M099 | No token variance check | Compare actual vs estimate |
| M110 | Verify when no source changes | Check for actual changes first |
| M120 | No next steps | Always provide options |
| M125 | Code blocks for output | Output directly without ``` |
| M150 | Invalid status transition | pending→in-progress→completed |
| M151 | Approval with unsquashed | Squash before approval |
| M153 | 90% in-progress at approval | Must be 100% completed |
| M154 | Checkout different branch | Merge INTO current branch |
| M157 | No squash before approval | Run git-squash skill |
| M160 | Summary only, no diff | Show actual diff content |
| M161 | Approval without diff | Always show diff |
| M163 | Task without parent update | Add to parent pending list |
| M170 | Plain git diff | Use render-diff skill |
| M171 | Ad-hoc diff format | Use render-diff skill |
| M172 | PLAN.md for skill usage | Use SKILL.md (authoritative) |
| M173 | Fallback rm on lock release | Just call issue-lock.sh |
| M201 | Wrong diff format | Use render-diff.py |
| M211 | Reformatted diff | Present VERBATIM |
| M231 | Truncated large diff | Show ALL content |
| M239 | Resume existing worktree | Skip, find alternative |
| M245 | Ignore get-available-issues.sh | Accept script results |
| M246 | Manual box characters | Copy-paste from template |
| M248 | Confuse version/branch | Distinguish task version from base branch |
| M251 | No subtask context | Include decomposition context |
| M262 | Ignore ERROR in script output | Fail-fast, report error, do NOT continue |
| M293 | "I'll wait for subagents" | Continue working or prompt user to check back |

### A### Series (Architecture Codes)

| Code | Issue | Prevention |
|------|-------|------------|
| A010 | Pre-approval checklist skip | Complete ALL checks first |
| A011 | STATE.md validation skip | Verify rules before update |
| A014 | Orchestration boundary | Check "should subagent do this?" |
| A015 | SKILL.md vs PLAN.md | SKILL.md for usage, PLAN.md for planning |
| A018 | No hard limit enforcement | Mandatory decomposition at 80% |
