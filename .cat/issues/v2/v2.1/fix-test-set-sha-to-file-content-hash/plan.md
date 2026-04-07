# Plan

## Goal

Clarify test_set_sha as SHA-256 of skill file content and require SPRT re-run on any skill file update including compression/rewrite passes.

## Pre-conditions

(none)

## Post-conditions

- [ ] `test_set_sha` is documented and stored as SHA-256 (64 hex chars) of the skill file content, not a git commit SHA
- [ ] `detect-changes` redesigned: first argument is SHA-256 content hash; uses equality check (`current_sha != stored_sha` → `skill_changed=true`); prior git content retrieval (`git show`) removed
- [ ] SHA format validation in `detectChanges()` updated from `[0-9a-f]{7,40}` to `[0-9a-f]{64}`
- [ ] `instruction-builder-agent/first-use.md` updated: `INSTRUCTION_DRAFT_SHA` computed as `sha256(skill_file)` not a git commit SHA; compression/rewrite passes explicitly require full SPRT re-run
- [ ] `batch-write-agent/test-results.json` `test_set_sha` updated to actual SHA-256 of `first-use.md`
- [ ] Tests passing, no regressions

## Research Findings

### Current Implementation

**`InstructionTestRunner.detectChanges()` (lines 309–468)**
- First argument `old_skill_sha` validated as `[0-9a-f]{7,40}` (git commit SHA format)
- Retrieves old skill content via `git rev-parse --show-toplevel` + `git show <sha>:<relpath>`
- Writes old content to temp file, parses both old and new skill files
- Compares frontmatter SHA-256 and body diff to produce `frontmatter_changed`, `body_changed`, `changed_ranges`
- Calls `hasTransitiveDependencyChanged()` which runs `git diff --name-only <sha> -- <skillDir>`
  to detect changes to sibling `.md` files (companion files like `first-use.md`)
- Returns JSON with fine-grained fields: `skill_changed`, `frontmatter_changed`, `body_changed`,
  `changed_ranges`, `all_test_case_ids`, `rerun_test_case_ids`, `carryforward_test_case_ids`,
  and `requires_unit_mapping` / `semantic_units_path_hint`

**`sha256File()` already exists** in `InstructionTestRunner` (line 1354): computes SHA-256 hex digest of a file.

**`instruction-builder-agent/first-use.md`**
- Line ~303: "Store the SHA as `INSTRUCTION_DRAFT_SHA`" (refers to git commit SHA from subagent result)
- Lines ~576-580: Context derivation code sets `INSTRUCTION_DRAFT_SHA="${DRAFT_COMMIT}"`
- Line ~562-563: Invokes `detect-changes <INSTRUCTION_DRAFT_SHA> <INSTRUCTION_TEXT_PATH> "${TEST_DIR}"`
- Line ~629: Second `detect-changes` invocation with same arg pattern
- Line ~1646: "Store the returned commit SHA as `INSTRUCTION_DRAFT_SHA`" (after improvement commit)
- Line ~1928: Compression commits instruction file with `refactor: accept final compression ...`
  but does NOT explicitly say to recompute `INSTRUCTION_DRAFT_SHA`

**`batch-write-agent/test-results.json`**
- `"test_set_sha": "b40012f59"` — 9-char partial git commit SHA
- SHA-256 of `plugin/skills/batch-write-agent/first-use.md`: `0babde5b6695c94703ce79903a36b3e32858ca30550ac025d3db88d7a0dc8fbd`

### New Design

The new `detectChanges()` compares SHA-256 of the current skill file against the provided SHA-256 hash:
- If hashes match → `skill_changed=false`, all test cases carry forward
- If hashes differ → `skill_changed=true`, all test cases rerun
- No git operations needed; `hasTransitiveDependencyChanged()` removed
- Fine-grained fields (`frontmatter_changed`, `body_changed`, `changed_ranges`, `requires_unit_mapping`) removed
- Simplified output: `skill_changed`, `all_test_case_ids`, `rerun_test_case_ids`, `carryforward_test_case_ids`

**Trade-off**: Companion file changes (e.g., `compression-protocol.md`) are no longer automatically detected,
since only the tracked file's SHA-256 is compared. This is acceptable per the issue scope.

## Jobs

### Job 1

TDD-first implementation in Java and plugin skill updates (all independent files).

- **Write failing tests** for new `detectChanges()` behavior BEFORE modifying production code:
  In `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerTest.java`:
  - Remove all 5 existing `detectChanges*` tests that use git repos and git commit SHAs:
    `detectChangesNoChanges`, `detectChangesFrontmatterChanged`, `detectChangesBodyOnlyChanged`,
    `detectChangesTransitiveDependencyChanged`, `detectChangesNoTransitiveDependencyChange`
  - Add new tests (write failing first, then they pass after production code change):
    - `detectChanges_sha256Match_allCarriedForward()`: Create temp skill file with content,
      compute its SHA-256, call `detectChanges(sha256, path, testDir)` → `skill_changed=false`,
      all test case IDs in `carryforward_test_case_ids`, `rerun_test_case_ids` empty
    - `detectChanges_sha256Mismatch_allRerun()`: Create temp skill file, use a SHA-256 of
      different content (e.g., SHA-256 of empty string = `e3b0c44...` 64-char hex), call
      `detectChanges(wrongSha256, path, testDir)` → `skill_changed=true`,
      all test case IDs in `rerun_test_case_ids`, `carryforward_test_case_ids` empty
    - `detectChanges_invalidSha_shortString_throwsIllegalArgument()`: Call with 9-char hex string
      (old git SHA format) → throws `IllegalArgumentException` with message containing "64"
    - `detectChanges_invalidSha_notHex_throwsIllegalArgument()`: Call with 64-char string
      containing non-hex chars → throws `IllegalArgumentException`
  - Run build: `mvn -f client/pom.xml verify -e` — expect FAILURES (new tests fail, old tests deleted)

- **Update `InstructionTestRunner.detectChanges()` method** in
  `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`:
  - Update Javadoc (lines 309-323): Change description from git-SHA-based comparison to
    SHA-256 content hash comparison. Remove mention of transitive dependencies, `git show`,
    and "changed line ranges". New description:
    ```
    Compares the SHA-256 content hash of the current skill file against the provided hash,
    and partitions test cases into rerun vs carry-forward.
    @param args [old_skill_sha256, new_skill_path, test_dir_path]
    @throws IOException if files cannot be read
    ```
  - Change SHA validation (line 336) from `[0-9a-f]{7,40}` to `[0-9a-f]{64}` (exactly 64 chars).
    Update error message: `"invalid SHA-256 content hash format: '" + oldSha + "'. Expected 64 lowercase hex characters."`
  - Remove all git operations:
    - Lines 347-366: Remove `ProcessRunner.run(..., "git", "rev-parse", "--show-toplevel")` block
    - Lines 356-366: Remove relPath derivation
    - Lines 360-366: Remove `ProcessRunner.run(..., "git", "show", ...)` block
  - Replace old-content temp file logic (lines 368-468) with:
    ```java
    String currentSha = sha256File(newSkillPath);
    boolean skillChanged = !currentSha.equals(oldSha);
    List<String> allTestCaseIds = readAllTestCaseIds(testDirPath);
    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode result = mapper.createObjectNode();
    result.put("skill_changed", skillChanged);
    ArrayNode allIdsArray = mapper.createArrayNode();
    for (String id : allTestCaseIds)
      allIdsArray.add(id);
    result.set("all_test_case_ids", allIdsArray);
    if (skillChanged)
    {
      result.set("rerun_test_case_ids", allIdsArray.deepCopy());
      result.set("carryforward_test_case_ids", mapper.createArrayNode());
    }
    else
    {
      result.set("rerun_test_case_ids", mapper.createArrayNode());
      result.set("carryforward_test_case_ids", allIdsArray.deepCopy());
      result.put("semantic_units_path_hint",
        "Run: skill-test-runner extract-units " + args[1]);
    }
    return compactJson(result);
    ```
  - Remove `repoRoot`, `relPath` variables and their usages
  - Remove `oldTempFile`, `newTempFile`, `oldBodyFile`, `newBodyFile` and their try-finally blocks
  - Remove `frontmatterChanged`, `bodyChanged`, `transitiveDependencyChanged`, `changedRanges` variables
  - Remove `hasTransitiveDependencyChanged()` call

- **Remove `hasTransitiveDependencyChanged()` method** (lines 485-508) from `InstructionTestRunner.java`

- **Update `plugin/skills/instruction-builder-agent/first-use.md`**:
  1. Lines ~303-308: Change the paragraph that starts "The subagent returns `{"status": "success",
     "commit_sha": "<SHA>"}`. Store the SHA as `INSTRUCTION_DRAFT_SHA`" to:
     "The subagent returns `{"status": "success", "commit_sha": "<SHA>"}`. Compute `INSTRUCTION_DRAFT_SHA`
     as the SHA-256 content hash of `INSTRUCTION_TEXT_PATH`:
     ```bash
     INSTRUCTION_DRAFT_SHA=$(sha256sum "${INSTRUCTION_TEXT_PATH}" | awk '{print $1}')
     ```
     The instruction text is now on disk and committed, so subagents can read it via `cat <INSTRUCTION_TEXT_PATH>`."
     (Remove the `git show <SHA>:<INSTRUCTION_TEXT_PATH>` reference since git SHA is no longer tracked.)
  2. Lines ~576-580: Retain the `DRAFT_COMMIT` and `INSTRUCTION_TEXT_PATH` derivation lines (still
     needed to locate the instruction file). Only change the last line:
     - Keep: `DRAFT_COMMIT=$(git log --oneline --all | grep "write instruction draft" | head -1 | awk '{print $1}')`
     - Keep: `INSTRUCTION_TEXT_PATH=$(git show "${DRAFT_COMMIT}" --name-only --format='' | grep -v '^$' | head -1)`
     - Change: `INSTRUCTION_DRAFT_SHA="${DRAFT_COMMIT}"` →
       `INSTRUCTION_DRAFT_SHA=$(sha256sum "${INSTRUCTION_TEXT_PATH}" | awk '{print $1}')`
     Update the comment on the last line from `# Compute INSTRUCTION_DRAFT_SHA` to
     `# Compute INSTRUCTION_DRAFT_SHA as SHA-256 of the current instruction file content`.
  3. Line ~1646: Change "Store the returned commit SHA as `INSTRUCTION_DRAFT_SHA` before returning
     to Step 6." to "Compute `INSTRUCTION_DRAFT_SHA` as SHA-256 of the updated
     `INSTRUCTION_TEXT_PATH` and store it before returning to Step 6:
     ```bash
     INSTRUCTION_DRAFT_SHA=$(sha256sum "${INSTRUCTION_TEXT_PATH}" | awk '{print $1}')
     ```"
     Also update the continuation shortcut paragraph at lines ~1648-1653 that says
     "committed with new INSTRUCTION_DRAFT_SHA abc123" — change the example value
     `abc123` to a description of the new format, e.g.:
     "committed with new INSTRUCTION_DRAFT_SHA <64-char-sha256>"
     to clarify that `INSTRUCTION_DRAFT_SHA` is now a 64-character SHA-256 hex string, not a
     short git commit SHA.
  4. Lines ~631-648 (the output fields list and test-case-selection table):
     - Update the `skill_changed` field description: change from "whether the skill or any of its
       transitive dependencies changed since the last SPRT run. Transitive dependencies are all
       `.md` files co-located with the skill file..." to "whether the skill file content changed —
       `true` when the SHA-256 of the current file differs from the provided hash."
     - Remove `prior_test_case_ids` from the output fields list (this field no longer exists).
     - Add `rerun_test_case_ids` and `carryforward_test_case_ids` to the output fields list:
       - `rerun_test_case_ids`: test case IDs that must re-run (all IDs when `skill_changed=true`,
         empty when `skill_changed=false`)
       - `carryforward_test_case_ids`: test case IDs that carry forward from prior results (all IDs
         when `skill_changed=false`, empty when `skill_changed=true`)
     - Update the three-row test-case-selection table (currently references `prior_test_case_ids`).
       Replace it with a two-row table:
       | Condition | Which test cases to run |
       |-----------|------------------------|
       | `skill_changed: true` | **All** test cases (full SPRT re-run). |
       | `skill_changed: false` | Carry all results forward. Skip re-test entirely. |
       (The "new test cases exist" row is removed because this detection required `prior_test_case_ids`
       which is no longer output by `detect-changes`.)
  5. After compression commit (around line 1928, the `refactor: accept final compression` commit):
     Add a sentence: "Recompute `INSTRUCTION_DRAFT_SHA` as SHA-256 of the compressed file —
     any content change requires a fresh SHA:
     ```bash
     INSTRUCTION_DRAFT_SHA=$(sha256sum "${INSTRUCTION_TEXT_PATH}" | awk '{print $1}')
     ```
     This ensures the next `detect-changes` call correctly detects that the skill changed
     (full SPRT re-run required)."

- **Update `plugin/tests/skills/batch-write-agent/first-use/test-results.json`**:
  Change `"test_set_sha": "b40012f59"` to
  `"test_set_sha": "0babde5b6695c94703ce79903a36b3e32858ca30550ac025d3db88d7a0dc8fbd"`
  (SHA-256 of `plugin/skills/batch-write-agent/first-use.md`)

- **Run the full build** after all changes:
  ```bash
  mvn -f client/pom.xml verify -e
  ```
  All tests must pass. Fix any linter errors (Checkstyle, PMD) before committing.

- **Commit** all changes (client Java + plugin skill + test data) in a single commit:
  ```
  bugfix: fix detect-changes to use SHA-256 content hash instead of git commit SHA
  ```
  (Commit type `bugfix:` because it fixes a bug where git SHA was used as a proxy for file
  identity, which could miss changes and used an incorrect format for test_set_sha.)

- **Update index.json**: status=closed, progress=100%

## Commit Type

`bugfix:` (Java client + plugin skill files + test data all relate to the same fix)
