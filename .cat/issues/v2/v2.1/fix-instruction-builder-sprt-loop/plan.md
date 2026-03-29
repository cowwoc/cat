# Plan

## Goal

Fix instruction-builder-agent SPRT test loop steps 4.1-4.3 to generate and use .md transcript format
scenario files instead of the old test-cases.json format. Also update empirical-test-agent and
skill-grader-agent if they still reference the old JSON format.

## Pre-conditions

(none)

## Post-conditions

- [ ] Steps 4.1-4.3 in plugin/skills/instruction-builder-agent/first-use.md no longer skip or fail due to
  test-cases.json format mismatch
- [ ] Step 4.1 generates .md scenario files in plugin/tests/<skill>/<scope>/ using the new transcript format
  (Turn sections + Assertions numbered list)
- [ ] Steps 4.2-4.3 read and use .md scenario files for SPRT execution
- [ ] empirical-test-agent and skill-grader-agent updated wherever they reference the old test-cases.json format
- [ ] Regression test added verifying steps 4.1-4.3 execute against .md scenario files
- [ ] No regressions in existing test infrastructure
- [ ] E2E verification: run instruction-builder on a real skill and confirm steps 4.1-4.3 execute without
  skipping

## Research Findings

Investigation of affected files confirms:

- `plugin/agents/skill-grader-agent.md` already reads `.md` scenario format — no changes needed
- `plugin/skills/empirical-test-agent/first-use.md` uses its own `/tmp/empirical-test-config.json` format
  unrelated to test-cases.json — no changes needed
- Two Java methods in `SkillTestRunner.java` currently accept `test_cases_path` (a JSON file path):
  - `detectChanges(String[] args)`: args `[old_skill_sha, new_skill_path, test_cases_path]` — third arg
    is path to test-cases.json; calls `readAllTestCaseIds(testCasesPath)` to list all TC IDs
  - `mapUnits(String[] args)`: args `[test_cases_path, changed_units_json]` — first arg is path to
    test-cases.json; reads `test_case_id` and `semantic_unit_id` from each entry in the JSON
  - Both must change to accept a test directory path instead of a JSON file path
- New `.md` file format for generated test cases must include `semantic_unit_id` in YAML frontmatter so
  `map-units` can determine which test cases map to which semantic units
- New `.md` file naming convention: use the `semantic_unit_id` as the file stem (e.g.,
  `unit_step44_guard.md`), placed in `${TEST_DIR}/`; the file stem becomes the test case ID
- All assertions in `.md` format are semantic (plain-text numbered list in `## Assertions` section);
  the typed deterministic assertions from JSON (regex, string_match, structural) are replaced by
  plain-text assertions graded entirely by skill-grader-agent
- Step 4.3 test-run subagent currently evaluates deterministic assertions inline and only passes semantic
  assertions to the grader; with `.md` format (all semantic), the test-run subagent no longer grades
  assertions inline — it only executes the prompt, writes output, and returns
- Step 4.4 also contains references to test-cases.json in its investigation flow — those references
  must be updated too to maintain consistency

## Jobs

### Job 1

Update `plugin/skills/instruction-builder-agent/first-use.md` — rewrite Steps 4.1-4.3 (and Step 4.4
references) to use `.md` scenario files instead of `test-cases.json`. Also create the regression test file.

**Step 4.1 rewrite** (currently lines 285-365):

Replace the JSON schema template section entirely. New instructions:

For each testable semantic unit, generate a `.md` file in `${TEST_DIR}/` named `<semantic_unit_id>.md`
(the semantic_unit_id becomes the file stem and serves as the test case ID). File format:

```
---
category: <CATEGORY>
semantic_unit_id: <semantic_unit_id>
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
<scenario prompt text that exercises the constraint>
## Assertions
1. <plain-text assertion describing expected behavior>
2. <plain-text assertion>
```

For CONDITIONAL semantic units that require two scenarios (one triggering, one not), generate two separate
`.md` files: `<semantic_unit_id>_triggered.md` and `<semantic_unit_id>_not_triggered.md`.

Remove the JSON schema block entirely. Remove all mention of `assertion_id`, `type`, `method`,
`pattern`, `expected` fields — these JSON-specific fields do not exist in the `.md` format.

Update the commit step: after presenting generated files to the user for approval and incorporating
feedback, commit all generated `.md` files:
```bash
cd "${TEST_DIR}" && git add *.md && cd - && \
  git -C "${CLAUDE_PROJECT_DIR}" add "${TEST_DIR}/*.md" && \
  git -C "${CLAUDE_PROJECT_DIR}" commit -m "test: generate test cases [session: ${CLAUDE_SESSION_ID}]"
```
Store the commit SHA as `TEST_SET_SHA`. Do NOT retain test case content in context — test-run subagents
read from the committed `.md` file for their assigned test case.

**Step 4.2 rewrite** (currently lines 367-416):

Update both CLI invocations to pass the test directory instead of the JSON file path:

```bash
# detect-changes: third arg is now the test directory
"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-test-runner" detect-changes \
  <SKILL_DRAFT_SHA> <SKILL_TEXT_PATH> "${TEST_DIR}"
```

```bash
# map-units: first arg is now the test directory
"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-test-runner" map-units \
  "${TEST_DIR}" '["unit_step44_guard", "unit_step44_reject"]'
```

Update the output field descriptions to reflect that `all_test_case_ids` is now derived from `.md` file
stems in `${TEST_DIR}` rather than from entries in `test-cases.json`.

**Step 4.3 rewrite** (currently lines 418-775):

1. **Test-run subagent prompt**: Change "reads assertions from `cat {TEST_DIR}/test-cases.json`" to
   "reads the assigned scenario file from `cat {TEST_DIR}/{test_case_id}.md`"

2. **Assertion grading flow**: Since all assertions in `.md` format are semantic (plain-text numbered
   list), remove the inline deterministic assertion grading from the test-run subagent:
   - Remove: "Evaluates deterministic assertions inline and reports results before returning"
   - Remove: step (a) in the pipelining control flow: "Independently verifies deterministic assertions..."
   - Change step (b): "Spawns a grader subagent for all assertions" (not just semantic ones)
   - The test-run subagent return format simplifies to:
     `{"run_id": "<TC_id>_run_<N>", "test_case_id": "<TC_id>", "output_path": "...",
      "duration_ms": <integer>, "total_tokens": <integer>}`
   - Remove `assertion_results` and `semantic_pending` fields from the return format

3. **Prohibition text** (currently "The ONLY permitted read from {TEST_DIR} is test-cases.json"):
   Replace with: "The ONLY permitted read from {TEST_DIR} is `{test_case_id}.md` (the assigned scenario
   file for this run). Do NOT read any other file under {TEST_DIR} via any mechanism."

4. **Permitted read list** in the prohibition block (currently "(1) `{TEST_DIR}/test-cases.json`"):
   Replace with: "(1) `{TEST_DIR}/{test_case_id}.md` (the assigned scenario file)"

5. **Check 2 — Prohibition verification**: Change the rejection condition from "references file paths
   under `{TEST_DIR}/` other than `test-cases.json`" to "references file paths under `{TEST_DIR}/`
   other than `{test_case_id}.md`"

6. **Check 3 — Design-flaw detection**: Change "Read the assertion's `semantic_unit_id` from
   `test-cases.json`" to "Read the `semantic_unit_id` field from the YAML frontmatter of
   `{TEST_DIR}/{test_case_id}.md`"

7. **Minimal happy-path example**: Update to show the new return format (no `assertion_results`
   or `semantic_pending`), show that main agent spawns grader for all assertions

8. **Scalar references passed to test-run subagent**: Update the note to say the subagent reads
   the scenario from `{TEST_DIR}/{test_case_id}.md` instead of from `test-cases.json`

**Step 4.4 references** (currently at line ~777+):

Scan the full SPRT Failure Investigation section for any remaining `test-cases.json` references and
replace them with the equivalent `.md` format references. Specifically, any instructions to read
`test-cases.json` for assertion or semantic_unit_id data should be updated to read from the
`{test_case_id}.md` frontmatter.

**Regression test file**: Create
`plugin/tests/skills/instruction-builder-agent/first-use/step41-generates-md-scenario-files.md` with
content:

```
---
category: REQUIREMENT
semantic_unit_id: unit_step41_md_generation
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
You are the instruction-builder-agent working in Step 4.1. The skill being tested is a fictional skill
"log-analyzer-agent" with one semantic unit: unit_log_1 (REQUIREMENT: always summarize findings in a
table). Generate the test case for unit_log_1 using the .md format. Show the complete file content you
would write to plugin/tests/skills/log-analyzer-agent/first-use/unit_log_1.md.
## Assertions
1. response must produce a file path like plugin/tests/skills/log-analyzer-agent/first-use/unit_log_1.md
2. response must show markdown file content with YAML frontmatter block delimited by --- markers
3. frontmatter must include a category field
4. frontmatter must include a semantic_unit_id field with value unit_log_1
5. file must include a ## Turn 1 section containing a scenario prompt
6. file must include a ## Assertions section with at least one numbered assertion
7. response must NOT include any JSON structure with test_cases array or assertion_id fields
```

**Commit** with message:
`bugfix: update SPRT loop steps 4.1-4.3 to use .md scenario format; add regression test`

### Job 2

Update `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillTestRunner.java` to accept a test
directory path instead of a JSON file path for `detect-changes` and `map-units` subcommands. Also update
or add tests in the test module. Then verify the build and close the issue.

**`detectChanges` method** (currently starting at line 236):

- Update Javadoc: `@param args {@code [old_skill_sha, new_skill_path, test_dir]}` — third arg is now a
  directory path, not a JSON file path
- Update the `args.length != 3` error message and usage string:
  `"Usage: skill-test-runner detect-changes <old_skill_sha> <new_skill_path> <test_dir>"`
- Rename local variable: `Path testDir = Path.of(args[2])` (was `testCasesPath`)
- Replace `Files.notExists(testCasesPath)` check with:
  `if (Files.notExists(testDir) || !Files.isDirectory(testDir))`
  and update error message:
  `"SkillTestRunner detect-changes: test directory not found: " + testDir`
- Change `readAllTestCaseIds(testCasesPath)` call to `readAllTestCaseIds(testDir)` — now reads `.md`
  file stems from the directory
- The `semantic_units_path_hint` string that references `args[2]` continues to work as-is since `args[2]`
  is now the directory path (used verbatim in the hint string)

**`mapUnits` method** (currently starting at line 385):

- Update Javadoc: `@param args {@code [test_dir, changed_units_json]}`
- Update the `args.length != 2` error message and usage string:
  `"Usage: skill-test-runner map-units <test_dir> <changed_units_json>"`
- Rename local variable: `Path testDir = Path.of(args[0])` (was `testCasesPath`)
- Replace `Files.notExists(testCasesPath)` check with:
  `if (Files.notExists(testDir) || !Files.isDirectory(testDir))`
  and update error message:
  `"SkillTestRunner map-units: test directory not found: " + testDir`
- Replace the JSON-reading logic (which read `root.path("test_cases")` array) with directory scanning:
  - List all `.md` files in `testDir` sorted by file name for deterministic ordering
  - For each `.md` file:
    - `testCaseId` = file stem (filename without `.md` extension)
    - `semanticUnitId` = read `semantic_unit_id` from YAML frontmatter using new helper method
  - Partition into `rerunIds` / `carryforwardIds` based on whether `semanticUnitId` is in `changedUnits`

**`readAllTestCaseIds` helper** (currently at line ~1249):

- Change the method to list `.md` files in the test directory (a `Path` parameter):
  - Use `Files.list(testDir)` filtered to `.md` files
  - Return file stems (filename without `.md` extension) sorted alphabetically for determinism
- Update Javadoc accordingly

**New private helper method** `readFrontmatterField(Path mdFile, String fieldName)`:

- Purpose: extract a named field from the YAML frontmatter block of a `.md` file
- Algorithm:
  1. Read all file lines
  2. If the first line is `---`, collect lines until the next `---` line (the frontmatter block)
  3. For each frontmatter line, match `fieldName: value` pattern
  4. Return the trimmed value, or empty string if field not found or no frontmatter present
- Add Javadoc:
  ```java
  /**
   * Reads a named field from the YAML frontmatter block of a Markdown file.
   * <p>
   * Frontmatter is the block between the first two {@code ---} delimiters at the top of the file.
   * Returns an empty string if the file has no frontmatter or the field is absent.
   *
   * @param mdFile    path to the Markdown file
   * @param fieldName the frontmatter key to look up
   * @return the field value, or an empty string if not found
   * @throws IOException if the file cannot be read
   * @throws NullPointerException if {@code mdFile} or {@code fieldName} are null
   */
  ```

**Tests** in `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillTestRunnerTest.java`
(file already exists — add new test methods to it):

- Test `detectChanges` with a test directory (create temp dir with some `.md` files representing test
  cases; create a temp git repo with `TestUtils.createTempGitRepo("main")`):
  - Verify it lists `.md` file stems as test case IDs in `all_test_case_ids`
  - Verify it returns correct `skill_changed`, `frontmatter_changed`, `body_changed` fields
- Test `mapUnits` with a test directory containing `.md` files that have `semantic_unit_id` in frontmatter:
  - Verify it correctly partitions test cases into `rerun_test_case_ids` and `carryforward_test_case_ids`
  - Test with changed units that match some semantic_unit_ids and not others
- Test `readFrontmatterField`:
  - File with frontmatter containing the field → returns correct value
  - File with frontmatter missing the field → returns empty string
  - File with no frontmatter → returns empty string

**Build verification**: Run `mvn -f client/pom.xml verify -e` from the worktree directory to confirm all
tests and linters pass.

**Close issue**: Update `.cat/issues/v2/v2.1/fix-instruction-builder-sprt-loop/index.json`: set
`"status": "closed"`.

**Commit** with message:
`bugfix: update skill-test-runner to accept test directory; add tests; close issue`

## Success Criteria

- `plugin/skills/instruction-builder-agent/first-use.md` Step 4.1 describes generating individual `.md`
  files with YAML frontmatter containing `category` and `semantic_unit_id`, a `## Turn 1` section, and
  a `## Assertions` numbered list — no JSON schema block remains
- `plugin/skills/instruction-builder-agent/first-use.md` Steps 4.2-4.3 pass `${TEST_DIR}` (directory)
  to `detect-changes` and `map-units` CLI commands — no references to `test-cases.json` remain in 4.1-4.3
- `plugin/skills/instruction-builder-agent/first-use.md` Step 4.3 test-run subagent reads
  `{TEST_DIR}/{test_case_id}.md` and its prohibition permits only that file from `${TEST_DIR}`
- `SkillTestRunner.detectChanges` accepts a directory as its third argument and derives test case IDs
  from `.md` file stems in that directory
- `SkillTestRunner.mapUnits` accepts a directory as its first argument and reads `semantic_unit_id`
  from `.md` file frontmatter to partition test cases
- `mvn -f client/pom.xml verify -e` passes with zero errors or warnings
