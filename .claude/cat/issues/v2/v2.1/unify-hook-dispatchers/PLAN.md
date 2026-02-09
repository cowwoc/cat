# Plan: unify-hook-dispatchers

## Current State
Hook scripts are registered individually in hooks.json and .claude/settings.json. Each bash/python script spawns a separate process. For SessionStart alone, 7 separate processes are spawned. Only some hook types have Java dispatchers (Bash PreToolUse, Read PreToolUse, PostToolUse, Bash PostToolUse, Read PostToolUse, UserPromptSubmit).

## Target State
One Java entrypoint per hook type that delegates internally based on matcher criteria. All bash/python hook logic migrated to Java handler classes. Only java.sh remains as the bridge script. hooks.json has one registration per hook type.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Hook behavior must be preserved exactly
- **Mitigation:** Each sub-issue has its own tests; existing tests must continue to pass

## Decomposition
Split into 6 sub-issues by hook type:
1. **unify-sessionstart-hooks** - 7 scripts (check-upgrade.sh, check-update-available.sh, echo-session-id.sh, check-retrospective-due.sh, inject-session-instructions.sh, clear_skill_markers.py, inject-env.sh)
2. **unify-userpromptsubmit-hooks** - 1 script (detect-giving-up.sh) into existing GetSkillOutput dispatcher
3. **unify-pretooluse-hooks** - 6 scripts (warn-unsquashed-approval.sh, warn-approval-without-renderdiff.sh, enforce-workflow-completion.sh, warn-skill-edit-without-builder.sh, warn-base-branch-edit.sh, enforce-approval-before-merge.sh)
4. **unify-posttooluse-hooks** - 2 scripts (detect-assistant-giving-up.sh, remind-restart-after-skill-modification.sh) into existing PostToolUse dispatchers
5. **unify-stop-sessionend-hooks** - 1 script (session-unlock.sh) + existing EnforceStatusOutput
6. **unify-project-hooks** - 2 scripts (validate-state-md-format.sh, block-worktree-cd.sh) from .claude/settings.json

## Acceptance Criteria
- [ ] All hook behavior preserved exactly (no functional changes)
- [ ] All tests passing
- [ ] All bash/python hook scripts removed
- [ ] hooks.json has one Java registration per hook type
- [ ] .claude/settings.json project hooks migrated to Java

## Execution Steps
1. Complete all 6 sub-issues
2. Verify no remaining bash/python hook scripts
3. Verify hooks.json consolidation

## Success Criteria
- [ ] Zero bash/python hook scripts remain in plugin/hooks/ (except java.sh bridge)
- [ ] hooks.json has maximum one java.sh registration per hook type per matcher
- [ ] All existing tests pass
- [ ] New Java handler tests cover migrated logic