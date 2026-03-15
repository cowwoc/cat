# Plan: Add Guidance to Hooks

## Current State

Many hook implementations output messages that block or warn agents, but some lack clear guidance about what the agent should do instead. Hook messages variously include actionable instructions (e.g., BlockGitconfigFileWrite, EnforceWorktreePathIsolation, EnforceCommitBeforeSubagentSpawn) while others provide only the reason for blocking without next steps (e.g., some validation error hooks, some migration hooks). Agents receive blocks/warns but must infer recovery paths from incomplete information.

## Target State

All hooks that block or warn operations provide guidance on what the agent should do instead (or explain what the hook protects when no safe alternative exists). Plugin hook guidance is standardized in a new convention file (plugin/rules/hook-output-guidance.md) so future hooks are created with complete guidance messages from inception.

## Parent Requirements

None (technical debt/tool polish)

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — only output messages change. Hook logic and behavior remain identical.
- **Mitigation:** Changes are additive (more detail in error messages). Existing tests verify hook behavior, not message content, so no test updates needed.

## Files to Modify

### Behavioral Hooks (2-3 sentence additions to existing block/warn messages):

1. **Plugin hooks in client/src/main/java/io/github/cowwoc/cat/hooks/**:
   - `write/BlockGitconfigFileWrite.java` — Already has actionable guidance (good model)
   - `write/EnforceWorktreePathIsolation.java` — Already has guidance (good model)
   - `write/EnforcePluginFileIsolation.java` — Check message; add guidance if missing
   - `write/WarnBaseBranchEdit.java` — Warn messages already guide to /cat:work or manual worktree (good model)
   - `write/StateSchemaValidator.java` — Add guidance explaining how to fix each schema violation
   - `edit/EnforceWorkflowCompletion.java` — Already has step-by-step guidance (good model)
   - `task/EnforceCommitBeforeSubagentSpawn.java` — Already has detailed guidance (good model)
   - `task/EnforceApprovalBeforeMerge.java` — Already has approval methods listed (good model)
   - `task/EnforceCollectAfterAgent.java` — Already has invocation guidance (good model)
   - `task/EnforceWorktreeSafetyBeforeMerge.java` — Already specifies required fix (good model)
   - `ask/WarnApprovalWithoutRenderDiff.java` — Already has step-by-step guidance (good model)
   - `ask/WarnUnsquashedApproval.java` — Already identifies which commits to squash (good model)
   - `tool/post/RemindRestartAfterSkillModification.java` — Already specifies action (restart) (good model)
   - `tool/post/DetectValidationWithoutEvidence.java` — Already instructs to invoke skill first (good model)
   - `tool/post/DetectTokenThreshold.java` — Message context only (not a critical action block)
   - `tool/post/DetectAssistantGivingUp.java` — Already lists prohibited patterns and token policy (good model)
   - `tool/post/AutoLearnMistakes.java` — Inject instruction to run learn skill
   - `tool/post/SetPendingAgentResult.java` — No block/warn (just internal flag management)
   - `failure/DetectPreprocessorFailure.java` — Review message; add guidance if needed
   - `failure/DetectRepeatedFailures.java` — Review message; add guidance if needed
   - `failure/ResetFailureCounter.java` — Review message; add guidance if needed

2. **New Convention File**:
   - `plugin/rules/hook-output-guidance.md` — Document requirement that all hooks provide guidance when blocking/warning. Include examples of good patterns (from BlockGitconfigFileWrite, EnforceCommitBeforeSubagentSpawn, etc.) and bad patterns (minimal reason only).

### Project Hooks

- `.claude/settings.json` — Currently `"hooks": {}` (empty). No changes needed for this issue. Document in convention that project hooks should also follow guidance pattern if added in future.

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Waves

- Read all behavioral hook files (18 files to inventory)
- Identify which already have complete guidance vs. which need enhancement
- Research message patterns in top 5 "good model" hooks to establish patterns

## Sub-Agent Waves

### Wave 1 (Inventory and Assessment)

- Inventory all hooks in `client/src/main/java/io/github/cowwoc/cat/hooks/` and `plugin/hooks/`
- For each hook, extract current block/warn message text
- Classify: already has actionable guidance, or needs enhancement
- Files: (all hook source files identified in "Files to Modify" section)

### Wave 2 (Enhancement)

- Update block/warn messages in hooks identified as needing enhancement
- Follow patterns from good models (BlockGitconfigFileWrite, EnforceCommitBeforeSubagentSpawn, etc.)
- Each message must include: what the hook prevents AND how the agent should proceed
- For hooks with no safe alternative (e.g., preventing rm -rf), explain what the hook protects against
- Files: Hook source files in `client/src/main/java/io/github/cowwoc/cat/hooks/`

### Wave 3 (Convention + Verification)

- Create `plugin/rules/hook-output-guidance.md` with convention that all hooks must include guidance
- Include examples of good and bad patterns
- Document both plugin hooks and project hooks guidance expectations
- Files: `plugin/rules/hook-output-guidance.md` (new file)

### Wave 4 (Test + Commit)

- Run full test suite (mvn -f client/pom.xml test) — verify no regressions
- Verify all hook logic unchanged (behavior identical, only messages enhanced)
- Create inventory/record showing which hooks were updated and which were already compliant
- Commit with type `feature:` (new convention + message guidance is a user-facing improvement)
- Update STATE.md with completion

## Post-conditions

- [ ] All hook block/warn messages provide actionable guidance (what to do instead, or explanation of protection)
- [ ] Hooks with no safe alternative explain what the hook protects against
- [ ] New convention file `plugin/rules/hook-output-guidance.md` added and documents requirement for future hooks
- [ ] All tests pass after message enhancements (no behavior changes, no regressions)
- [ ] Inventory record exists showing which hooks were enhanced and which were already compliant
- [ ] E2E verification: Trigger a hook block (e.g., git config write, worktree isolation violation) and confirm output includes guidance text