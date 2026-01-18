# Plan: command-consolidation

## Objective
consolidate add/remove commands into unified skills

## Details
Replace 6 separate commands with 2 unified interactive commands:
- /cat:add-task, /cat:add-minor-version, /cat:add-major-version → /cat:add
- /cat:remove-task, /cat:remove-minor-version, /cat:remove-major-version → /cat:remove

New commands ask user what they want to add/remove (task or version),
then route to the appropriate workflow. Version conflicts now offer
insert-before-and-renumber option.

Updates all documentation references accordingly.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
