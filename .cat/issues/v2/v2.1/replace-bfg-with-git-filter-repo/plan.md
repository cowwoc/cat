# Plan: replace-bfg-with-git-filter-repo

## Current State

`plugin/skills/git-rewrite-history-agent/first-use.md` documents BFG Repo-Cleaner (`bfg.jar`) as
the history rewriting tool. BFG's `--delete-files` flag matches by **filename only** (not by path),
causing accidental deletion of all files with matching names across all directories (M562). For
example, `--delete-files PLAN.md` removes every `PLAN.md` in the repository, not just the root-level
one.

`plugin/scripts/download-bfg.sh` downloads the BFG JAR on first use and caches it under
`${CLAUDE_PLUGIN_ROOT}/lib/`.

## Target State

`git filter-repo` replaces BFG as the history rewriting tool. git filter-repo supports
**path-based filtering** (`--path X --invert-paths`), which is precise and correct.

The download strategy:
1. If Python 3 is installed (`python3 --version` succeeds), invoke `git filter-repo` directly
   (it is a single Python script distributed via PyPI and GitHub releases).
2. If Python 3 is not installed, download a pre-built PyInstaller standalone binary from the
   CAT GitHub releases. The binary embeds the Python runtime and requires no Python installation.

A new CI workflow builds the per-platform binaries using PyInstaller and publishes them as
GitHub release assets tagged `git-filter-repo-vX.Y.Z`.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** None — skill documentation changes only; BFG JAR download may be removed
  from `plugin/scripts/` once migration is complete
- **Concerns:** PyInstaller binaries are ~20-30 MB each; CI must build for all supported platforms
- **Mitigation:** Python detection runs first so users with Python pay no download cost; SHA256
  verification ensures binary integrity

## Supported Platforms

Match the existing jlink bundle platforms:
- `linux-x64` (`ubuntu-latest`)
- `linux-aarch64` (`ubuntu-24.04-arm`)
- `macos-x64` (`macos-latest`)
- `macos-aarch64` (`macos-latest`)

## Files to Modify

- `plugin/skills/git-rewrite-history-agent/first-use.md` — Replace all BFG references with
  git filter-repo; add Python detection / binary download pattern
- `plugin/skills/git-rewrite-history-agent/SKILL.md` — Update frontmatter description
- `plugin/scripts/download-git-filter-repo.sh` — New script (mirrors `download-bfg.sh`):
  detects Python 3, falls back to downloading platform binary from GitHub releases
- `.github/workflows/build-git-filter-repo.yml` — New CI workflow: builds standalone binaries
  via PyInstaller for all supported platforms, publishes to GitHub releases

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: CI workflow and download script

- Create `.github/workflows/build-git-filter-repo.yml`:
  - Trigger: push to `main` or `v*` branches, tags `git-filter-repo-v*`, and `workflow_dispatch`
  - Matrix: linux-x64 (ubuntu-latest), linux-aarch64 (ubuntu-24.04-arm), macos-x64 (macos-latest),
    macos-aarch64 (macos-latest)
  - Steps per platform: checkout, set up Python 3, `pip install pyinstaller git-filter-repo`,
    run PyInstaller to produce single-file binary (`pyinstaller --onefile $(which git-filter-repo)`
    or equivalent), rename output to `git-filter-repo-{platform}`, upload as release asset
  - License header required (shell/yaml — use HTML comment for yml since YAML has no comment syntax
    note: YAML files are exempt from license headers per license-header.md)
  - Files: `.github/workflows/build-git-filter-repo.yml`

- Create `plugin/scripts/download-git-filter-repo.sh`:
  - Mirror the structure of `plugin/scripts/download-bfg.sh`
  - First check: `if command -v python3 &>/dev/null && python3 -c "import git_filter_repo" 2>/dev/null`
    → output `python3 /path/to/git-filter-repo` and exit 0
  - Second check: if git-filter-repo script is on PATH (`command -v git-filter-repo`) → output
    the path and exit 0
  - Fall back: detect platform (`uname -s` and `uname -m`), construct GitHub release URL for the
    appropriate binary, download to `${CLAUDE_PLUGIN_ROOT}/lib/git-filter-repo`, chmod +x, verify
    SHA256, output path and exit 0
  - Use placeholder SHA256 values per platform (marked as `# TODO: update after first CI build`)
  - Use placeholder GitHub release URL (marked as `# TODO: update after first CI build`)
  - License header required
  - Files: `plugin/scripts/download-git-filter-repo.sh`

- Commit: `feature: add CI workflow and download script for git-filter-repo standalone binary`

### Wave 2: Update git-rewrite-history-agent skill

- Rewrite `plugin/skills/git-rewrite-history-agent/first-use.md`:
  - **Purpose line**: "Safely rewrite git history using git filter-repo, with automatic Python
    detection and on-demand binary download."
  - **Why git filter-repo section**: Replace "Why BFG Repo-Cleaner" section:
    - Path-based filtering: `--path X --invert-paths` targets specific files/directories precisely
    - No filename collision risk: BFG's `--delete-files` removed ALL files with matching names
      (M562); git filter-repo is path-exact
    - Actively maintained and recommended by git itself
    - Python-free fallback: standalone binary downloaded on first use if Python is not installed
  - **Installation / first-use pattern**:
    ```bash
    FILTER_REPO=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-git-filter-repo.sh")
    ```
    This returns either the Python script path or the standalone binary path.
  - **Safety Pattern section**: Update to git filter-repo pattern (no bare clone needed):
    ```bash
    # git filter-repo modifies the repo in-place — create a backup first
    git branch backup-before-rewrite-$(date +%Y%m%d-%H%M%S)
    FILTER_REPO=$("${CLAUDE_PLUGIN_ROOT}/scripts/download-git-filter-repo.sh")
    "$FILTER_REPO" [options] --force
    # Verify history is correct
    # Then delete backup: git branch -D backup-before-rewrite-...
    ```
  - **Common Operations section** — replace BFG examples with git filter-repo equivalents:
    - Remove a file from all history:
      `"$FILTER_REPO" --path secrets.txt --invert-paths --force`
    - Remove a directory from all history:
      `"$FILTER_REPO" --path vendor/ --invert-paths --force`
    - Remove large files:
      `"$FILTER_REPO" --strip-blobs-bigger-than 10M --force`
    - Remove text patterns/secrets:
      `"$FILTER_REPO" --replace-text expressions.txt --force`
    - Drop specific commits: use `cat:git-rebase-agent` with
      `--onto <parent-of-commit> <commit> <branch>`
  - **After Rewriting History section**: Keep (still applies)
  - **Verification Checklist**: Keep, update command examples to use `$FILTER_REPO`
  - **Recovery section**: Update — git filter-repo does not use bare clones; restore from backup
    branch: `git reset --hard backup-before-rewrite-...`
  - **References section**: Update to git filter-repo links:
    - [git filter-repo documentation](https://htmlpreview.github.io/?https://github.com/newren/git-filter-repo/blob/docs/html/git-filter-repo.html)
    - [git filter-repo GitHub](https://github.com/newren/git-filter-repo)
    - [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
  - Files: `plugin/skills/git-rewrite-history-agent/first-use.md`

- Update `plugin/skills/git-rewrite-history-agent/SKILL.md` frontmatter description:
  - Change to: `"MANDATORY: Use instead of git filter-branch or BFG - git filter-repo with Python detection and on-demand binary download"`
  - Files: `plugin/skills/git-rewrite-history-agent/SKILL.md`

- Commit: `refactor: replace BFG with git filter-repo in git-rewrite-history-agent skill`
- Update STATE.md: status=closed, progress=100%

### Wave 3: Fix missing post-conditions (iteration 1)

- Update `plugin/skills/git-rewrite-history-agent/SKILL.md` frontmatter description to remove the
  "or BFG" reference: change the description value to
  `"MANDATORY: Use instead of git filter-branch - git filter-repo with Python detection and on-demand binary download"`
  so the description contains no mention of BFG
  - Files: `plugin/skills/git-rewrite-history-agent/SKILL.md`

- Set the executable permission bit on `plugin/scripts/download-git-filter-repo.sh` so it can be
  invoked directly via `$()` subshell as `"${CLAUDE_PLUGIN_ROOT}/scripts/download-git-filter-repo.sh"`:
  run `chmod +x plugin/scripts/download-git-filter-repo.sh` and stage the mode change with
  `git add plugin/scripts/download-git-filter-repo.sh` (git tracks the 100755 mode)
  - Files: `plugin/scripts/download-git-filter-repo.sh`

- Commit: `bugfix: remove BFG from SKILL.md description and set executable bit on download script`

## Post-conditions

- [ ] `first-use.md` contains no reference to BFG or `bfg.jar`
- [ ] `first-use.md` uses `download-git-filter-repo.sh` for all examples
- [ ] All common operations use `--path X --invert-paths` syntax (path-exact, no filename collision)
- [ ] `download-git-filter-repo.sh` checks for Python 3 first, falls back to platform binary
- [ ] `.github/workflows/build-git-filter-repo.yml` builds binaries for all 4 platforms
- [ ] SKILL.md description no longer references BFG
- [ ] E2E: Invoke `cat:git-rewrite-history-agent` and confirm the skill loads with git filter-repo
  instructions and the download script resolves to a usable path
