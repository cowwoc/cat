<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Organic Test Design

Organic test cases verify that an agent chooses the correct skill and follows its procedure —
using realistic prompts with no system_reminder scaffolding that lists available skills.

## What Makes a Good Positive Test Case

A positive test case is a prompt that **should** trigger the skill being tested. Good positive cases:

- Describe a realistic work scenario a user might actually type (not a contrived test prompt).
- Contain enough semantic signal for the agent to choose the skill without being told it exists.
- Do NOT name the skill directly (e.g., "use cat:git-squash to…") — the agent must infer the correct skill.
- Do NOT include a `system_reminder` field that lists available skills or enumerates skill names.
- Are written in natural language that a developer, operator, or end-user would actually produce.

**Example (git-squash skill):**
```
Squash my last 3 commits into one before I open the PR — keep the commit message from the most recent one.
```
This is good: it describes the user's intent without naming the skill, and a well-calibrated agent should
recognize that `cat:git-squash` handles this.

**Counter-example (too explicit):**
```
Run cat:git-squash on my last 3 commits.
```
This is bad: it names the skill, so it cannot test whether the agent selects it organically.

## What Makes a Good Negative Test Case

A negative test case is a prompt that **should NOT** trigger the skill. Good negative cases:

- Describe a clearly out-of-scope task — one that requires a different skill, a built-in tool, or no skill at all.
- Cover distinct out-of-scope categories (do not write three prompts that are all slight variations of the
  same off-topic scenario).
- Are realistic prompts that an agent might plausibly confuse with the target skill's trigger domain.

**Example (negative cases for git-squash skill):**
1. "Show me the git log for the last 10 commits." — read-only, no squash needed
2. "Rebase my branch onto main and resolve any conflicts." — rebase skill, not squash
3. "I need to cherry-pick commit abc123 into my feature branch." — cherry-pick operation

These are good: each covers a distinct out-of-scope scenario and is realistic.

## Tier 1 and Tier 2 Assertions

Positive test cases must carry both a Tier 1 and a Tier 2 assertion.

### Tier 1 — Skill Chosen

Tier 1 asserts that the agent invoked the correct skill at all. Use the `must_use_tools` field to specify
that the `Skill` tool was called. This is the minimum bar: the right skill was reached.

```json
"assertions": [
  {
    "tier": 1,
    "type": "must_use_tools",
    "tools": ["Skill"],
    "description": "Agent invoked a skill (Skill tool was called)"
  }
]
```

### Tier 2 — Skill Followed Correctly

Tier 2 asserts that the skill's procedure was followed correctly, not just that it was chosen. A Tier 2
assertion checks for a behavioral outcome — a specific tool call sequence, output content, or state change
that the skill's procedure requires.

**Tier 2 examples:**

| Skill | Tier 2 assertion |
|-------|-----------------|
| `cat:git-squash` | `must_use_tools: ["Bash"]` with a command matching `git rebase -i` or `git reset --soft` |
| `cat:git-commit` | Output contains a commit message in conventional format (regex: `^(feat\|fix\|refactor):`) |
| `cat:work` | The `TaskCreate` tool was called before any `Edit` or `Write` tool call |
| `cat:status` | Output contains a table listing issues with Status column |

Tier 2 assertions must be falsifiable: they must fail when the agent selects the skill but skips or
misexecutes a key procedure step.

**Counter-examples (Tier 1 masquerading as Tier 2):**
- "Agent produced some output" — always true, not discriminating
- "Agent did not crash" — always true, not falsifiable
- "Output is non-empty" — does not verify the skill's procedure

## Test Case JSON Format

Organic test cases follow this schema:

```json
{
  "test_cases": [
    {
      "test_case_id": "positive_1",
      "type": "positive",
      "prompt": "Squash my last 3 commits into one before opening the PR.",
      "assertions": [
        {
          "tier": 1,
          "type": "must_use_tools",
          "tools": ["Skill"],
          "description": "Agent invoked a skill"
        },
        {
          "tier": 2,
          "type": "must_use_tools",
          "tools": ["Bash"],
          "description": "Agent ran a git command to squash commits"
        }
      ]
    },
    {
      "test_case_id": "negative_1",
      "type": "negative",
      "prompt": "Show me the git log for the last 10 commits.",
      "assertions": [
        {
          "tier": 1,
          "type": "must_not_use_tools",
          "tools": ["Skill"],
          "description": "Agent did NOT invoke cat:git-squash"
        }
      ]
    }
  ]
}
```

**Field definitions:**

| Field | Type | Description |
|-------|------|-------------|
| `test_case_id` | string | Unique ID; prefix `positive_` or `negative_` to make type self-describing |
| `type` | `"positive"` or `"negative"` | Whether the skill SHOULD or SHOULD NOT be triggered |
| `prompt` | string | Realistic user prompt with no skill scaffolding |
| `assertions` | array | One or more assertion objects (see tiers above) |
| `tier` | 1 or 2 | Tier 1 = skill chosen; Tier 2 = procedure followed |
| `type` (assertion) | string | `must_use_tools`, `must_not_use_tools`, `output_contains`, `output_matches_regex` |
| `tools` | array of strings | Tool names for `must_use_tools` / `must_not_use_tools` assertions |
| `description` | string | Human-readable assertion label used in grading output |

## Calibration: How to Tune Trigger Sensitivity

A well-calibrated skill is neither over-triggering (invoked on out-of-scope prompts) nor under-triggering
(missed on in-scope prompts). Use the empirical-test-runner to calibrate:

1. Run positive cases using the SPRT test run (p0=0.95, p1=0.85, α=β=0.05): pass rate must be ≥95%. If below
   95%, the `description:` frontmatter in SKILL.md may not contain enough trigger signal. Revise the test prompt
   to add semantic clarity, OR revise the skill's `description:` to include more trigger keywords.
2. Run negative cases: the skill must NOT be invoked. If it is, the `description:` frontmatter is too broad.
   Narrow the trigger language or add explicit exclusion clauses.
3. Do NOT lower the 95% threshold — revise prompts or skill descriptions until the threshold is met.
