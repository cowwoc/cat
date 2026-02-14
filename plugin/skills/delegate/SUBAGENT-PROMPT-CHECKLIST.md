<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Subagent Prompt Checklist (A013)

**CRITICAL: Cross-reference recent learnings before delegating.**

Every subagent prompt MUST include these items based on past mistakes:

## STATE.md Requirements

```
STATE.md UPDATE (required in SAME commit as implementation):
- Path: .claude/cat/issues/v{major}/v{major}.{minor}/{issue-name}/STATE.md
- Set: Status: completed
- Set: Progress: 100%
- Set: Resolution: implemented (MANDATORY - not optional)
- Set: Completed: {YYYY-MM-DD HH:MM}
- Set: Tokens Used: {tokensUsed from .completion.json}
- Include STATE.md in git add before commit
```

## Trust Setting (for PLANNING subagents)

```bash
TRUST_PREF=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

| Value | Include in Planning Prompt |
|-------|---------------------------|
| `short` | "Present multiple options for the user to choose from." |
| `medium` | "Present options for meaningful trade-offs. Proceed with balanced for routine." |
| `long` | "Make autonomous decisions. Only present options for significant choices." |

## Curiosity Setting (for IMPLEMENTATION subagents)

```bash
CURIOSITY_PREF=$(jq -r '.curiosity // "low"' .claude/cat/cat-config.json)
```

| Value | Include in Implementation Prompt |
|-------|----------------------------------|
| `low` | "Focus ONLY on the assigned task. Report only task-related issues." |
| `medium` | "NOTE obvious issues in files you touch. Report in .completion.json." |
| `high` | "Actively look for code quality issues. Report ALL findings." |

## Issues Return Format

Subagent writes to .completion.json when curiosity is medium/high:

```json
{
  "status": "{actual status}",
  "tokensUsed": "{actual token count}",
  "inputTokens": "{actual input tokens}",
  "outputTokens": "{actual output tokens}",
  "compactionEvents": "{actual count}",
  "summary": "{actual summary of work done}",
  "discoveredIssues": [
    {
      "file": "{actual file path}",
      "line": "{actual line number}",
      "type": "{code-quality|security|performance}",
      "severity": "{low|medium|high}",
      "description": "{actual description}",
      "benefitCost": "{actual ratio}"
    }
  ]
}
```

## Patience Setting (Main Agent Uses This)

The patience setting determines what the MAIN AGENT does with issues returned from subagents.
Do NOT include patience instructions in implementation subagent prompts.

| Value | Main Agent Action on Returned Issues |
|-------|--------------------------------------|
| `low` | Resume PLANNER subagent to update plan with fixes, then continue |
| `medium` | Create tasks for discovered issues in CURRENT version backlog |
| `high` | Create tasks for discovered issues in FUTURE version backlog |

## Token Tracking Requirements (A017)

**MAIN AGENT MUST include session ID in prompt** - subagents cannot measure tokens without it.

```
TOKEN MEASUREMENT (required):
Session ID: {paste actual session ID from your CAT SESSION INSTRUCTIONS}
Session file: /home/node/.config/claude/projects/-workspace/{SESSION_ID}.jsonl

On completion, measure tokens:
TOKENS=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  select(. != null) | (.input_tokens + .output_tokens)] | add // 0' "$SESSION_FILE")
```

**Token tracking detailed requirements:**
- Track cumulative token usage across the ENTIRE session
- If context compaction occurs, PRESERVE pre-compaction token count
- Write TOTAL tokens (pre-compaction + post-compaction) to .completion.json
- Include: inputTokens, outputTokens, tokensUsed (total), compactionEvents count

## Context Limit Enforcement (A018)

**MANDATORY: Validate task size BEFORE delegating.**

| Limit | Percentage | Tokens (200K) | Purpose |
|-------|------------|---------------|---------|
| Soft target | 40% | 80,000 | Recommended task size |
| Hard limit | 80% | 160,000 | MANDATORY decomposition above |
| Context limit | 100% | 200,000 | Absolute ceiling |

```bash
# Pre-delegation validation
if [ "${ESTIMATED_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "ERROR: Task estimate (${ESTIMATED_TOKENS}) exceeds hard limit (${HARD_LIMIT})"
  echo "MANDATORY: Decompose task before delegating. Use /cat:decompose-task"
  exit 1
fi
```

**Post-Execution Limit Check:**

After subagent completes (in collect_results), verify actual usage:

```bash
ACTUAL_TOKENS=$(jq -r '.tokensUsed' "$COMPLETION_JSON")
if [ "${ACTUAL_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "EXCEEDED: Subagent used ${ACTUAL_TOKENS} tokens (hard limit: ${HARD_LIMIT})"
  # Trigger learn with A018 reference
fi
```

## Skill Delegation Requirement

When delegating a skill to a subagent, the subagent MUST:
1. Invoke the exact same skill (not apply its "principles" manually)
2. Return the skill's outputs back to the parent agent

```
# ❌ WRONG - Describes principles instead of invoking skill
"Apply the skill's principles: [manual steps]..."

# ✅ CORRECT - Requires skill invocation and output reporting
"Invoke /cat:{skill-name} and return its output to the parent agent."
```

## Skill Postcondition Reporting

When delegating a skill that has postconditions, the prompt MUST require the subagent to report
the skill's validation output. Each skill defines its own postconditions in its SKILL.md.

```
POSTCONDITION REPORTING (required for skills with validation):
When executing /cat:{skill-name}, you MUST:
- Report the validation output returned by the skill
- State whether the postcondition was met
- If postcondition failed, include the actual values
```

Refer to each skill's SKILL.md for its specific postconditions and success criteria.

## Final Verification Checklist

Before invoking Task tool, confirm:

| Checklist Item | Required For | Mistake Ref |
|----------------|--------------|-------------|
| STATE.md path specified | All implementation tasks | M076, M085 |
| Resolution field mentioned | All implementation tasks | M092 |
| CRITICAL REQUIREMENTS block | All tasks | A008 |
| Exact code examples | Non-trivial changes | M062 |
| Fail-fast conditions | All tasks | spawn-subagent |
| **Session ID in prompt** | All tasks | A017, M099, M109 |
| Token measurement instructions | All tasks | A017 |
| **Pre-delegation limit validation** | All tasks | A018 |
| **Skill postcondition reporting** | Skill delegation tasks | M258 |
| **Skill invocation (not principles)** | Skill delegation tasks | M264 |

**Anti-pattern:** Delegating without reviewing this checklist against your prompt.
