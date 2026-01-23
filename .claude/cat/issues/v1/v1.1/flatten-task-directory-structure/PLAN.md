# Plan: flatten-task-directory-structure

## Objective
flatten task directory structure and use task ID format

## Details
Changes:
- Remove task/ subdirectory: tasks now live directly under minor version
  Old: .claude/cat/v1/v1/task/my-task/
  New: .claude/cat/v1/v1/my-task/

- Change execute-task argument format from path to ID
  Old: /cat:execute-task 1.0/task-name
  New: /cat:execute-task 1.0-task-name

Updated files:
- README.md, SPECIFICATION.md
- All commands referencing task paths
- Skills and reference documentation

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
