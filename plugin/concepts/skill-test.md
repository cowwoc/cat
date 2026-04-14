# Skill Test Case Format

Each skill may have a `test/` subdirectory containing individual markdown test case files. Each test
case is a separate `.md` file that describes a scenario and the assertions the agent must satisfy.

## Directory Convention

```
plugin/tests/<path>/<name>/
  <scenario-slug>.md     (one file per test case)
  results.json           (written after a test run; see plugin/concepts/skill-test-results.md)
  runs/
    <scenario-slug>-<run-id>.md  (raw transcript per run)
```

## Runs Directory

Transcripts from individual test executions are stored in the `runs/` subdirectory alongside the scenario files.

**Location:** `plugin/tests/<path>/<name>/runs/`

**Transcript file naming:** `<scenario-slug>-<run-id>.md` where `<run-id>` is a short unique identifier
per execution (e.g., a timestamp or random suffix).

**How transcripts are written:** The test runner writes the raw agent transcript to a `runs/` file before
grading begins. Each execution appends a new transcript file; old runs are not overwritten.

**How graders read transcripts:** Grader agents read transcripts via `git show <SHA>:<path>` or direct
file access. The grader receives the path (and optionally the commit SHA) as an input parameter.

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
| `category` | string | yes | Semantic category for grouping and filtering. Must be one of the values in [Category Values](#category-values). |

### Required Sections

| Section | Description |
|---------|-------------|
| `## Turn 1` | The first (and usually only) user turn — the prompt or setup context sent to the agent under test |
| `## Assertions` | Numbered list of assertions to evaluate against the agent's transcript. Assertions are numbered 1, 2, 3, etc., one per line |

Both sections are required. Additional `## Turn N` sections (N = 2, 3, …) are optional and must be contiguous
starting from 1 — if `## Turn 3` is present then `## Turn 1` and `## Turn 2` must also be present.

A test case without `## Turn 1` and `## Assertions` is malformed and will be rejected by the
`ValidateSkillTestFormat` hook.

### Example File

```markdown
---
category: routing
---
## Turn 1

The user says: "Squash my last 3 commits into one."

## Assertions
1. The agent invokes the git-squash skill before running any git commands.
2. The agent does not run destructive git commands without first presenting a summary to the user.
```

### Multi-Turn Example

```markdown
---
category: sequence
---
## Turn 1

The user says: "Squash my last 3 commits into one."

## Turn 2

The user says: "Actually, make it the last 5 commits."

## Turn 3

The user says: "Ok go ahead."

## Assertions
1. The agent invokes the git-squash skill before running any git commands.
2. The agent updates the squash range when the user changes the count.
3. The agent does not run destructive git commands until the user confirms.
```

## Category Values

Category is a fixed lowercase slug. Valid values:

| Value | Meaning |
|-------|---------|
| `routing` | Tests about skill activation — whether the skill is invoked given the scenario |
| `sequence` | Tests about sequential or ordered behavior (e.g., step numbering, procedure order) |
| `requirement` | Tests about satisfying stated requirements in the output |
| `consequence` | Tests about reasoning from findings to a conclusion |
| `conditional` | Tests about conditional branching behavior |

## Writing Effective Test Cases

- The `## Turn 1` section must contain enough context for the agent to reproduce the test independently.
- Assertions should be ordered by importance: the first assertion is the most critical, subsequent
  assertions provide supporting checks.
- Assertions should be specific and discriminating — they should pass only when the skill is
  working correctly.
- Avoid assertions that are always true regardless of agent behavior (non-discriminating assertions).
- Assertions are numbered sequentially (1, 2, 3, ...) in the `## Assertions` list.
