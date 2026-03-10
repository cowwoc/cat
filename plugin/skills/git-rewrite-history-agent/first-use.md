<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Git Rewrite History Skill

**Purpose**: Safely rewrite git history using BFG Repo-Cleaner, a Java-based alternative to git-filter-repo that
requires no Python dependency.

## Why BFG Repo-Cleaner

**Always use BFG instead of `git filter-branch`**. Git itself warns against filter-branch:

> git-filter-branch has a glut of gotchas generating mangled history rewrites.

| Feature | BFG Repo-Cleaner | git-filter-repo | git-filter-branch |
|---------|------------------|-----------------|-------------------|
| Speed | 10-720x faster | Fast | Slow |
| Safety | Handles edge cases | Good | Many gotchas |
| Maintenance | Actively maintained | Actively maintained | Deprecated |
| Runtime | Java (already required by CAT) | Python (not available in CAT) | Bash/shell |

## Installation

BFG is automatically downloaded on first use. The download script at
`${CLAUDE_PLUGIN_ROOT}/scripts/download-bfg.sh` handles downloading, SHA256 verification, and caching.

To manually trigger the download or verify the installation:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/download-bfg.sh"
```

The JAR is cached at `${CLAUDE_PLUGIN_ROOT}/lib/bfg.jar`.

## Safety Pattern: Clone, Clean, Verify

**ALWAYS follow this pattern:**

```bash
# BFG requires a bare clone — create one in a unique temp directory
WORK_DIR=$(mktemp -d)
BARE_REPO="${WORK_DIR}/repo-clean.git"
git clone --mirror . "$BARE_REPO"
cd "$BARE_REPO"
# Run BFG operation
BFG_JAR=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-bfg.sh")
java -jar "$BFG_JAR" [options] .
# Verify history is correct
# Then fetch cleaned refs back into the original repo:
cd -
git fetch "$BARE_REPO" '+refs/heads/*:refs/heads/*'
rm -rf "$WORK_DIR"
```

## Common Operations

**First, ensure BFG is available:**

```bash
BFG_JAR=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-bfg.sh")
```

Then use `java -jar "$BFG_JAR"` in place of `java -jar bfg.jar` for all operations below.

### Remove a File from All History

```bash
java -jar "$BFG_JAR" --delete-files secrets.txt .
```

### Remove a Directory from All History

```bash
java -jar "$BFG_JAR" --delete-folders vendor .
```

### Remove Large Files

```bash
java -jar "$BFG_JAR" --strip-blobs-bigger-than 10M .
```

### Filter by Content (Remove Secrets)

```bash
# Replace text patterns (expressions.txt format: PASSWORD=secret==>PASSWORD=REDACTED)
java -jar "$BFG_JAR" --replace-text expressions.txt .
```

### Drop Specific Commits

BFG does not support dropping individual commits by hash. Use the `cat:git-rebase-agent` skill with
`--onto <parent-of-commit> <commit> <branch>` arguments to replay commits after the dropped commit onto its parent.
(MANDATORY: always use `cat:git-rebase-agent` instead of running `git rebase` directly.)

## After Rewriting History

1. **All collaborators must re-clone** or reset their branches
2. **Force push required**: Run `cat:validate-git-safety-agent` first, then `git push --force-with-lease origin <branch>`
3. **Update any CI/CD** that caches the old commits
4. **GitHub/GitLab**: May need to run garbage collection on server

## Verification Checklist

- [ ] Target files/paths no longer in history: `git log --all -- <path>`
- [ ] Commit count is expected
- [ ] No unexpected files removed: `git diff --stat <old-commit>..<new-head>`
- [ ] Build still works
- [ ] Tests pass

## Recovery

If something goes wrong (before fetching cleaned refs back):

- **Reflog recovery**: `git reflog` then `git reset --hard HEAD@{n}` (run inside the bare clone)
- **Re-clone**: If reflog is insufficient, delete the temp directory and start over from the local repo

Since we do not expire the reflog or run aggressive garbage collection, old commits remain
accessible via `git reflog` indefinitely until a manual cleanup is performed.

## When to Use This Skill

- Removing accidentally committed secrets
- Removing large binary files to reduce repo size
- Removing files or directories from all history

## References

- [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
- [BFG GitHub](https://github.com/rtyley/bfg-repo-cleaner)
- [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
