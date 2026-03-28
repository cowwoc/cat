# Plan

## Goal

Update `plugin/concepts/skill-benchmarking.md` to document the extended test-cases.json schema used by all
existing benchmarks, and migrate all four benchmark test-cases.json files to add `"version": "2.0"` and align
their structure with the documented schema.

Currently `skill-benchmarking.md` documents a bare-array format with string assertions and an `id` field, but
all actual benchmarks (work-implement-agent, git-squash-agent, get-output-agent, status-agent) use a richer
format with `test_case_id`, `semantic_unit_id`, `category`, `priming_messages`, and structured assertion
objects (deterministic/semantic/tool_use types). The concept doc is stale and the four benchmark files lack
the `"version": "2.0"` root field.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/skill-benchmarking.md` documents the full extended schema: root object with `version`
  and `test_cases` array; required fields (`test_case_id`, `semantic_unit_id`, `prompt`, `assertions`);
  optional fields (`category`, `priming_messages`); both string and structured assertion formats;
  deterministic assertion methods (`regex`, `string_match`, `structural`); semantic assertion fields
  (`instruction`, `expected`); tool_use assertion fields (`tool`, `expected`); and `semantic_unit_id`
  naming convention (domain-specific, not sequential)
- [ ] `plugin/concepts/skill-benchmarking.md` documents `priming_messages` as a first-class optional field
  with role/content format
- [ ] All four benchmark test-cases.json files contain `"version": "2.0"` at the root
- [ ] All four benchmark test-cases.json files use `test_case_id` (not `id`) and include `semantic_unit_id`
- [ ] User-visible behavior unchanged (no runtime behavior is modified — only docs and data files)
- [ ] Java test suite passes with no regressions (`mvn -f client/pom.xml test`)
- [ ] E2E: Validate each updated test-cases.json contains `"version": "2.0"` and `"test_cases"` at the root using:
  `for f in plugin/skills/work-implement-agent/benchmark/test-cases.json plugin/skills/git-squash-agent/benchmark/test-cases.json plugin/skills/get-output-agent/benchmark/test-cases.json plugin/skills/status-agent/benchmark/test-cases.json; do grep -q '"version"' "$f" && grep -q '"test_cases"' "$f" && echo "OK: $f" || echo "FAIL: $f"; done`

## Research Findings

**Actual benchmark files (4 total, all in `benchmark/` subdirectories):**
- `plugin/skills/work-implement-agent/benchmark/test-cases.json`
- `plugin/skills/git-squash-agent/benchmark/test-cases.json`
- `plugin/skills/get-output-agent/benchmark/test-cases.json`
- `plugin/skills/status-agent/benchmark/test-cases.json`

**Extended schema structure (from actual files):**
- Root: `{"test_cases": [...]}` (missing `"version": "2.0"`)
- Required test case fields: `test_case_id`, `semantic_unit_id`, `prompt`, `assertions`
- Optional test case fields: `category` (e.g., `"REQUIREMENT"`, `"PROHIBITION"`, `"SEQUENCE"`), `priming_messages`
- `priming_messages` format: array of `{"role": "user"|"assistant", "content": "..."}` objects
- Assertion types:
  - `deterministic`: `assertion_id`, `type`, `method` (`regex`/`string_match`/`structural`), `description`,
    `pattern`, `expected` (bool)
  - `semantic`: `assertion_id`, `type`, `description`, `instruction`, `expected` (bool)
  - `tool_use`: `assertion_id`, `type`, `description`, `tool` (e.g., `"Skill"`), `expected` (bool)
- All 4 files already use `test_case_id` (not `id`) — no field rename needed
- None of the 4 files have a `"version"` field — need to add `"version": "2.0"` at root

**`semantic_unit_id` convention:** Domain-specific identifiers describing the behavioral unit under test
(e.g., `"unit_single_job_collect_immediately"`, `"unit_step3_seq_read_comment"`), not sequential numbers.

## Jobs

### Job 1

- Update `plugin/concepts/skill-benchmarking.md`:
  - Replace the old "Eval Set Format" section (which documents a bare array with `id` and string assertions)
    with the extended schema:
    - Root object: `{"version": "2.0", "test_cases": [...]}` with field definitions for `version` (string,
      schema version, currently `"2.0"`) and `test_cases` (array of test case objects)
    - Test case fields table: `test_case_id` (required, unique identifier), `semantic_unit_id` (required,
      domain-specific behavioral unit name — not sequential), `prompt` (required, user prompt),
      `assertions` (required, array of assertion objects), `category` (optional, e.g., `REQUIREMENT`,
      `PROHIBITION`, `SEQUENCE`), `priming_messages` (optional, array of prior conversation turns)
    - Add `priming_messages` subsection documenting role/content format with an example JSON snippet
    - Keep the existing "Guidelines for writing test cases" subsection in "Eval Set Format" unchanged
    - Update the "Assertion Schema" section: completely replace the existing prose (which describes
      natural-language string assertions and PASS/FAIL verdicts) with documentation of the three
      structured assertion types:
      - deterministic: fields `assertion_id` (string), `type` (literal `"deterministic"`), `method`
        (`"regex"` | `"string_match"` | `"structural"`), `description` (string), `pattern` (string),
        `expected` (bool). Add a methods table: `regex` = pattern match with regex, `string_match` =
        literal substring match, `structural` = structural/format check.
      - semantic: fields `assertion_id` (string), `type` (literal `"semantic"`), `description` (string),
        `instruction` (string), `expected` (bool)
      - tool_use: fields `assertion_id` (string), `type` (literal `"tool_use"`), `description` (string),
        `tool` (string, e.g. `"Skill"`), `expected` (bool)
      Note: the existing "Grading Output Schema" and "PASS/FAIL verdict" content that follows in the
      file is in a SEPARATE "Grading Output Schema" section — that section must NOT be modified.
    - Update the example JSON in "Eval Set Format" to show the new root-object format with `version`
      and a test case using structured assertion objects (include one of each type: deterministic,
      semantic, tool_use), with `priming_messages` example
    - Keep the following sections UNCHANGED (do not modify their content):
      - "Overview" section
      - "Grading Output Schema" section
      - "Benchmark JSON Schema" section
      - "Benchmark/Iterate Workflow" section
      - "Description Optimization" section

### Job 2

- Add `"version": "2.0"` as the first field in the root object of each benchmark test-cases.json file:
  - `plugin/skills/work-implement-agent/benchmark/test-cases.json`
  - `plugin/skills/git-squash-agent/benchmark/test-cases.json`
  - `plugin/skills/get-output-agent/benchmark/test-cases.json`
  - `plugin/skills/status-agent/benchmark/test-cases.json`
  - For each file: use the Read tool to read the file, then use the Edit tool to insert
    `"version": "2.0",` as the first line after the opening `{` of the root object (before the
    `"test_cases"` key), preserving 2-space indent and all existing content unchanged
- Verify each file:
  - Contains `"version": "2.0"` at root
  - Uses `test_case_id` (not `id`)
  - All test cases have `semantic_unit_id`
- Run Java tests: `mvn -f client/pom.xml test`
- Update `.cat/issues/v2/v2.1/update-benchmark-schema-docs/index.json`: set `"status": "closed"`, `"progress": "100%"`
- Commit all changes (both Jobs 1 and 2, including the index.json update) in a single commit with
  type `config:` (because `plugin/concepts/skill-benchmarking.md` is in `plugin/concepts/` which uses
  `config:` per CLAUDE.md; the benchmark test-cases.json files in `plugin/skills/` are data files with
  no runtime behavior change)
