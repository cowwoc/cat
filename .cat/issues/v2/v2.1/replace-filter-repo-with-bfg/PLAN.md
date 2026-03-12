# Plan: replace-filter-repo-with-bfg

## Current State

`plugin/skills/git-rewrite-history-agent/first-use.md` documents `git-filter-repo` as the history
rewriting tool and includes a `pip install git-filter-repo` installation step. The SKILL.md
description references git-filter-repo by name.

## Target State

The skill documents **BFG Repo-Cleaner** (`bfg.jar`) as the primary tool. BFG is a Java/Scala JAR
that runs on the JVM, consistent with the project convention that Java is available but Python is
not. For the special case of dropping individual commits (not removing files), the skill directs the
agent to use `git rebase --onto` (pure git, no external tool needed).

## Parent Requirements

None

## BFG Capability Analysis

BFG supports (replacing equivalent git-filter-repo operations):

| Operation | git-filter-repo | BFG equivalent |
|-----------|-----------------|----------------|
| Remove file from all history | `--path file --invert-paths` | `--delete-files file` |
| Remove directory from all history | `--path dir/ --invert-paths` | `--delete-folders dir` |
| Remove large blobs | `--strip-blobs-bigger-than 10M` | `--strip-blobs-bigger-than 10M` |
| Remove text patterns/secrets | `--replace-text expressions.txt` | `--replace-text expressions.txt` |

BFG does NOT support (these use pure git instead):

| Operation | Alternative |
|-----------|-------------|
| Drop specific commits | `git rebase --onto <parent> <commit> <branch>` |
| Rename/move files in history | Not supported — document as out of scope |
| Keep only specific paths | Not supported — document as out of scope |

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — skill is documentation only; no behavior changes
- **Mitigation:** BFG is a well-established tool (maintained through 2025); Java is already required

## Files to Modify

- `plugin/skills/git-rewrite-history-agent/first-use.md` — replace git-filter-repo content with BFG
- `plugin/skills/git-rewrite-history-agent/SKILL.md` — update frontmatter description

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Rewrite `plugin/skills/git-rewrite-history-agent/first-use.md` to document BFG:
  - **Purpose line**: "Safely rewrite git history using BFG Repo-Cleaner, a Java-based alternative
    to git-filter-repo that requires no Python dependency."
  - **Why BFG section**: Replace the "Why git-filter-repo" section with "Why BFG Repo-Cleaner":
    - BFG is Java-based (CAT already requires Java; Python is not available in the plugin runtime)
    - BFG is 10-720x faster than git filter-branch
    - Simple CLI, actively maintained
    - Comparison table: BFG vs git-filter-branch (not vs git-filter-repo)
  - **Installation section**: Replace pip install with:
    ```bash
    # Download BFG JAR (requires Java 11+, which CAT already provides)
    curl -o bfg.jar https://repo1.maven.org/maven2/com/madgag/bfg/1.14.0/bfg-1.14.0.jar
    # Or via Homebrew (macOS/Linux): brew install bfg
    # Verify: java -jar bfg.jar --version
    ```
  - **Safety Pattern section**: Update to BFG's mirror-clone pattern:
    ```bash
    # BFG requires a bare/mirror clone for history rewriting
    git clone --mirror <url> repo.git
    cd repo.git
    # Run BFG operation
    java -jar bfg.jar [options] .
    # Clean up
    git reflog expire --expire=now --all && git gc --prune=now --aggressive
    # Verify, then push
    git push --force
    ```
  - **Common Operations section** — replace with BFG equivalents:
    - Remove a file: `java -jar bfg.jar --delete-files secrets.txt repo.git`
    - Remove a directory: `java -jar bfg.jar --delete-folders vendor repo.git`
    - Remove large files: `java -jar bfg.jar --strip-blobs-bigger-than 10M repo.git`
    - Remove text patterns/secrets: `java -jar bfg.jar --replace-text expressions.txt repo.git`
    - Drop specific commits: use `git rebase --onto <parent-of-commit> <commit> <branch>`
      (pure git — BFG does not support dropping individual commits by hash)
  - **Remove** the "Working on Existing Clone" section (BFG uses mirror clones natively)
  - **Remove** the "Rename/Move Files in History" section (not supported by BFG)
  - **Remove** the "Keep Only Specific Paths" section (not supported by BFG)
  - **Remove** the "Remove a Submodule" section (covered by --delete-folders)
  - **After Rewriting History section**: Keep as-is (still applies)
  - **Verification Checklist**: Keep as-is
  - **Recovery section**: Update to reflect BFG's recovery approach:
    - Before gc/prune: `cd repo.git && git reflog` then `git reset --hard HEAD@{n}`
    - After gc: restore from backup branch or re-clone
  - **When to Use This Skill section**: Keep as-is, remove "Restructuring repository paths"
    and "Splitting a repository" (not BFG use cases)
  - **References section**: Replace git-filter-repo links with BFG links:
    - [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
    - [BFG GitHub](https://github.com/rtyley/bfg-repo-cleaner)
    - [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
  - Files: `plugin/skills/git-rewrite-history-agent/first-use.md`

- Update `plugin/skills/git-rewrite-history-agent/SKILL.md` frontmatter description:
  - Change: `"MANDATORY: Use instead of git filter-branch - safer git-filter-repo with recovery"`
  - To: `"MANDATORY: Use instead of git filter-branch - BFG Repo-Cleaner (Java-based, no Python required)"`
  - Files: `plugin/skills/git-rewrite-history-agent/SKILL.md`

- Commit: `refactor: replace git-filter-repo with BFG Repo-Cleaner in git-rewrite-history-agent`
- Update STATE.md: status=closed, progress=100%

## Post-conditions

- [ ] `first-use.md` contains no reference to `git-filter-repo` or `pip install`
- [ ] `first-use.md` documents BFG installation via JAR download (with Java as prerequisite)
- [ ] All BFG-supported operations have correct `java -jar bfg.jar` command examples
- [ ] Dropping individual commits is documented as a `git rebase --onto` operation
- [ ] Unsupported operations (rename, keep-specific-paths) are removed
- [ ] SKILL.md frontmatter description no longer references git-filter-repo
- [ ] E2E: Invoke `cat:git-rewrite-history-agent` and confirm the skill loads and shows BFG-based instructions
