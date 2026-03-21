# Plan: fix-autolearn-diff-false-positive

## Problem

The `AutoLearnMistakes` hook triggers a false positive when Bash tool output contains `git diff` unified diff output.
Unified diff hunk headers (e.g., `@@ -1,4 +1,7 @@`) and diff content lines can contain words that match existing
mistake-detection patterns, causing spurious "MISTAKE DETECTED" context injections when no actual mistake occurred.

## Parent Requirements

None

## Reproduction Code

```java
// Simulate running: git diff v2.1..HEAD
// The tool output contains unified diff output with lines like:
//   @@ -1,4 +1,7 @@
//   -old line
//   +new line
// This output passes through filterJsonContent() (which does NOT strip @ lines)
// and may match patterns such as Pattern 4 (merge conflicts: ^=======) or
// Pattern 2 (test_failure: ^\s*\S+\s+\.\.\.\s+FAILED) if diff context lines
// happen to contain matching words.

AutoLearnMistakes handler = new AutoLearnMistakes();
// Construct a Bash tool result whose stdout is a realistic git diff output:
String gitDiffOutput = """
    diff --git a/plugin/skills/learn-agent/SKILL.md b/plugin/skills/learn-agent/SKILL.md
    index abc1234..def5678 100644
    --- a/plugin/skills/learn-agent/SKILL.md
    +++ b/plugin/skills/learn-agent/SKILL.md
    @@ -1,4 +1,7 @@
    -old content
    +new content
    """;
JsonNode result = toolResult(gitDiffOutput);
// handler.check("Bash", result, SESSION_ID, hookData) must NOT trigger any pattern
```

## Expected vs Actual

- **Expected:** `check()` returns `Result.allow()` (no context injection) when tool output is a plain `git diff`
- **Actual:** One or more patterns match diff content lines (e.g. `=======` separator matches Pattern 4 merge-conflict
  regex `^=======$`; diff lines containing `failed`, `error`, `CONFLICT`, `violation` etc. in filenames or context
  match other patterns)

## Root Cause

The `filterGitNoise()` method (lines 339–359) correctly filters lines starting with `+`, `-`, and `@` — but it is only
called for Patterns 7 and 11. All other patterns (1–6, 8–10, 12–16) operate on `filtered` (output of
`filterJsonContent()`), which strips JSONL lines but does NOT strip unified diff hunk headers or diff content lines.

Specifically:
- `filterJsonContent()` does not strip lines starting with `+`, `-`, or `@@`
- Pattern 4 regex `^=======$` matches the `=======` separator that appears in diff output for files with merge markers
- Pattern 2, 3, 6, 7 regexes can match filenames or context lines in diff output that contain words like `failed`,
  `violation`, `error`

**Fix approach:** Extend `filterGitNoise()` — or create a new `filterDiffNoise()` method — that strips unified diff
hunk headers (lines matching `^@@\s+-\d+.*\+\d+.*@@`) and apply it consistently to ALL patterns, not just Patterns 7
and 11.

**Rejected alternative — per-pattern guards:** Adding a per-pattern `if (isDiffOutput(output))` guard duplicates the
detection logic and is error-prone. A single pre-filter applied to all patterns is more robust.

**Rejected alternative — skip Bash tool entirely:** Skipping all Bash tool output would miss real mistakes (build
failures, git errors) that happen in Bash tool calls. The hook must still process Bash output.

## Impact Notes

Overlapping issues exist in v2.1 for other AutoLearnMistakes false positives
(fix-autolearn-java-source-false-positive, fix-autolearn-path-false-positive,
fix-autolearn-pattern11-false-positive, fix-autolearn-pattern2-false-positive). This issue is distinct — it addresses
the git diff `@@` hunk header false positive, not the existing pattern-specific or path-specific false positives.

The fix is additive: it extends `filterGitNoise()` to also cover diff output lines, rather than changing any pattern
regex. This minimizes regression risk to existing true-positive detections.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Existing true-positive patterns (build failures, test failures, merge conflicts in actual source
  files) are identified by error text that does not appear in normal diff output. Filtering diff structure lines
  (`@@`, `+`, `-`, `diff --git`, `index`, `---`, `+++`) does not remove real error text.
- **Mitigation:** Add regression tests for all existing true-positive patterns after applying the fix; run full test
  suite before merging.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` — extend `filterGitNoise()` to
  also strip unified diff structural lines (hunk headers `@@`, file headers `diff --git`, `index`, `---`, `+++`), and
  apply it as a pre-filter for all patterns (not just 7 and 11).
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java` — add regression tests verifying
  that `git diff` output does not trigger any pattern.

## Test Cases

- [ ] `gitDiffHunkHeaderDoesNotTrigger` — Bash output containing only hunk header lines (`@@ -1,4 +1,7 @@`) does not
  trigger any pattern
- [ ] `gitDiffOutputWithEqualsSignDoesNotTrigger` — Bash output containing `=======` in diff context does not trigger
  Pattern 4 (merge_conflict)
- [ ] `gitDiffFileHeaderLinesDoNotTrigger` — Output containing `diff --git a/... b/...`, `index ...`, `--- a/...`,
  `+++ b/...` lines does not trigger any pattern
- [ ] `gitDiffWithFailedInFileNameDoesNotTrigger` — Output like `diff --git a/test_failed.sh b/test_failed.sh` does
  not trigger Pattern 2 (test_failure)
- [ ] `realGitDiffOutputDoesNotTrigger` — A multi-hunk realistic `git diff` output block does not trigger any pattern
- [ ] All existing true-positive tests still pass (no regressions in build_failure, test_failure,
  merge_conflict, edit_failure, etc.)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Extend `filterGitNoise()` in `AutoLearnMistakes.java` to strip unified diff structural lines:
  - Lines matching `^@@\s+-\d+` (hunk headers)
  - Lines starting with `diff --git`
  - Lines starting with `index ` (followed by hash..hash)
  - Lines starting with `--- ` or `+++ ` (file header lines; note: these differ from `+`/`-` content lines by having
    a space after)
  - Note: lines starting with `+` or `-` (content lines, not headers) are already filtered
  - At the top of `detectMistake()`, call `filterGitNoise(filtered)` to get `gitFiltered`, then replace ALL uses of
    `filtered` in patterns 1–6, 8–10, and 12–16 with `gitFiltered`. Patterns 7 and 11 already use `gitFiltered` and
    require no change. The variable `filtered` should only be used as input to `filterGitNoise()`.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`

- Add regression tests to `AutoLearnMistakesTest.java`:
  - `gitDiffHunkHeaderDoesNotTrigger`: stdout = `"@@ -1,4 +1,7 @@"`, tool = `"Bash"`, assert
    `additionalContext` does not contain `"MISTAKE DETECTED"`
  - `gitDiffEqualsLineDoesNotTriggerMergeConflict`: stdout = `"======="`, tool = `"Bash"`, assert
    `additionalContext` does not contain `"merge_conflict"`
  - `gitDiffFileHeaderDoesNotTrigger`: stdout = `"diff --git a/test_failed.sh b/test_failed.sh"`, tool = `"Bash"`,
    assert `additionalContext` does not contain `"MISTAKE DETECTED"`
  - `gitDiffWithFailedInFileNameDoesNotTrigger`: stdout contains `"diff --git a/test_failed.sh b/test_failed.sh\n"`
    followed by a hunk header and a few content lines, tool = `"Bash"`, assert `additionalContext` does not contain
    `"test_failure"`
  - `realGitDiffOutputDoesNotTrigger`: multi-line realistic git diff output (at least two hunks, with `diff --git`,
    `index`, `---`, `+++`, `@@`, `+`, `-` lines), tool = `"Bash"`, assert `additionalContext` does not contain
    `"MISTAKE DETECTED"`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java`

- Run full test suite:
  - `mvn -f client/pom.xml test`
  - All tests must pass

- Update STATE.md to reflect completion:
  - Files: `.cat/issues/v2/v2.1/fix-autolearn-diff-false-positive/STATE.md`

## Post-conditions

- [ ] `AutoLearnMistakes` hook does not inject context when Bash tool runs `git diff` commands (no false positive
  for hunk headers `@@ -1,4 +1,7 @@`)
- [ ] Pattern 4 (merge_conflict) does not trigger on `=======` lines that appear in diff output
- [ ] All five new regression tests pass
- [ ] All pre-existing `AutoLearnMistakesTest` tests still pass
- [ ] E2E: Run `git diff v2.1..HEAD` via Bash tool and confirm no "MISTAKE DETECTED" context is injected
