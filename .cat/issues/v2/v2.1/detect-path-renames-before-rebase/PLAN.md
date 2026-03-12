# Plan: detect-path-renames-before-rebase

## Goal

Add pre-rebase path consistency validation to `cat:git-rebase-agent` to detect renamed paths (e.g., `.claude/cat` →
`.cat`) before initiating a rebase, failing fast with a clear error message instead of discovering conflicts
mid-rebase. Two categories of problems must be detected:

1. **Tracked-path renames:** Files exist at paths on the current branch that no longer exist on the target branch
   due to a rename/move (e.g., `.claude/cat/` was renamed to `.cat/` on the target).
2. **Content references:** File content on the current branch contains string patterns matching paths that were
   renamed on the target branch (e.g., a skill file contains the string `.claude/cat` after the target has moved
   those files to `.cat/`).

## Research Findings

### Current flow in `GitRebase.java`

The `execute()` method in `GitRebase.java` flows:
1. Validate target branch is non-empty
2. Detect fork point via `git merge-base --fork-point`
3. Create timestamped backup branch
4. Capture pre-rebase untracked files
5. Attempt rebase with `git rebase --onto <target> <forkPoint>`
6. Verify tree state (no unexpected modifications)
7. Delete orphaned files
8. Count commits rebased
9. Delete backup
10. Return OK JSON

The new validation must be inserted **before Step 3** (before the backup is created, so that if we fail early, no
cleanup is required). The validation must be purely read-only (no git state changes).

### How to detect tracked-path renames

Use `git diff --name-status --diff-filter=R <mergeBase> <targetBranch>` to find files renamed on the target branch
since the common ancestor. Each `R` line has format:
```
R100    old/path    new/path
```
Then check if the current branch still has any tracked file at `old/path`:
```bash
git ls-files --error-unmatch old/path
```
If it exists on current branch but target has renamed it, we have a path conflict.

### How to detect content references

After finding renamed path pairs `(oldPath → newPath)` from the diff above, search all tracked files on the current
branch for content containing the old path string. Use `git grep -l <oldPath>` to find files with references. Report
each file that still references the old path.

### Where to insert in the code

New method `validatePathConsistency(String targetBranch, String mergeBase)` called from `execute()` after Step 1
(target branch validation) but before Step 3 (backup creation). The method:
- Returns `null` on success (proceed with rebase)
- Returns an error JSON string on failure (caller returns it immediately)

### JSON output for validation failure

Reuse `buildErrorJson(message, null, null)` with `backup_branch: null` since no backup was created yet. The status
will be `ERROR`. The message must clearly identify:
- Which paths on the current branch have tracking issues
- Which content references need updating

### Test structure

Existing `GitRebaseTest.java` at
`client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java`. New tests go in the same file, using the
same pattern of `TestUtils.createTempGitRepo()` for isolation.

### Content reference search scope

Search only tracked files (files known to git). Untracked files are not part of the rebase so their content
references are irrelevant.

### Similarity threshold for R entries

`git diff --diff-filter=R` uses a default rename similarity threshold of 50%. The same threshold applies here.
Use `--find-renames=50%` explicitly so the behavior is documented.

## Affected Files

### Implementation changes

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java` — add `validatePathConsistency()` and
  call it from `execute()` before backup creation

### New test file

- No new test file needed; add test methods to the existing
  `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java`

### Skill documentation update

- `plugin/skills/git-rebase-agent/first-use.md` — add a section explaining the new path rename validation behavior
  so agents know what causes pre-rebase errors

## Execution Steps

### Step 1: Write failing tests in GitRebaseTest.java

Add the following test methods to the existing `GitRebaseTest.java`. Tests must:
- Use `TestUtils.createTempGitRepo()` for isolation
- Create a target branch with a renamed path (via `git mv`)
- Create a feature branch with the old path tracked AND/OR file content referencing the old path
- Run `GitRebase.execute()` and assert the result has `status=ERROR` and a message mentioning the affected paths

**Test method 1 — tracked path rename detection:**
- Set up a repo with a file at `.claude/cat/config.json`
- Create target branch `main`, commit the file there
- Rename via `git mv .claude/cat .cat` on the target branch
- Create feature branch from before the rename that still has `.claude/cat/config.json` tracked
- Call `new GitRebase(scope, workingDirectory).execute("main")`
- Assert `status=ERROR`
- Assert message contains `.claude/cat` (the old path) and indicates a tracked path conflict

**Test method 2 — content reference detection:**
- Set up a repo with a file `skill.md` containing the text `.claude/cat`
- Create target branch `main`, rename `.claude/cat/` to `.cat/` on main
- Create feature branch from before the rename, with `skill.md` containing `.claude/cat` still committed
- Call `new GitRebase(scope, workingDirectory).execute("main")`
- Assert `status=ERROR`
- Assert message contains `skill.md` and `.claude/cat`

**Test method 3 — no false positive when no renames:**
- Normal repo with no renames on target branch
- Feature branch adds a new file
- Assert `status=OK` (rebase proceeds normally)

**Test method 4 — no false positive when references are updated:**
- Target branch renames `.claude/cat` to `.cat`
- Feature branch has `skill.md` that has already been updated to reference `.cat` (not `.claude/cat`)
- Assert `status=OK` (no content reference violation)

### Step 2: Run tests and confirm they fail

```bash
mvn -f /workspace/client/pom.xml test -Dtest=GitRebaseTest 2>&1 | tail -40
```

All new test methods should fail (compilation error or assertion failure) because the validation is not yet
implemented.

### Step 3: Implement validatePathConsistency() in GitRebase.java

Add a new private method immediately before the `execute()` method:

```java
/**
 * Validates that the current branch's tracked paths and file content are consistent with the target branch.
 * <p>
 * Detects two categories of problems:
 * <ol>
 *   <li>Tracked-path renames: files on the current branch whose paths were renamed on the target branch
 *       since the merge base. These would cause confusion or conflicts during rebase.</li>
 *   <li>Content references: tracked files on the current branch that contain string references to paths
 *       that were renamed on the target branch since the merge base.</li>
 * </ol>
 * Returns {@code null} if no problems are found (rebase may proceed). Returns an error JSON string if
 * any problem is found (caller must return it immediately without creating a backup).
 *
 * @param targetBranch the branch to rebase onto
 * @param mergeBase    the common ancestor commit hash between the current branch and target branch
 * @return {@code null} on success, or an ERROR JSON string on failure
 * @throws IOException if git commands fail
 */
private String validatePathConsistency(String targetBranch, String mergeBase) throws IOException
```

**Implementation details:**

1. Run `git diff --name-status --diff-filter=R --find-renames=50% <mergeBase> <targetBranch>` to find all renamed
   files on the target branch since the merge base.
2. Parse each `R` line to extract `(oldPath, newPath)` pairs. Lines have format:
   `R<score>\t<oldPath>\t<newPath>` (tab-separated with score immediately after R, no space).
3. For each `(oldPath, newPath)` pair:
   a. Run `git ls-files -- <oldPath>` (without `--error-unmatch` to avoid exceptions). If output is non-empty, the
      current branch still tracks this old path. Add `oldPath` to `trackedConflicts` list.
   b. Run `git grep -l -- <oldPath>` on the current branch. Any output lines are files containing old-path
      references. Add `(oldPath, [files])` pairs to `contentConflicts` map.
4. If both `trackedConflicts` and `contentConflicts` are empty, return `null`.
5. Otherwise, build a clear error message listing:
   - Section "Tracked path conflicts (old path still tracked on current branch):" with each conflicting old path
   - Section "Content reference conflicts (files still referencing renamed paths):" with each file and the old path
     it references
6. Return `buildErrorJson(message, null, null)`.

**Calling the new method from execute():**

In `execute()`, after Step 1 (target branch validation) and BEFORE Step 2 (detectForkPoint), add:

```java
// Step 1.5: Validate path consistency before creating backup
String mergeBaseForValidation = detectMergeBase(targetBranch);
if (mergeBaseForValidation != null)
{
  String validationError = validatePathConsistency(targetBranch, mergeBaseForValidation);
  if (validationError != null)
    return validationError;
}
```

Where `detectMergeBase()` is a thin private method:
```java
/**
 * Returns the merge-base commit hash, or {@code null} if the branches have no common ancestor.
 *
 * @param targetBranch the target branch name
 * @return the merge-base commit hash, or {@code null}
 * @throws IOException if the git command fails unexpectedly
 */
private String detectMergeBase(String targetBranch) throws IOException
{
  ProcessRunner.Result result = runGit("merge-base", targetBranch, "HEAD");
  if (result.exitCode() != 0)
    return null;
  return result.stdout().strip();
}
```

Note: `detectForkPoint()` already calls `merge-base` internally, so `detectMergeBase()` adds one extra git call. This
is acceptable since the validation runs only once before backup creation and the cost is negligible vs. a failed
mid-rebase recovery.

**git grep behavior:** `git grep -l -- <pattern>` exits with code 1 when no matches are found (not an error), so the
exit code must not be treated as a failure. Only exit code > 1 indicates a real error.

**Edge case — partial path matches:** Use `--` separator in both `git ls-files` and `git grep` calls to prevent
path ambiguity. For content references, use the full old path string (e.g., `.claude/cat`) as the pattern. This may
match substrings of longer paths (e.g., `.claude/category`). This is intentional and errs on the side of caution —
false positives are preferable to missed references. The error message will show the exact file, allowing the agent to
verify before taking action.

### Step 4: Run tests and confirm they pass

```bash
mvn -f /workspace/client/pom.xml test -Dtest=GitRebaseTest 2>&1 | tail -40
```

All tests (existing and new) must pass.

### Step 5: Run full test suite

```bash
mvn -f /workspace/client/pom.xml test 2>&1 | tail -40
```

All tests must pass with no regressions.

### Step 6: Update git-rebase-agent skill documentation

Edit `plugin/skills/git-rebase-agent/first-use.md` to add a new section after "PROJECT.md Merge Policy Check" titled
"Pre-Rebase Path Consistency Validation":

The section must explain:
- The validation runs automatically before backup creation
- What triggers an ERROR status before rebase starts
- The two categories: tracked-path renames and content references
- What the error message contains and what action to take
- Example scenario: `.claude/cat` → `.cat` directory rename

### Step 7: Update STATE.md and commit

Commit all changes with message:
```
feature: add pre-rebase path rename detection to git-rebase-agent
```

Files to commit:
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java`
- `plugin/skills/git-rebase-agent/first-use.md`
- `.cat/issues/v2.1/detect-path-renames-before-rebase/STATE.md`

## Success Criteria

- [ ] When the current branch has a file tracked at a path that was renamed on the target branch,
      `execute()` returns status `ERROR` before creating a backup, with a message naming the conflicting paths
- [ ] When the current branch has tracked files whose content references string patterns matching renamed paths
      on the target branch, `execute()` returns status `ERROR` before creating a backup, with a message naming
      the files and the old path pattern
- [ ] When no path renames exist, `execute()` returns status `OK` (no regression)
- [ ] When renames exist on the target but the current branch has already updated its content references,
      `execute()` returns status `OK` (no false positive)
- [ ] All new tests pass: tracked-path conflict, content reference conflict, no-rename no-op, updated-reference no-op
- [ ] Full test suite passes with no regressions (`mvn -f client/pom.xml test` exits 0)
- [ ] `git-rebase-agent/first-use.md` documents the new validation behavior
