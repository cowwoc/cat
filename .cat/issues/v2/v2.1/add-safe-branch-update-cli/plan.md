# Goal

Add a Java CLI tool (`update-branch`) that wraps dangerous git branch pointer update operations and enforces
fast-forward preconditions before executing. Replace all `git update-ref` usage in plugin skill files with
calls to this guarded tool.

## Background

The `git update-ref refs/heads/BRANCH HASH` command force-updates a branch pointer without any safety checks.
If the branch has diverged from the target hash (i.e., the hash is not a descendant of the current branch
tip), `update-ref` silently overwrites the branch, losing commits. This happened in session
2823e46b-6087-4389-a775-aa1cbaf7c2e8 when a `merge-base --is-ancestor` check returned non-zero (not fast-forward)
but `update-ref` ran anyway in the same chained `&&` command sequence, losing three learning-record commits on v2.1.

The Java tool enforces the fast-forward invariant at the CLI level, making it impossible to lose commits without
an explicit `--force` flag.

## Acceptance Criteria

- [ ] `update-branch` Java CLI enforces `git merge-base --is-ancestor OLD_TIP NEW_TIP` check before updating any
      branch ref; exits non-zero with a clear error if the update is not a fast-forward
- [ ] All `git update-ref` patterns in plugin skill `.md` files are replaced with calls to `update-branch`
- [ ] Java unit tests cover: fast-forward allowed, non-fast-forward rejected, explicit `--force` bypass,
      branch does not exist (new branch allowed), invalid arguments
- [ ] All `git push --force` patterns in plugin skill `.md` files that update branch refs locally are replaced
      with the guarded Java CLI (or documented as intentional with `--force` flag)

## Implementation Plan

### Wave 1: Java CLI tool

1. Create `client/src/main/java/io/github/cowwoc/cat/client/UpdateBranch.java` implementing `main(String[])`:
   - Parse arguments: `update-branch [--force] <branch> <new-tip-hash>`
   - If `--force` is absent: run `git merge-base --is-ancestor <current-tip> <new-tip-hash>`; exit 1 with error
     if not fast-forward
   - Run `git update-ref refs/heads/<branch> <new-tip-hash>`
   - Output success/failure as plain text (not JSON — this is a skill CLI tool)

2. Wire the tool into the jlink manifest so it ships as `update-branch` launcher

3. Write TestNG unit tests in `client/src/test/java/io/github/cowwoc/cat/client/UpdateBranchTest.java`:
   - Fast-forward case: allowed
   - Non-fast-forward case: rejected with descriptive error
   - `--force` flag: bypasses fast-forward check
   - New branch (no prior tip): allowed without fast-forward check
   - Missing arguments: exits non-zero with usage message

### Wave 2: Update skill files

4. Search all `plugin/skills/**/*.md` and `plugin/commands/**/*.md` for `git update-ref` usage

5. Replace each occurrence with the equivalent `update-branch` call, preserving the same branch and hash
   arguments

6. Search the same files for `git push --force` patterns that update local branch refs (e.g.,
   `git push . branch:target --force`); replace with `update-branch --force` or document as intentional

7. Run `mvn -f client/pom.xml test` to verify all tests pass

## Effort Estimate

High (new Java CLI tool + test suite + multi-file skill updates)
