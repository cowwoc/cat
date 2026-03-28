# Plan

## Goal

Revise instruction-builder to treat any context-loaded file as a build/review/optimize/test target, and relocate tests from skill-directory/test/ to plugin/tests/<path-to-file-without-extension>/

Two concrete changes:

1. **Generalize file targets**: Currently instruction-builder only targets SKILL.md and first-use.md files. Revise it to treat any file loaded into the agent context as a valid target for building, reviewing, optimizing, and testing. This includes plugin/rules/*.md, plugin/agents/*.md, plugin/concepts/*.md, and any other context-loaded file.

2. **Relocate test directory**: Instead of storing tests in <skill-directory>/test/ (e.g., plugin/skills/instruction-builder-agent/test/), store tests in plugin/tests/<path-to-file-without-extension>/ (e.g., plugin/tests/skills/instruction-builder-agent/first-use/ for plugin/skills/instruction-builder-agent/first-use.md).

## Pre-conditions

(none)

## Post-conditions

- [ ] instruction-builder treats any context-loaded file as a valid build/review/optimize/test target
- [ ] Tests relocated from <skill-dir>/test/ to plugin/tests/<path-to-file-without-extension>/
- [ ] All existing instruction-builder tests continue to pass after relocation
- [ ] No regressions in existing skill-building workflows
- [ ] E2E: instruction-builder can target plugin/rules/skill-models.md and produce tests under plugin/tests/rules/skill-models/

## Research Findings

All changes are confined to one file: `plugin/skills/instruction-builder-agent/first-use.md` (1540 lines).

### TEST_DIR path formula

Current formula (lines 233–238):
```bash
SKILL_ABS_PATH="${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}"
TEST_DIR="$(dirname "$SKILL_ABS_PATH")/test"
```

New formula:
```bash
SKILL_ABS_PATH="${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}"
SKILL_RELATIVE="${SKILL_TEXT_PATH#plugin/}"
SKILL_RELATIVE_NO_EXT="${SKILL_RELATIVE%.*}"
TEST_DIR="${CLAUDE_PROJECT_DIR}/plugin/tests/${SKILL_RELATIVE_NO_EXT}"
```

Examples:
- `plugin/skills/instruction-builder-agent/first-use.md` → `plugin/tests/skills/instruction-builder-agent/first-use/`
- `plugin/rules/skill-models.md` → `plugin/tests/rules/skill-models/`
- `plugin/agents/work-execute.md` → `plugin/tests/agents/work-execute/`

### All locations in first-use.md that reference the old test path

| Line(s) | Type | Old text | Action |
|---------|------|----------|--------|
| 233–238 | TEST_DIR computation | `TEST_DIR="$(dirname "$SKILL_ABS_PATH")/test"` | Replace with new formula |
| 252–253 | Artifact location description | "stable `test/` directory adjacent to the skill file" | Update to describe new path |
| 1134 | In-place mode check | `<skill-dir>/test/test-results.json` | Change to `${TEST_DIR}/test-results.json` |
| 1153–1154 | Batch mode file filter | `SKILL.md` and `first-use.md` only | Broaden to all `.md` files |
| 1334–1341 | Step 8 SKILL_DIR derivation | `SKILL_DIR=$(dirname ...)` + example text | Remove SKILL_DIR; use TEST_DIR (already computed in Step 4) |
| 1368–1369 | Create and write test-cases.json | `${SKILL_DIR}/test/` | Change to `${TEST_DIR}/` |
| 1378 | empirical-test-runner --config | `"${SKILL_DIR}/test/test-cases.json"` | Change to `"${TEST_DIR}/test-cases.json"` |
| 1400 | git add in Step 8.5 | `git add "${SKILL_DIR}/test/test-cases.json"` | Change to `git add "${TEST_DIR}/test-cases.json"` |
| 1445–1446 | Related Concepts | old test path references | Update to new path |
| 1460 | Verification checklist | `<skill-dir>/test/` derived from dirname | Update to `plugin/tests/<path-without-ext>/` |
| 1496 | Verification checklist | `<skill-dir>/test/test-results.json` | Update to `${TEST_DIR}/test-results.json` |
| 1528 | Verification checklist | `<skill-dir>/test/` | Update to `${TEST_DIR}/` |

### Existing test files to relocate

Source: `plugin/skills/instruction-builder-agent/test/` (11 .md files, no test-cases.json or test-results.json)
Destination: `plugin/tests/skills/instruction-builder-agent/first-use/`

Files:
- design-output-includes-required-sections.md
- step1-goal-decomposes-to-ordered-steps.md
- step44-accept-decision-skips-investigation.md
- step44-contradictory-evidence-concludes-inconclusive.md
- step44-get-history-invoked-with-correct-args.md
- step44-no-contamination-concludes-genuine-defect.md
- step44-reject-decision-starts-investigation.md
- step44-reject-investigation-report-no-duplicate-summary.md
- step44-session-analyzer-error-continues-investigation.md
- step44-shared-subagent-detects-contamination.md
- step44-thinking-block-recorded-in-report.md

## Jobs

### Job 1

- In `plugin/skills/instruction-builder-agent/first-use.md`, make the following targeted edits (all
  edits use worktree-relative paths via Read+Edit tools from the worktree):

  **Edit 1 — TEST_DIR computation (lines 233–239):**
  Replace the old 3-line block:
  ```
  At the start of Step 4, compute `TEST_DIR` as the `test/` subdirectory adjacent to the
  skill file being improved:
  ```bash
  # SKILL_TEXT_PATH is worktree-relative (e.g., plugin/skills/foo/SKILL.md)
  SKILL_ABS_PATH="${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}"
  TEST_DIR="$(dirname "$SKILL_ABS_PATH")/test"
  ```
  ```
  With:
  ```
  At the start of Step 4, compute `TEST_DIR` as the corresponding directory under `plugin/tests/`:
  ```bash
  # SKILL_TEXT_PATH is worktree-relative (e.g., plugin/skills/foo/SKILL.md)
  SKILL_ABS_PATH="${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}"
  SKILL_RELATIVE="${SKILL_TEXT_PATH#plugin/}"
  SKILL_RELATIVE_NO_EXT="${SKILL_RELATIVE%.*}"
  TEST_DIR="${CLAUDE_PROJECT_DIR}/plugin/tests/${SKILL_RELATIVE_NO_EXT}"
  ```
  ```

  **Edit 2 — Artifact location description (line 252–253):**
  Replace:
  `**Artifact location:** \`TEST_DIR\` is the stable \`test/\` directory adjacent to the skill`
  With:
  `**Artifact location:** \`TEST_DIR\` is the stable directory under \`plugin/tests/\` corresponding to the skill file`

  **Edit 3 — In-place mode check (line 1134):**
  Replace:
  `` `<skill-dir>/test/test-results.json` exists (where `<skill-dir>` is the directory containing the target ``
  `` skill file). ``
  With:
  `` `${TEST_DIR}/test-results.json` exists (where `TEST_DIR` is `${CLAUDE_PROJECT_DIR}/plugin/tests/<path-to-file-without-extension>/`). ``

  Note: line 1135 "If no prior test is found..." still applies; only the path expression changes.

  **Edit 4 — Batch mode file filter (line 1153–1154):**
  Replace:
  `If the caller passes a directory path (or \`--batch <dir>\`) instead of a single file, enumerate all \`SKILL.md\``
  `and \`first-use.md\` files under the directory recursively. Apply the single-skill workflow to each file.`
  With:
  `If the caller passes a directory path (or \`--batch <dir>\`) instead of a single file, enumerate all \`.md\``
  `files under the directory recursively. Apply the single-skill workflow to each file.`

  **Edit 5 — Step 8 SKILL_DIR derivation and example (lines 1334–1341):**
  Replace the block:
  ```
  Design test cases following the organic standard. Derive `SKILL_DIR` from `SKILL_TEXT_PATH` (set in
  Step 4) using the directory component of the path:
  ```bash
  SKILL_DIR=$(dirname "${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}")
  ```
  For example, if `SKILL_TEXT_PATH` is `plugin/skills/my-skill/first-use.md`, then
  `SKILL_DIR` is `${CLAUDE_PROJECT_DIR}/plugin/skills/my-skill/`. The test-cases file will be written to
  `${SKILL_DIR}/test/test-cases.json`.
  ```
  With:
  ```
  Design test cases following the organic standard. Use `TEST_DIR` (already computed in Step 4) as the
  test artifacts directory. For example, if `SKILL_TEXT_PATH` is `plugin/skills/my-skill/first-use.md`, then
  `TEST_DIR` is `${CLAUDE_PROJECT_DIR}/plugin/tests/skills/my-skill/first-use`. The test-cases file will be
  written to `${TEST_DIR}/test-cases.json`.
  ```

  **Edit 6 — Create and write test-cases.json (lines 1368–1369):**
  Replace:
  `Create \`${SKILL_DIR}/test/\` if it does not exist, then write the test cases to`
  `` `${SKILL_DIR}/test/test-cases.json`. Follow the format defined in `testing.md`. ``
  With:
  `Create \`${TEST_DIR}/\` if it does not exist, then write the test cases to`
  `` `${TEST_DIR}/test-cases.json`. Follow the format defined in `testing.md`. ``

  **Edit 7 — empirical-test-runner --config (line 1378):**
  Replace:
  `  --config "${SKILL_DIR}/test/test-cases.json" \`
  With:
  `  --config "${TEST_DIR}/test-cases.json" \`

  **Edit 8 — git add in Step 8.5 (line 1400):**
  Replace:
  `git add "${SKILL_DIR}/test/test-cases.json"`
  With:
  `git add "${TEST_DIR}/test-cases.json"`

  **Edit 9 — Related Concepts (lines 1444–1447):**
  Replace:
  `- **Behavioral test cases**: SPRT calibration test cases for this skill are stored in`
  `  \`plugin/skills/instruction-builder-agent/test/\` (one \`.md\` file per test case). Test results are stored in`
  `  \`plugin/skills/instruction-builder-agent/test/test-results.json\` (skill_hash, model, session_id, timestamp,`
  `  overall_decision, and per-case SPRT data).`
  With:
  `- **Behavioral test cases**: SPRT calibration test cases for this skill are stored in`
  `  \`plugin/tests/skills/instruction-builder-agent/first-use/\` (one \`.md\` file per test case). Test results are stored in`
  `  \`plugin/tests/skills/instruction-builder-agent/first-use/results.json\` (skill_hash, model, session_id, timestamp,`
  `  overall_decision, and per-case SPRT data).`

  **Edit 10 — Verification checklist item (line 1460):**
  Replace:
  `- [ ] Test artifacts directory is the skill-adjacent \`<skill-dir>/test/\` (derived from dirname of SKILL_TEXT_PATH)`
  With:
  `- [ ] Test artifacts directory is \`plugin/tests/<path-to-file-without-extension>/\` (derived from SKILL_TEXT_PATH)`

  **Edit 11 — Verification checklist item (line 1496):**
  Replace:
  `` - [ ] Step 6 in-place mode verifies prior test existence before skipping Steps 1-4 (checking `<skill-dir>/test/test-results.json`) ``
  With:
  `` - [ ] Step 6 in-place mode verifies prior test existence before skipping Steps 1-4 (checking `${TEST_DIR}/test-results.json`) ``

  **Edit 12 — Verification checklist item (line 1528):**
  Replace:
  `` - [ ] `test-cases.json` and `test-results.json` are committed directly to `<skill-dir>/test/` after SPRT completes (no separate persist step) ``
  With:
  `` - [ ] `test-cases.json` and `test-results.json` are committed directly to `${TEST_DIR}/` after SPRT completes (no separate persist step) ``

- Relocate test files using `git mv`:
  ```bash
  mkdir -p plugin/tests/skills/instruction-builder-agent/first-use
  git mv plugin/skills/instruction-builder-agent/test/design-output-includes-required-sections.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step1-goal-decomposes-to-ordered-steps.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-accept-decision-skips-investigation.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-contradictory-evidence-concludes-inconclusive.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-get-history-invoked-with-correct-args.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-no-contamination-concludes-genuine-defect.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-reject-decision-starts-investigation.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-reject-investigation-report-no-duplicate-summary.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-session-analyzer-error-continues-investigation.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-shared-subagent-detects-contamination.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  git mv plugin/skills/instruction-builder-agent/test/step44-thinking-block-recorded-in-report.md \
    plugin/tests/skills/instruction-builder-agent/first-use/
  ```
  After moving all files, remove the now-empty source directory:
  ```bash
  rmdir plugin/skills/instruction-builder-agent/test
  ```

- Update `index.json`: set `"status": "closed"` and `"progress": 100`.
  The index.json is at:
  `.cat/issues/v2/v2.1/revise-instruction-builder-file-targets/index.json`
  (worktree-relative path inside the worktree)

- Update `STATE.md`: set `Status: closed`.
  The STATE.md is at:
  `.cat/issues/v2/v2.1/revise-instruction-builder-file-targets/STATE.md`
  (worktree-relative path inside the worktree)

- Commit all changes together (the `git mv` operations auto-stage; the remaining files need explicit staging):
  ```bash
  git add plugin/skills/instruction-builder-agent/first-use.md
  git add .cat/issues/v2/v2.1/revise-instruction-builder-file-targets/index.json
  git add .cat/issues/v2/v2.1/revise-instruction-builder-file-targets/STATE.md
  git commit -m "feature: revise instruction-builder to generalize file targets and relocate test directory to plugin/tests/"
  ```

### Job 2 — E2E Test: instruction-builder on plugin/rules/skill-models.md

**Step 1:** Verify that `plugin/rules/skill-models.md` exists.
  ```bash
  test -f plugin/rules/skill-models.md || echo "ERROR: File not found"
  ```

**Step 2:** Invoke the Skill tool directly to test instruction-builder generalization on a non-skill context-loaded file.

  The implementation agent will invoke:
  ```
  skill: "cat:instruction-builder-agent"
  args: "06db371a-8e40-47ba-8f21-ddb555fbddb0 plugin/rules/skill-models.md"
  ```

  This Skill tool invocation:
  - Passes the cat_agent_id as the first argument (required by instruction-builder Skill definition)
  - Passes `plugin/rules/skill-models.md` as the file target (a non-skill file to verify generalization)
  - Allows instruction-builder to compute TEST_DIR using the new formula (lines 233–238 in Job 1)

**Step 3:** Verify the TEST_DIR path formula correctness after skill invocation.

  The instruction-builder Skill tool will create a TEST_DIR directory. Verify that:
  1. The directory path is `plugin/tests/rules/skill-models/` (not `plugin/rules/skill-models/test/`)
  2. The path was computed from the target file `plugin/rules/skill-models.md` using:
     - Strip `plugin/` prefix: `rules/skill-models.md`
     - Remove file extension: `rules/skill-models`
     - Prepend `plugin/tests/`: `plugin/tests/rules/skill-models/`

**Step 4:** Verify test artifacts are created at the correct location.

  After instruction-builder completes, check that one or more of the following exist:
  - `plugin/tests/rules/skill-models/test-cases.md` (or multiple `.md` test case files)
  - `plugin/tests/rules/skill-models/test-cases.json` (SPRT test config)
  - `plugin/tests/rules/skill-models/test-results.json` (SPRT test results)

  If test artifacts appear at any other location (e.g., `plugin/rules/skill-models/test/`), the E2E criterion fails
  and the issue must be debugged.

**Step 5:** Commit the E2E test results.

  After verifying Step 4, commit the test artifacts directory:
  ```bash
  git add plugin/tests/rules/skill-models/
  git commit -m "planning: add E2E test results for instruction-builder generalization (iteration 2)"
  ```

  (If no new files are created by the E2E test, the commit message can note that the skill invocation was successful
  and the TEST_DIR path formula was verified.)
