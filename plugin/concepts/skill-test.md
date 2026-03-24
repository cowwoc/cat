<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Test Case Format

Each skill may have a `test/` subdirectory containing individual markdown test case files. Each test
case is a separate `.md` file that describes a scenario and the assertions the agent must satisfy.

## Directory Convention

```
plugin/skills/<skill-name>/
  test/
    <test-case-slug>.md   (one file per test case)
  results.json            (written after a test run; see plugin/concepts/skill-test-results.md)
```

## Filename Convention

Test case filenames use lowercase kebab-case (e.g., `squash-trigger-basic.md`, `step1-decompose-goal.md`).

- The name should describe the scenario being tested, not be an internal ID.
- Names must be unique within the skill's `test/` directory.
- Avoid generic names like `test1.md` or `case_a.md`.

## Test Case File Format

Each test case file uses YAML frontmatter followed by required markdown sections.

### Frontmatter Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | yes | Test type. Must be one of: `should-trigger`, `should-not-trigger`, `behavior` |
| `category` | string | yes | Semantic category for grouping and filtering. Must be one of the values in [Category Values](#category-values). |

### Required Sections

| Section | Description |
|---------|-------------|
| `## Scenario` | The user prompt or setup context sent to the agent under test |
| `## Tier 1 Assertion` | The primary assertion — the single most important behavioral property to check |
| `## Tier 2 Assertion` | A secondary assertion — a supporting property that should also hold |

Both tier sections are required, even if the tier 2 assertion is weaker than tier 1. A test case
without both sections is malformed and will be rejected by the `ValidateSkillTestFormat` hook.

### Example File

```markdown
---
type: should-trigger
category: routing
---
## Scenario

The user says: "Squash my last 3 commits into one."

## Tier 1 Assertion

The agent invokes the git-squash skill before running any git commands.

## Tier 2 Assertion

The agent does not run destructive git commands without first presenting a summary to the user.
```

## Type Values

| Value | Meaning |
|-------|---------|
| `should-trigger` | The skill should be invoked given this scenario |
| `should-not-trigger` | The skill should NOT be invoked given this scenario |
| `behavior` | Validates agent behavior when the skill is active (not about trigger routing) |

## Category Values

Category is a fixed lowercase slug. Valid values:

| Value | Meaning |
|-------|---------|
| `routing` | Tests about skill activation — whether the skill is invoked given this scenario (used with `should-trigger` and `should-not-trigger` types) |
| `sequence` | Tests about sequential or ordered behavior (e.g., step numbering, procedure order) |
| `requirement` | Tests about satisfying stated requirements in the output |
| `consequence` | Tests about reasoning from findings to a conclusion |
| `conditional` | Tests about conditional branching behavior |

## Writing Effective Test Cases

- The `## Scenario` section must contain enough context for the agent to reproduce the test independently.
- Tier 1 assertions should be the single most discriminating check — one that passes only when the
  skill is working correctly.
- Tier 2 assertions should cover a supporting property, such as absence of harmful behavior or a
  secondary output characteristic.
- Avoid assertions that are always true regardless of skill activation (non-discriminating assertions).
- For `should-not-trigger` tests, the tier 1 assertion should explicitly state that the skill was not
  invoked.
