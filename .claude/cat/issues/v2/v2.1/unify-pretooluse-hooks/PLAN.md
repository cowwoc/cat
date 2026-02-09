# Plan: unify-pretooluse-hooks

## Current State
9 PreToolUse registrations in hooks.json with 6 bash scripts and 3 Java dispatchers:
- AskUserQuestion: warn-unsquashed-approval.sh, warn-approval-without-renderdiff.sh
- Bash: java.sh GetBashPretoolOutput (already Java)
- Read|Glob|Grep: java.sh GetReadPretoolOutput (already Java)
- Edit: enforce-workflow-completion.sh, warn-skill-edit-without-builder.sh
- Write|Edit: warn-base-branch-edit.sh, java.sh EnforceWorktreeIsolation (already Java)
- Task: enforce-approval-before-merge.sh

## Target State
Consolidate by matcher. Each unique matcher gets one Java dispatcher:
- AskUserQuestion: New GetAskPretoolOutput dispatcher with WarnUnsquashedApproval + WarnApprovalWithoutRenderDiff handlers
- Edit: Extend existing or new GetEditPretoolOutput with EnforceWorkflowCompletion + WarnSkillEditWithoutBuilder handlers
- Write|Edit: Absorb WarnBaseBranchEdit into EnforceWorktreeIsolation dispatcher (or new GetWriteEditPretoolOutput)
- Task: New GetTaskPretoolOutput with EnforceApprovalBeforeMerge handler
- Bash and Read|Glob|Grep: Already Java, no changes needed

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** warn-base-branch-edit.sh has complex allowlist logic; enforce-approval-before-merge.sh reads session JSONL; warn-approval-without-renderdiff.sh counts box-drawing characters
- **Mitigation:** Careful porting with edge case tests

## Files to Modify
- hooks/src/main/java/com/cat/hooks/ask/ - NEW: AskUserQuestion handler package
- hooks/src/main/java/com/cat/hooks/ask/WarnUnsquashedApproval.java - NEW
- hooks/src/main/java/com/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java - NEW
- hooks/src/main/java/com/cat/hooks/GetAskPretoolOutput.java - NEW: Dispatcher
- hooks/src/main/java/com/cat/hooks/edit/ - NEW: Edit handler package
- hooks/src/main/java/com/cat/hooks/edit/EnforceWorkflowCompletion.java - NEW
- hooks/src/main/java/com/cat/hooks/edit/WarnSkillEditWithoutBuilder.java - NEW
- hooks/src/main/java/com/cat/hooks/GetEditPretoolOutput.java - NEW: Dispatcher
- hooks/src/main/java/com/cat/hooks/write/WarnBaseBranchEdit.java - NEW
- hooks/src/main/java/com/cat/hooks/EnforceWorktreeIsolation.java - Modify to also run WarnBaseBranchEdit
- hooks/src/main/java/com/cat/hooks/task/ - NEW: Task handler package
- hooks/src/main/java/com/cat/hooks/task/EnforceApprovalBeforeMerge.java - NEW
- hooks/src/main/java/com/cat/hooks/GetTaskPretoolOutput.java - NEW: Dispatcher
- hooks/src/test/java/com/cat/hooks/ask/ - NEW: Tests
- hooks/src/test/java/com/cat/hooks/edit/ - NEW: Tests
- hooks/src/test/java/com/cat/hooks/task/ - NEW: Tests
- plugin/hooks/hooks.json - Consolidate PreToolUse entries by matcher
- plugin/hooks/warn-unsquashed-approval.sh - DELETE
- plugin/hooks/warn-approval-without-renderdiff.sh - DELETE
- plugin/hooks/enforce-workflow-completion.sh - DELETE
- plugin/hooks/warn-skill-edit-without-builder.sh - DELETE
- plugin/hooks/warn-base-branch-edit.sh - DELETE
- plugin/hooks/enforce-approval-before-merge.sh - DELETE

## Acceptance Criteria
- [ ] All 6 bash PreToolUse scripts ported to Java handlers
- [ ] warn-unsquashed-approval.sh commit counting logic preserved
- [ ] warn-approval-without-renderdiff.sh box-character detection preserved
- [ ] enforce-workflow-completion.sh STATE.md phase checking preserved
- [ ] warn-skill-edit-without-builder.sh skill path detection preserved
- [ ] warn-base-branch-edit.sh allowlist logic preserved (M442 narrow allowlist)
- [ ] enforce-approval-before-merge.sh trust-level + session log search preserved
- [ ] hooks.json PreToolUse consolidated (one entry per matcher)
- [ ] All 6 bash scripts deleted
- [ ] Tests pass

## Key Implementation Details
- warn-unsquashed-approval.sh: Needs git operations (commit count vs base branch) - use ProcessRunner or GitCommands util
- warn-approval-without-renderdiff.sh: Reads session JSONL, counts Unicode box chars - use Files.readString + regex
- enforce-workflow-completion.sh: Parses STATE.md for status field - use regex on file content
- warn-skill-edit-without-builder.sh: Path matching on /skills/*/SKILL.md - simple string check
- warn-base-branch-edit.sh: Complex allowlist with M442 narrowing - careful port of pattern matching
- enforce-approval-before-merge.sh: Reads cat-config.json for trust level, searches session JSONL - use JsonMapper + Files

## Execution Steps
1. Create handler packages (ask/, edit/, task/)
2. Port each bash script to its Java handler
3. Create dispatchers (GetAskPretoolOutput, GetEditPretoolOutput, GetTaskPretoolOutput)
4. Integrate WarnBaseBranchEdit into EnforceWorktreeIsolation
5. Write tests for each handler
6. Update hooks.json
7. Delete old bash scripts
8. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] No bash PreToolUse scripts remain
- [ ] hooks.json PreToolUse has one entry per unique matcher