# Skill Validation

How to use the eval-driven validation agents to verify that new and revised skills route correctly and
execute as intended.

## Why Validate Skills

Skills rely on their `description:` frontmatter for intent routing — Claude reads the description and
decides whether to invoke the skill for a given user request. A description that is too broad activates
the skill for requests it was not designed to handle. A description that is too narrow misses requests
it should handle. Validation surfaces these calibration errors before the skill is deployed.

Beyond routing, skills can contain instructions that inadvertently prime agents toward incorrect behavior,
or omit steps that agents would need to execute correctly. Test prompts make these gaps visible.

## Validation Agents

### skill-validator-agent

**When to use**: After writing or revising a skill, to verify that its description routes correctly.

Accepts a skill path and a JSON object with two arrays:
- `should_trigger`: Phrases that the skill's description should activate the skill for.
- `should_not_trigger`: Phrases that the skill's description should not activate the skill for.

Returns a per-prompt PASS/FAIL result with a one-sentence explanation and an overall calibration verdict.

**Invoke from instruction-builder-agent Step 8:**
```
Skill(skill="cat:skill-validator-agent", args="<skill-path> <test-prompts-json-path>")
```

**Test prompt format:**
```json
{
  "should_trigger": [
    "squash my last 3 commits",
    "combine commits before merging"
  ],
  "should_not_trigger": [
    "rebase onto main",
    "merge the feature branch"
  ]
}
```

### description-tester-agent

**When to use**: When you want to audit a description's trigger precision without writing test prompts
manually. The agent generates its own calibration queries from the description text.

Accepts a skill path. Generates queries in four categories (core triggers, synonym triggers, boundary
cases, adjacent non-triggers), evaluates each, and reports overall calibration status with recommendations.

**Invoke:**
```
Skill(skill="cat:description-tester-agent", args="<skill-path>")
```

Returns a calibration report with an overall status: WELL-CALIBRATED, OVER-BROAD, UNDER-BROAD, or
AMBIGUOUS.

## Iteration Loop

Skill validation is an iteration loop, not a one-time check. If validation reveals miscalibration:

1. Identify the failing prompts and their explanations.
2. Revise the `description:` frontmatter to address the specific calibration issue.
3. Re-run validation until all prompts pass.

The goal is WELL-CALIBRATED: every should-trigger prompt activates the skill, and every
should-not-trigger prompt does not.

## What Good Test Prompts Look Like

**Should-trigger prompts** are natural user phrases, not paraphrases of the description:

```
Good: "squash my last 3 commits"        — what a user actually types
Bad:  "use git squash to combine commits" — paraphrase of the skill name
```

**Should-not-trigger prompts** come from adjacent domains that might be confused with the skill:

```
Good: "rebase onto main"   — similar domain (git), different operation
Bad:  "check email"        — completely unrelated (trivially excluded)
```

If you cannot write 2 natural should-trigger phrases, the skill's trigger condition may be unclear.
Revisit the description before adding test prompts.

## When to Skip Validation

Validation is most valuable for:
- New skills with no established usage history
- Descriptions that have been revised to fix routing issues
- Skills with multiple similar siblings in the same domain (high confusion risk)

Validation adds less value for:
- Internal agents invoked only by other agents (not user-facing routing)
- Skills with a single, unambiguous trigger condition (e.g., "MANDATORY: Use instead of X")
