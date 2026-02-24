# Plan: guard-get-diff-against-large-diffs

## Goal
Prevent `get-diff-output` from crashing with OutOfMemoryError when the diff between HEAD and the detected base branch
is excessively large (e.g., 158K insertions across 1588 files when diffing v2.1 against main).

## Satisfies
- None (infrastructure robustness)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Guard threshold too aggressive could prevent legitimate large diffs from rendering
- **Mitigation:** Use `DiffStats` (already computed before raw diff retrieval) to check file count and insertion count
  before loading the raw diff into memory

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — add guard before `getRawDiff()` call
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` — add test for large diff guard

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add guard using DiffStats before raw diff retrieval** — In `getOutput(Path projectRoot)`, after the existing
   `getDiffStats()` call (line ~427) and before `getRawDiff()` (line ~430), check if `stats.filesChanged() > 500` or
   `stats.insertions() + stats.deletions() > 50000`. If exceeded, return a message telling the agent the diff is too
   large to render inline and to use `git diff <base>..HEAD --stat` instead.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`
2. **Add test** — Create a test that verifies the guard triggers for large diffs and returns a descriptive message
   instead of attempting to render.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`

## Post-conditions
- [ ] `get-diff-output` returns a descriptive error message (not OOM) when diff exceeds the guard threshold
- [ ] Existing tests still pass
