# Plan: cat-work-command-rename

## Objective
fix inconsistent task path patterns (missing /task/ subdirectory)

## Details
The find command and several path references were missing the /task/
subdirectory in the task path structure.

Wrong:  .claude/cat/v{major}/v{major}.{minor}/{task-name}/
Right:  .claude/cat/v{major}/v{major}.{minor}/task/{task-name}/

This caused execute-task to fail finding any STATE.md files during
task discovery, requiring manual directory exploration.

Fixed in:
- commands/execute-task.md (find pattern + validation path)
- commands/add-task.md (output path)
- commands/init.md (mkdir path)
- commands/research.md (RESEARCH.md path)
- workflows/execute-task.md (STATE.md paths)
- workflows/merge-and-cleanup.md (STATE.md paths)

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
