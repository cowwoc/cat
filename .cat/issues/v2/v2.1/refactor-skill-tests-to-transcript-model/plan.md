# Plan

## Type

refactor

## Goal

Redesign skill tests to use stored transcripts and grader agents for SPRT evaluation. Each test scenario runs
once and stores the raw transcript in a `runs/` subdirectory (as defined by
2.1-revise-instruction-builder-file-targets). Assertions are evaluated by new dedicated grader agents that
read transcripts rather than inline during agent execution. All existing test-cases.json and test-results.json
files are migrated to the new format. Agents replaced by the new grader model (e.g., cat:skill-validator-agent)
are removed.

## Pre-conditions

- 2.1-revise-instruction-builder-file-targets is complete (defines runs/ directory location)

## Post-conditions

- [ ] New transcript-based test schema documented in plugin/concepts/skill-test.md (updated)
- [ ] cat:skill-grader-agent skill created: reads a transcript + assertion list, returns pass/fail per assertion
- [ ] cat:skill-validator-agent and any other agents superseded by the grader model are removed
- [ ] All existing test-cases.json files converted to .md scenario files with named assertions
- [ ] All existing test-results.json files replaced by results.json keyed by scenario filename and assertion name
- [ ] SPRT statistics tracked per assertion (not per file) in results.json
- [ ] ValidateSkillTestFormat hook updated to enforce new .md schema
- [ ] E2E verification: run a skill test end-to-end and confirm transcript stored in runs/, graded correctly,
      results.json updated
- [ ] Tests pass, no regressions

## Research Findings

### Current file inventory

**test-cases.json files to migrate (10 files):**

| Old path | New target directory |
|----------|---------------------|
| `plugin/skills/work-implement-agent/benchmark/test-cases.json` | `plugin/tests/skills/work-implement-agent/first-use/` |
| `plugin/skills/init/test/test-cases.json` | `plugin/tests/skills/init/first-use/` |
| `plugin/skills/git-squash-agent/benchmark/test-cases.json` | `plugin/tests/skills/git-squash-agent/first-use/` |
| `plugin/skills/stakeholder-review-agent/tests/test-cases.json` | `plugin/tests/skills/stakeholder-review-agent/first-use/` |
| `plugin/skills/cleanup/test/test-cases.json` | `plugin/tests/skills/cleanup/first-use/` |
| `plugin/skills/config/test/test-cases.json` | `plugin/tests/skills/config/first-use/` |
| `plugin/skills/get-output-agent/benchmark/test-cases.json` | `plugin/tests/skills/get-output-agent/first-use/` |
| `plugin/skills/github-trigger-workflow-agent/test/test-cases.json` | `plugin/tests/skills/github-trigger-workflow-agent/first-use/` |
| `plugin/skills/status-agent/benchmark/test-cases.json` | `plugin/tests/skills/status-agent/first-use/` |
| `plugin/skills/retrospective-agent/test/test-cases.json` | `plugin/tests/skills/retrospective-agent/first-use/` |

**test-results.json files to migrate (3 files):**

| Old path | New results.json path |
|----------|-----------------------|
| `plugin/skills/stakeholder-review-agent/tests/test-results.json` | `plugin/tests/skills/stakeholder-review-agent/first-use/results.json` |
| `plugin/skills/cleanup/test/test-results.json` | `plugin/tests/skills/cleanup/first-use/results.json` |
| `plugin/skills/retrospective-agent/test/test-results.json` | `plugin/tests/skills/retrospective-agent/first-use/results.json` |

**Files/directories to remove:**
- `plugin/skills/skill-validator-agent/` (entire directory — superseded by grader model)
- `plugin/agents/skill-grader-agent.md` (after skill created in plugin/skills/)
- All source `test-cases.json` and `test-results.json` files listed above (after migration)
- Empty `test/`, `tests/`, and `benchmark/` directories left after migration

**New skill to create:**
- `plugin/skills/skill-grader-agent/SKILL.md` + `first-use.md` (from plugin/agents/skill-grader-agent.md content)

### New directory convention

Tests for a file at `plugin/<path>/<name>.md` go to `plugin/tests/<path>/<name>/`.
Results file lives at `plugin/tests/<path>/<name>/results.json`.
Transcripts from runs go to `plugin/tests/<path>/<name>/runs/`.

### New .md scenario file format

```markdown
---
type: behavior
category: <lowercase-category>
---
## Scenario

<test prompt / scenario description>

## Tier 1 Assertion

<primary assertion — the single most discriminating check>

## Tier 2 Assertion

<secondary assertion — a supporting behavioral property>
```

### Filename derivation from test-cases.json entries

Filename = `semantic_unit_id` with `unit_` prefix stripped, underscores → hyphens.
If `semantic_unit_id` is absent, use `test_case_id` lowercased, underscores → hyphens.

Examples:
- `unit_step4_no_dir_delete` → `step4-no-dir-delete.md`
- `unit_routing_analysis_action` → `routing-analysis-action.md`

### Category mapping (old uppercase → new lowercase)

| Old value | New value |
|-----------|-----------|
| `SEQUENCE` | `sequence` |
| `CONDITIONAL` | `conditional` |
| `REQUIREMENT` | `requirement` |
| `CONSEQUENCE` | `consequence` |
| `PROHIBITION` | `requirement` |

### Type mapping

All existing test cases use behavioral prompts (simulating agent execution). Map all to `type: behavior`.

### Assertion mapping to Tier 1 / Tier 2

Each test case's `assertions` array maps as follows:
- **Tier 1**: Text of the first `semantic` assertion's `description` field. If no semantic assertions, use first
  assertion's `description`.
- **Tier 2**: Text of the second assertion's `description`. If only one assertion, write a reasonable secondary
  assertion that tests an absence or supporting property derived from the scenario context.

Use the assertion `description` field as the tier text (not `instruction` — `description` is concise and
assertion-focused).

### New results.json schema (per-assertion SPRT)

```json
{
  "skill_hash": "<SHA-256 of first-use.md at run time>",
  "model": "<model identifier>",
  "session_id": "<CLAUDE_SESSION_ID>",
  "timestamp": "<ISO-8601 UTC timestamp>",
  "overall_decision": "pass|fail|inconclusive",
  "scenarios": {
    "<scenario-filename-without-extension>": {
      "type": "behavior",
      "tier_1": {
        "log_ratio": 0.0,
        "pass_count": 0,
        "fail_count": 0,
        "total_runs": 0,
        "total_tokens": 0,
        "total_duration_ms": 0,
        "decision": "inconclusive"
      },
      "tier_2": {
        "log_ratio": 0.0,
        "pass_count": 0,
        "fail_count": 0,
        "total_runs": 0,
        "total_tokens": 0,
        "total_duration_ms": 0,
        "decision": "inconclusive"
      }
    }
  }
}
```

### Migrating existing test-results.json to new format

For each entry in `sprt.test_cases`, find the corresponding new scenario filename (using the semantic_unit_id
mapping above). Map the per-case data to tier_1 (primary assertion). Set tier_2 to all zeros/inconclusive.

Old field → new location:
- `log_ratio` → `scenarios.<name>.tier_1.log_ratio`
- `passes` → `scenarios.<name>.tier_1.pass_count`
- `failures` → `scenarios.<name>.tier_1.fail_count`
- `runs` → `scenarios.<name>.tier_1.total_runs`
- `total_tokens` → `scenarios.<name>.tier_1.total_tokens`
- `total_duration_ms` → `scenarios.<name>.tier_1.total_duration_ms`
- Derive `decision` from `log_ratio` vs SPRT thresholds (accept if log_ratio >= accept_boundary, reject if <=
  reject_boundary, else inconclusive)

Top-level fields: copy `skill_hash`, `model`, `session_id`, `timestamp`, `overall_decision` from old format.

### ValidateSkillTestFormat hook path pattern change

**Current regex:** `(?:^|/)plugin/skills/[^/]+/test/[^/]+\.md$`
**New regex:** `(?:^|/)plugin/tests/.+\.md$`

Only change is the path portion — format validation logic (frontmatter, sections) stays the same.

Also update `ValidateSkillTestFormatTest.java` to use the new path pattern in test cases.

### cat:skill-grader-agent skill structure

Create from existing `plugin/agents/skill-grader-agent.md`:

**`plugin/skills/skill-grader-agent/SKILL.md`:**
```
---
description: >
  Internal subagent — grades a list of assertions against a single test-case output, assigning pass/fail
  verdicts with evidence quotes. Reads run output via git show, commits grading JSON, returns commit SHA.
model: haiku
user-invocable: false
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" skill-grader-agent "$0"`
```

**`plugin/skills/skill-grader-agent/first-use.md`:**
Copy content from `plugin/agents/skill-grader-agent.md` (the body below the frontmatter), adding license header.

### plugin/concepts/skill-test.md updates

Add a `## Runs Directory` section after the `## Directory Convention` section documenting:
- `runs/` subdirectory location: `plugin/tests/<path>/<name>/runs/`
- Transcript file naming: `<scenario-slug>-<run-id>.md` (run-id is a short unique identifier per execution)
- Transcripts are written by the test runner before grading
- Graders read transcripts from runs/ via `git show` or direct file access

Update `## Directory Convention` to show the `plugin/tests/` path (not `plugin/skills/<skill>/test/`):
```
plugin/tests/<path>/<name>/
  <scenario-slug>.md     (one file per test case)
  results.json           (written after a test run; see plugin/concepts/skill-test-results.md)
  runs/
    <scenario-slug>-<run-id>.md  (raw transcript per run)
```

### plugin/concepts/skill-test-results.md updates

Replace `test_cases` array schema with `scenarios` object schema per the new results.json format above.

Update `## File Location` to:
```
plugin/tests/<path>/<name>/results.json
```

Update the schema description to reflect per-assertion SPRT (tier_1 and tier_2 objects per scenario).

## Jobs

### Job 1

Independent of all other jobs. Modify concept docs and create/remove skill files.

- Modify `plugin/concepts/skill-test.md`:
  - Change `## Directory Convention` code block to show `plugin/tests/<path>/<name>/` structure with
    `runs/` subdirectory
  - Add `## Runs Directory` section after `## Directory Convention` documenting transcript file naming
    and how graders read them
- Modify `plugin/concepts/skill-test-results.md`:
  - Update `## File Location` to `plugin/tests/<path>/<name>/results.json`
  - Replace `test_cases` array entries table with `scenarios` object schema matching the new JSON structure
    (showing tier_1 and tier_2 keys with their sub-fields)
- Create `plugin/skills/skill-grader-agent/SKILL.md` with frontmatter as shown in Research Findings and
  `!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" skill-grader-agent "$0"`` directive
- Create `plugin/skills/skill-grader-agent/first-use.md` by copying the body from
  `plugin/agents/skill-grader-agent.md` and adding the license header comment block
- Remove `plugin/agents/skill-grader-agent.md` (content moved to skill)
- Remove `plugin/skills/skill-validator-agent/` directory (entire directory)

### Job 2

Migrate all test-cases.json files. Does not overlap with Job 1, Job 3, or Job 4.

For each of the 10 test-cases.json files listed in Research Findings:
1. Read the JSON file
2. For each test case entry, derive the filename from `semantic_unit_id` (strip `unit_` prefix, replace `_` with `-`)
3. Map `category` (uppercase → lowercase) and set `type: behavior`
4. Map assertions to Tier 1 / Tier 2 per the assertion mapping rules
5. Write the `.md` file to the target directory shown in Research Findings
6. Add license header to each new .md file
7. After creating all .md files for a test-cases.json, delete the test-cases.json file
8. Remove empty `test/`, `tests/`, or `benchmark/` directories left behind

### Job 3

Migrate test-results.json files, update ValidateSkillTestFormat, and update Java tests.
Does not overlap with Job 1 or Job 2 (different files).

- For each of the 3 test-results.json files, convert to the new results.json format per Research Findings
  (scenarios object with per-tier SPRT data) and write to the new plugin/tests/ location
- In `client/src/main/java/io/github/cowwoc/cat/hooks/write/ValidateSkillTestFormat.java`:
  - Change `TEST_MD_PATTERN` from `(?:^|/)plugin/skills/[^/]+/test/[^/]+\.md$`
    to `(?:^|/)plugin/tests/.+\.md$`
- In `client/src/test/java/io/github/cowwoc/cat/hooks/test/ValidateSkillTestFormatTest.java`:
  - Update any test file paths that use the old `plugin/skills/<skill>/test/` pattern to use
    `plugin/tests/<path>/<name>/` paths
- Run `mvn -f client/pom.xml verify -e` and fix any build failures
- Update `index.json` to `status: closed` in the SAME commit as the last implementation commit
