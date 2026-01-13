# Plan: retrospective-action-items-A005-A007

## Objective
implement retrospective action items A005, A007

## Details
## A005: Checkpoint Enforcement
- commands/execute-task.md: Add plan_change_checkpoint and user_review_checkpoint
- skills/learn-from-mistakes/SKILL.md: Add Step 0 get-history verification (M037)
- skills/learn-from-mistakes/SKILL.md: Add path validation guidance (M040, M041)

## A007: Destructive Git Commands
- hooks/lib/json-parser.sh: Add output_hook_block and output_hook_warning functions
- hooks/block-reflog-destruction.sh: Use CAT lib instead of project-specific path
- hooks/warn-file-extraction.sh: NEW - Warns about file extraction from non-HEAD commits
- hooks/hooks.json: Register warn-file-extraction.sh (already done in previous commit)

## Readability Improvement
- hooks/inject-session-instructions.sh: Use multiline heredoc with jq for readable source

Addresses: M034, M035, M037, M040, M041, M025, M026

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
