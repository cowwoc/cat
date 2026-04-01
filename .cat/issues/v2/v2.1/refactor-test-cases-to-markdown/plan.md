# Plan: refactor-test-cases-to-markdown

## Current State

Test cases for the SPRT instruction testing workflow are authored as `.md` files (e.g.,
`plugin/skills/instruction-builder-agent/test/*.md`) but the SPRT loop, CLI tools (`InstructionTestRunner`),
and test-run subagents consume a separate `test-cases.json` file. This requires converting between formats
and maintaining two representations of the same data.

## Target State

Eliminate `test-cases.json` as an intermediate format. The `.md` test case files become the single source of
truth consumed directly by:
- `InstructionTestRunner` Java CLI (parses markdown headings, bullet lists, and frontmatter)
- Test-run subagents (read `.md` files from the test directory)
- The instruction-builder skill procedure (references `.md` files instead of JSON)

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** CLI tool argument signatures change (paths to `.md` directory instead of `.json` file);
  existing `test-cases.json` files in `instruction-test/` directories become obsolete; persisted
  `instruction-test.json` metadata files reference `test-cases.json` paths that will no longer exist
- **Mitigation:** Update all CLI commands atomically with the skill procedure changes; add migration to
  convert existing `instruction-test/test-cases.json` files to `.md` format; run full test suite before and
  after

## Files to Modify

### Java source
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java` — Update `detect-changes`,
  `map-units`, `persist-artifacts`, and `readTestCaseIds` to parse `.md` files from a directory instead of a
  single JSON file
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerTest.java` — Rewrite tests to
  create `.md` test case files instead of `test-cases.json`

### Plugin skill (instruction-builder)
- `plugin/skills/instruction-builder-agent/first-use.md` — Replace all 28 references to `test-cases.json`
  with `.md` file directory references; update Step 4.1 (auto-generate test cases) to output `.md` files;
  update Step 4.3 (SPRT run) subagent prompts to read `.md` files; update Steps 4.2, 7, 8

### Plugin documentation
- `plugin/concepts/instruction-test-design.md` — Update `empirical-test-runner --config` example to reference
  `.md` test directory

### Existing test-cases.json files to migrate
- `plugin/skills/grep-and-read-agent/test/test-cases.json` — Convert to `.md` files
- `plugin/skills/grep-and-read-agent/instruction-test/test-cases.json` — Convert to `.md` files
- `plugin/skills/plan-before-edit-agent/test/test-cases.json` — Convert to `.md` files

### Persisted metadata referencing test-cases.json
- `plugin/skills/status-agent/instruction-test/instruction-test.json` — Update `test_cases.path` field
- `plugin/skills/get-output-agent/instruction-test/instruction-test.json` — Update `test_cases.path` field

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Define .md test case schema
- Document the canonical `.md` test case format (frontmatter fields, section headings, assertion syntax) so
  Java parsing and LLM authoring align on one specification
  - Files: `plugin/concepts/instruction-test-design.md`

### Job 2: Update InstructionTestRunner to parse .md files
- Update `detect-changes` to accept a test directory path instead of `test-cases.json` path; enumerate `.md`
  files in the directory
- Update `map-units` to accept a test directory path; read test case IDs from `.md` filenames
- Update `persist-artifacts` to copy `.md` files instead of `test-cases.json`
- Update `readTestCaseIds` to scan a directory for `.md` files and extract IDs from filenames/frontmatter
- Update `init-sprt` and `merge-results` argument signatures if they reference `test-cases.json` paths
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`

### Job 3: Update InstructionTestRunner tests
- Rewrite test methods to create `.md` test case files in temp directories instead of `test-cases.json`
- Verify all existing test scenarios still pass with the new format
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerTest.java`

### Job 4: Update instruction-builder first-use.md
- Replace all `test-cases.json` references with `.md` directory references
- Update Step 4.1 to output individual `.md` files per test case instead of a single JSON file
- Update Step 4.3 subagent prompts to read `.md` files from the test directory
- Update Step 4.2 incremental selection to work with `.md` files
- Update Steps 7 and 8 test case references
  - Files: `plugin/skills/instruction-builder-agent/first-use.md`

### Job 5: Migrate existing test-cases.json files to .md format
- Convert `plugin/skills/grep-and-read-agent/test/test-cases.json` to individual `.md` files
- Convert `plugin/skills/grep-and-read-agent/instruction-test/test-cases.json` to individual `.md` files
- Convert `plugin/skills/plan-before-edit-agent/test/test-cases.json` to individual `.md` files
- Remove the original `test-cases.json` files after conversion
  - Files: `plugin/skills/grep-and-read-agent/test/`, `plugin/skills/grep-and-read-agent/instruction-test/`,
    `plugin/skills/plan-before-edit-agent/test/`

### Job 6: Update persisted metadata and documentation
- Update `instruction-test.json` files in `status-agent` and `get-output-agent` to reference `.md` test
  directory instead of `test-cases.json` path
- Update `plugin/concepts/instruction-test-design.md` empirical-test-runner example
  - Files: `plugin/skills/status-agent/instruction-test/instruction-test.json`,
    `plugin/skills/get-output-agent/instruction-test/instruction-test.json`,
    `plugin/concepts/instruction-test-design.md`

## Post-conditions

- [ ] No file named `test-cases.json` exists under `plugin/` (excluding `.cat/` worktree artifacts)
- [ ] `grep -r "test-cases.json" plugin/` returns zero matches
- [ ] `grep -r "test-cases.json" client/src/` returns zero matches
- [ ] All existing `.md` test case files in `plugin/skills/instruction-builder-agent/test/` are consumable by
  the updated `InstructionTestRunner` CLI commands
- [ ] `mvn -f client/pom.xml test` passes with exit code 0
- [ ] E2E: Run `instruction-test-runner detect-changes` against a skill with `.md` test cases and confirm it
  returns valid output referencing test case IDs derived from `.md` filenames
