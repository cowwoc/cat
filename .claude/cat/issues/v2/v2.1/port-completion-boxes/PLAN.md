# Plan: port-completion-boxes

## Goal
Port checkpoint, issue-complete, and next-task box Python scripts to Java classes in the hooks module.

## Current State
Three related Python scripts render completion/progress boxes. `get-next-task-box.py` imports from
`get-issue-complete-box.py`. Java `ComputeBoxLines.java` and `DisplayUtils.java` already exist as shared utilities.

## Target State
Java classes replace the three Python scripts. Handlers invoke Java directly without spawning Python subprocesses.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Output format must remain character-for-character identical
- **Mitigation:** Diff-based comparison of old vs new output

## Scripts to Port
- `get-checkpoint-box.py` (147 lines) - Renders checkpoint boxes with metrics
- `get-issue-complete-box.py` (156 lines) - Renders issue-complete or scope-complete boxes
- `get-next-task-box.py` (195 lines) - Orchestrates task completion: releases lock, discovers next task, renders box

## Files to Create/Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java` - New
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` - New
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextTaskOutput.java` - New
- Update relevant handlers to call Java classes instead of Python scripts

## Execution Steps
1. **Read Python scripts** to understand exact output format and logic
2. **Read existing Java patterns** (`ComputeBoxLines.java`, `DisplayUtils.java`, `GetWorkOutput.java`) to match style
3. **Read `.claude/cat/conventions/java.md`** for coding conventions
4. **Port `get-checkpoint-box.py`** to `GetCheckpointOutput.java`
5. **Port `get-issue-complete-box.py`** to `GetIssueCompleteOutput.java`
6. **Port `get-next-task-box.py`** to `GetNextTaskOutput.java`
7. **Update handlers** that currently invoke these Python scripts to call Java classes directly
8. **Run tests:** `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] Three Java classes created matching exact Python output
- [ ] Handlers updated to call Java instead of Python
- [ ] All tests pass
