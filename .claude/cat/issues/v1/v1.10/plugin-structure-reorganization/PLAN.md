# Plan: plugin-structure-reorganization

## Objective
add task/ subdirectory migration to 1.10

## Details
Migrates deprecated directory structure where tasks were nested under
a task/ subdirectory to the expected flat structure:

Old: .claude/cat/v1/v1/task/<task-name>/PLAN.md
New: .claude/cat/v1/v1/<task-name>/PLAN.md

Fail-fast behavior:
- Exits on mv failure
- Exits on conflict (target directory exists)
- Exits if task/ not empty after migration (unexpected files)

Related to M156 - status.sh crash when task/ subdirectory used.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
