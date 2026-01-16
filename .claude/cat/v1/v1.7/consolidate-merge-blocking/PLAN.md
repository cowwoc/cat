# Plan: consolidate-merge-blocking

## Objective
consolidate and strengthen merge commit blocking

## Details
- Merged warn-merge-commits.sh into block-merge-commits.sh
- Upgraded git merge without --ff-only from WARNING to BLOCK
- All git merge commands now require --ff-only or --squash
- MERGE_HEAD check remains as informational warning
- Updated hooks.json to remove deleted script reference

BLOCKED: git merge --no-ff, git merge (without --ff-only/--squash)
ALLOWED: git merge --ff-only, git merge --squash

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
