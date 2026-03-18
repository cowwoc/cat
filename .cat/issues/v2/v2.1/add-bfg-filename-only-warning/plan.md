# Add BFG --delete-files Filename-Only Warning

## Goal
Update `plugin/skills/git-rewrite-history-agent/first-use.md` to add a CRITICAL warning explaining that BFG's `--delete-files` flag matches by filename only (not by path), and document `git filter-repo --path X --invert-paths` as the correct tool for path-specific deletion.

## Background
M562: BFG's `--delete-files PLAN.md` deleted ALL files named PLAN.md from git history ��� including hundreds of `.cat/issues/*/PLAN.md` files ��� not just root-level ones. The skill had no warning about this filename-only behavior.

## Changes Required

1. In `plugin/skills/git-rewrite-history-agent/first-use.md`, in the "Remove a File from All History" section, add a CRITICAL warning:
   - BFG `--delete-files` matches by **filename only**, not by path
   - If other files share the same name anywhere in the repository, they will also be deleted
   - Provide a pre-check command: `git log --all -- "**/<filename>" | head` to detect shared names

2. Add a new "Remove a File from a Specific Path" section documenting:
   - Use `git filter-repo --path <path> --invert-paths` for path-specific deletion
   - Example: `git filter-repo --path PLAN.md --invert-paths --force` removes only root-level PLAN.md

## Post-conditions

- [ ] `first-use.md` includes CRITICAL warning that `--delete-files` matches by filename only
- [ ] `first-use.md` documents `git filter-repo --path X --invert-paths` as the path-specific alternative
- [ ] `first-use.md` includes a bash pre-check command to verify no other files share the same name
- [ ] Regression test added for M562 scenario
- [ ] No regressions introduced
- [ ] E2E verification: confirm updated skill guides agents to correct tool for path-specific deletion