# Context Management

## Quality Degradation

Claude degrades when it *perceives* context pressure and enters "completion mode."

| Context Usage | Quality | Claude's State |
|---------------|---------|----------------|
| 0-30% | PEAK | Thorough, comprehensive |
| 30-50% | GOOD | Confident, solid work |
| 50-70% | DEGRADING | Efficiency mode begins |
| 70%+ | POOR | Rushed, minimal |

**The 40-50% inflection point:** Claude sees context mounting and thinks "I'd better conserve now."
Result: "I'll complete the remaining tasks more concisely" = quality crash.

**The rule:** Stop BEFORE quality degrades, not at context limit.

## Context Target

**Tasks should complete within ~50% of context usage.**

Why 50% not 80%?
- No context anxiety possible
- Quality maintained start to finish
- Room for unexpected complexity
- If you target 80%, you've already spent 40% in degradation mode

## Task Sizing

**Each minor version: 2-4 tasks maximum. Stay under 50% context per task.**

| Task Complexity | Context/Task | Guideline |
|-----------------|--------------|-----------|
| Simple (CRUD, config) | ~10-15% | Can batch 3-4 per session |
| Medium (business logic) | ~20-30% | 2-3 per session |
| Complex (algorithms, integrations) | ~30-40% | 1-2 per session |
| Very complex (migrations, refactors) | ~40-50% | 1 per session, consider decomposition |

**When in doubt: Smaller tasks.** Better to have more tasks than degraded quality.

## TDD Context Usage

**TDD tasks are fundamentally heavier.**

TDD requires 2-3 execution cycles (RED → GREEN → REFACTOR), each with file reads, test runs, and
potential debugging.

| TDD Complexity | Context Usage |
|----------------|---------------|
| Simple utility function | ~25-30% |
| Business logic with edge cases | ~35-40% |
| Complex algorithm | ~40-50% |

**One feature per TDD task.** If features are trivial enough to batch, skip TDD.

## Decomposition Signals

**Always decompose when:**
- Task estimated >40% context
- Multiple subsystems (DB + API + UI)
- Any task with >5 file modifications
- Discovery + implementation together

**Consider decomposing when:**
- Estimated >5 files modified total
- Complex domains (auth, payments)
- Uncertainty about approach
- Natural semantic boundaries

## Token Tracking

### Session File Location
```
/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl
```

### Metrics

| Metric | Value | Description |
|--------|-------|-------------|
| Context limit | 200,000 | Default max tokens |
| Target usage | 40% | Soft target (80K tokens) |
| Compaction flag | Any | Signals task may need decomposition |

### Reading Token Usage

Sum `input_tokens + output_tokens` from all messages in the session file.

Count entries with `type: "summary"` - indicates conversation was compacted.

### Decomposition Triggers

1. **Pre-execution**: Estimate suggests task exceeds 40% threshold
2. **During execution**: Compaction event detected
3. **Post-execution**: Token report shows high usage

## Subagent Context

Subagents get **fresh context** (200K tokens, 0% used) - peak performance for autonomous work.

**Evidence**: Quality degrades in main context due to:
- Accumulated context reduces attention to new information
- More likely to miss protocol steps, user messages, TDD requirements
- Errors compound as context fills

**Rule**: Spawn subagents for autonomous task execution. Fresh context = peak quality.

## Token Report Format

```markdown
## Token Report: [task-name]

- **Total Tokens:** 85,234
- **Context Usage:** 42.6%
- **Compaction Events:** 0
- **Recommendation:** Within limits
```

## Fixed Constants

Context limits are fixed architectural values, not user-configurable. See `agent-architecture.md` § Context Limit Constants.

## Summary

| Principle | Target |
|-----------|--------|
| Task context usage | <50% |
| Ideal task size | 2-3 files, clear scope |
| TDD tasks | Single feature per task |
| Decomposition trigger | >40% estimated or compaction |
| Subagent usage | Fresh context for autonomous work |

**The principle:** Aggressive atomicity. Smaller tasks, consistent quality.

**The rule:** If in doubt, decompose. Quality over consolidation. Always.
