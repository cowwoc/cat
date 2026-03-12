# Plan: fix-git-c-in-skill-files

## Goal

Replace `git -C <path>` usage in skill and agent files with the conventional pattern of `cd`-ing into the
worktree directory first and then running git commands without the `-C` flag. This follows the convention
documented in `plugin/concepts/git-operations.md:51` which states: "When executing an issue, run git commands
from inside the issue worktree — not from the main workspace using `git -C`."

## Satisfies

None — internal tooling cleanup.

## Files to Modify

### Skill files with replaceable `git -C` patterns
- `plugin/agents/work-squash.md` — 11 occurrences (rebase, status, log, show, add, commit)
- `plugin/skills/work-with-issue/first-use.md` — 7 occurrences (log, amend, diff)
- `plugin/skills/collect-results/first-use.md` — 6 occurrences (log, diff, status)
- `plugin/skills/work-merge/first-use.md` — 3 occurrences (diff, add)
- `plugin/skills/work-prepare/first-use.md` — some occurrences (log, branch checks)
- `plugin/skills/cleanup/first-use.md` — 4 occurrences (status, branch, rev-parse)
- `plugin/skills/git-merge-linear/first-use.md` — 2 occurrences (fetch, rebase); also update description
  claiming it uses `git -C` to avoid `cd`
- `plugin/skills/git-squash/first-use.md` — 3 post-squash verification occurrences

### Acceptable `git -C` uses (keep as-is, add explanatory comment)
- `plugin/skills/work-prepare/first-use.md` — `git worktree remove` and `git branch -D` run from project
  root (must not cd into worktree being removed)
- `plugin/skills/learn/phase-record.md` — commits to a non-worktree directory

### Files to leave unchanged (documentation/examples)
- `plugin/concepts/git-operations.md` — documents the convention
- `plugin/skills/safe-rm/first-use.md` — warns against `git -C` usage
- `plugin/skills/learn/HOOK-WORKAROUNDS.md` — historical documentation

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Audit all skill files for `git -C` usage** — for each occurrence, determine whether it can be replaced
   with a `cd` pattern or must remain (e.g., `git worktree remove` and `git branch -D` that run from the
   project root, or commits to non-worktree directories).

2. **Replace `git -C WORKTREE_PATH <cmd>` with cd pattern** — for each replaceable occurrence, update to:
   ```bash
   cd "${WORKTREE_PATH}"
   git <cmd>
   ```
   Or when staying inline: `(cd "${WORKTREE_PATH}" && git <cmd>)`

3. **Update `git-merge-linear/first-use.md`** — remove the claim "Uses `git -C` for all operations to
   avoid cd into" and replace with the `cd` idiom.

4. **Add comments to kept `git -C` uses** — each remaining `git -C` must have a comment explaining why
   it cannot use `cd` instead.

5. **Run grep to verify** — confirm no unintended `git -C` patterns remain in skill/agent files.


## Post-conditions

- [ ] No unexplained `git -C "${WORKTREE_PATH}"` patterns remain in skill/agent `.md` files
- [ ] All replaced patterns use `cd "${WORKTREE_PATH}"` or `(cd ... && git ...)` idiom
- [ ] `plugin/skills/git-merge-linear/first-use.md` no longer claims to use `git -C` to avoid `cd`
- [ ] Remaining `git -C` uses (if any) each have a comment explaining why `cd` is not used
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
