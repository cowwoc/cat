<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Rewrite History Skill

## Purpose

Safely rewrite git history using git filter-repo, with automatic tool resolution (Python module or
standalone binary). Always use this skill instead of `git filter-branch`.

**Why git filter-repo over alternatives:**

| Feature | git filter-repo | git filter-branch |
|---------|-----------------|-------------------|
| Path filtering | Path-exact (`--path X --invert-paths`) | Filename-only (collision risk) |
| Speed | Fast | Slow |
| Safety | Handles edge cases correctly | Many gotchas |
| Maintenance | Actively maintained (recommended by git) | Deprecated |

**Key advantages:**

- **Path-based filtering**: `--path X --invert-paths` targets specific files or directories by exact path.
  Filename-only tools accidentally remove ALL files with a matching name across all directories — making
  them unsafe for files like `plan.md` that exist in many directories.
- **Actively maintained and recommended by git itself** as the preferred history rewriting tool.
- **No Python requirement**: a standalone binary is downloaded on first use if Python is not installed.

---

## Procedure

### Step 1: Resolve git filter-repo

Before any operation, resolve the tool invocation string:

```bash
FILTER_REPO=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-git-filter-repo.sh")
```

This returns either `python3 -m git_filter_repo` or the path to a cached standalone binary. Always invoke
it via `eval` to handle multi-word invocations correctly:

```bash
eval "$FILTER_REPO" [options]
```

> **Note:** `CLAUDE_PLUGIN_ROOT` must not contain spaces. When `FILTER_REPO` resolves to a bare binary path
> (e.g., `/path/to/git-filter-repo`), `eval` splits on spaces — a path with spaces would be split
> incorrectly. The `python3 -m git_filter_repo` case is unaffected.

To force re-download and re-verification (e.g., after suspected corruption):

```bash
GFR_FORCE_DOWNLOAD=1 FILTER_REPO=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-git-filter-repo.sh")
```

### Step 2: Create a backup branch

git filter-repo modifies the repository in-place. Always create a timestamped backup branch before
rewriting:

```bash
git branch backup-before-rewrite-$(date +%Y%m%d-%H%M%S)-$$-$RANDOM
```

### Step 3: Run git filter-repo

Use `eval "$FILTER_REPO"` in place of `git filter-repo` for all operations. The `--force` flag is required
because the working directory is not a fresh clone.

**Remove a file from all history:**

```bash
eval "$FILTER_REPO" --path secrets.txt --invert-paths --force
```

**Remove a directory from all history:**

```bash
eval "$FILTER_REPO" --path vendor/ --invert-paths --force
```

**Remove large files:**

```bash
eval "$FILTER_REPO" --strip-blobs-bigger-than 10M --force
```

**Replace text patterns (remove secrets by substitution):**

Create `expressions.txt` with lines of the form `old_value==>new_value`, then:

```bash
eval "$FILTER_REPO" --replace-text expressions.txt --force
```

**Drop specific commits:**

git filter-repo does not support dropping individual commits by hash directly. Use the `cat:git-rebase-agent`
skill with `--onto <parent-of-commit> <commit> <branch>` arguments to replay commits after the dropped
commit onto its parent. (MANDATORY: always use `cat:git-rebase-agent` instead of running `git rebase`
directly.)

### Step 4: Verify the result

- [ ] Target files/paths no longer appear in history: `git log --all -- <path>`
- [ ] Commit count matches expectations
- [ ] No unexpected files were removed: `git diff --stat <old-commit>..<new-head>`
- [ ] Build succeeds
- [ ] Tests pass

### Step 5: Propagate the rewritten history

1. **All collaborators must re-clone** or reset their branches — history has been rewritten.
2. **Force push required**: Run `cat:validate-git-safety-agent` first, then:
   ```bash
   git push --force-with-lease origin <branch>
   ```
3. **Update any CI/CD** that caches the old commits.
4. **GitHub/GitLab**: May need to run server-side garbage collection to purge old objects.

### Step 6: Clean up the backup branch

Once history is verified correct, delete the backup branch:

```bash
git branch -D backup-before-rewrite-<timestamp>
```

---

## Recovery

If something goes wrong after running git filter-repo:

- **Restore from backup branch**: `git reset --hard backup-before-rewrite-<timestamp>`
- **Reflog recovery** (if backup branch was not created):
  `git reflog` then `git reset --hard HEAD@{n}`

Since the reflog is not expired or aggressively garbage-collected, old commits remain accessible via
`git reflog` until a manual cleanup is performed.

---

## References

- [git filter-repo documentation](https://htmlpreview.github.io/?https://github.com/newren/git-filter-repo/blob/docs/html/git-filter-repo.html)
- [git filter-repo GitHub](https://github.com/newren/git-filter-repo)
- [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
