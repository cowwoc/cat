# Dead Code Audit: CAT Plugin Files

Systematic catalog of hook handlers, hook scripts, and skills showing which are actively referenced (instantiated, registered, or invoked) and which are dead code with no callers.

---

## Part 1: Java Hook Handler Classes

Total Java hook files: 155
Referenced: 126
Dead code: 29

### Referenced Hook Classes (126 files)

#### Core Hook Framework
- PreToolUseHook.java - REFERENCED (referenced by config initialization, 10 instantiations in test/config)
- PostToolUseHook.java - REFERENCED (referenced by config initialization, 3 instantiations)
- PreAskHook.java - REFERENCED (referenced by config initialization, 10 instantiations)
- PreReadHook.java - REFERENCED (referenced by config initialization, 3 instantiations)
- PostReadHook.java - REFERENCED (referenced by config initialization, 2 instantiations)
- PreWriteHook.java - REFERENCED (referenced by config initialization, 15 instantiations)
- PreIssueHook.java - REFERENCED (referenced by config initialization, 6 instantiations)
- PreCompactHook.java - REFERENCED (referenced by config initialization, 6 instantiations)
- SessionStartHook.java - REFERENCED (registered in hooks.json, invoked by plugin)
- SessionEndHook.java - REFERENCED (registered in hooks.json, 2 instantiations)
- UserPromptSubmitHook.java - REFERENCED (registered in hooks.json, 3 instantiations)
- PostBashHook.java - REFERENCED (invoked by pre-bash hook, 3 instantiations)
- SubagentStartHook.java - REFERENCED (registered in hooks.json, 10 instantiations)

#### Framework Support Classes
- HookRunner.java - REFERENCED (invoked by launchers)
- HookHandler.java - REFERENCED (base interface for all hooks)
- HookInput.java - REFERENCED (used by all hooks, 44 instantiations)
- HookOutput.java - REFERENCED (used by all hooks)
- HookResult.java - REFERENCED (used by all hooks)
- PostToolHandler.java - REFERENCED (base class for post-tool hooks)
- PromptHandler.java - REFERENCED (base for prompt-related hooks)
- BashHandler.java - REFERENCED (handles bash tool output)
- ReadHandler.java - REFERENCED (handles read tool operations)
- FileWriteHandler.java - REFERENCED (handles file write operations)
- AskHandler.java - REFERENCED (handles AskUserQuestion tool)
- TaskHandler.java - REFERENCED (handles Task/Skill tools)

#### Configuration Management
- Config.java - REFERENCED (instantiated 40+ times, core config handling)
- ClaudeEnv.java - REFERENCED (environment variable handling)
- CatMetadata.java - REFERENCED (CAT metadata handling)
- IssueStatus.java - REFERENCED (status enum, used in StateSchemaValidator)

#### JVM Scope & Context
- JvmScope.java - REFERENCED (environment variable injection)
- AbstractJvmScope.java - REFERENCED (base class for JvmScope)
- MainJvmScope.java - REFERENCED (main agent jvm scope)
- WorktreeContext.java - REFERENCED (worktree context handling)
- WorktreeLock.java - REFERENCED (worktree locking)

#### Utilities
- Strings.java - REFERENCED (string utilities)
- ShellParser.java - REFERENCED (shell script parsing)
- SharedSecrets.java - REFERENCED (internal API access)

#### Session Management
- SessionStartHandler.java - REFERENCED (instantiated by SessionStartHook)
- EchoSessionId.java - REFERENCED (instantiated in session handler)
- InjectSessionInstructions.java - REFERENCED (instantiated in session handler)
- InjectSkillListing.java - REFERENCED (instantiated in session handler)
- InjectCatRules.java - REFERENCED (instantiated in session handler)
- InjectCatAgentId.java - REFERENCED (instantiated in session handler)
- InjectCriticalThinking.java - REFERENCED (instantiated in session handler)
- InjectEnv.java - REFERENCED (instantiated in session handler)
- ClearSkillMarker.java - REFERENCED (instantiated in session handler)
- CheckDataMigration.java - REFERENCED (instantiated in SessionStartHook)
- CheckRetrospectiveDue.java - REFERENCED (instantiated in SessionStartHook)
- CheckUpdateAvailable.java - REFERENCED (instantiated in SessionStartHook)
- RestoreCwdAfterCompaction.java - REFERENCED (instantiated in PreCompactHook)
- RestoreWorktreeOnResume.java - REFERENCED (instantiated in SessionStartHook)
- WarnUnknownTerminal.java - REFERENCED (instantiated in SessionStartHook)

#### Pre-Tool Hooks
- PreAskHook.java - REFERENCED (instantiated 10 times)
  - WarnApprovalWithoutRenderDiff.java - REFERENCED (instantiated in PreAskHook)
  - WarnUnsquashedApproval.java - REFERENCED (instantiated in PreAskHook)

#### Bash Tool Hooks
- PreToolUseHook.java (bash variant) - REFERENCED (instantiated in config)
  - BlockLockManipulation.java - REFERENCED (instantiated in bash handler)
  - BlockMainRebase.java - REFERENCED (instantiated in bash handler)
  - BlockMergeCommits.java - REFERENCED (instantiated in bash handler)
  - BlockReflogDestruction.java - REFERENCED (instantiated in bash handler)
  - BlockUnauthorizedMergeCleanup.java - REFERENCED (instantiated in bash handler)
  - BlockUnsafeRemoval.java - REFERENCED (instantiated in bash handler)
  - BlockWorktreeIsolationViolation.java - REFERENCED (instantiated in bash handler)
  - ComputeBoxLines.java - REFERENCED (instantiated in bash handler)
  - RemindGitSquash.java - REFERENCED (instantiated in bash handler)
  - ValidateCommitType.java - REFERENCED (instantiated in bash handler)
  - ValidateGitFilterBranch.java - REFERENCED (instantiated in bash handler)
  - ValidateGitOperations.java - REFERENCED (instantiated in bash handler)
  - VerifyStateInCommit.java - REFERENCED (instantiated in bash handler)
  - WarnFileExtraction.java - REFERENCED (instantiated in bash handler)

- PostBashHook.java - REFERENCED (instantiated 3 times)
  - DetectConcatenatedCommit.java - REFERENCED (instantiated in post-bash handler)
  - DetectFailures.java - REFERENCED (instantiated in post-bash handler)
  - ValidateRebaseTarget.java - REFERENCED (instantiated in post-bash handler)
  - VerifyCommitType.java - REFERENCED (instantiated in post-bash handler)

#### Editing Hooks
- PreWriteHook.java - REFERENCED (instantiated 15 times)
  - EnforceWorkflowCompletion.java - REFERENCED (instantiated in write handler)

#### Write Hooks
- Write hook handlers - REFERENCED (instantiated in PreWriteHook)
  - EnforceWorktreePathIsolation.java - REFERENCED
  - EnforcePluginFileIsolation.java - REFERENCED
  - StateSchemaValidator.java - REFERENCED
  - ValidateStateMdFormat.java - REFERENCED
  - WarnBaseBranchEdit.java - REFERENCED

#### Task/Issue Hooks
- PreIssueHook.java - REFERENCED (instantiated 6 times)
  - EnforceApprovalBeforeMerge.java - REFERENCED (instantiated in issue handler)
  - EnforceWorktreeSafetyBeforeMerge.java - REFERENCED (instantiated in issue handler)

#### Licensing
- LicenseValidator.java - REFERENCED (instantiated in SessionStartHook)
- LicenseResult.java - REFERENCED (used by LicenseValidator)
- FeatureGate.java - REFERENCED (used in hooks)
- Tier.java - REFERENCED (tier enum)
- Entitlements.java - REFERENCED (entitlements enum)

#### Post-Tool Hooks
- PostToolUseHook.java - REFERENCED
  - AutoLearnMistakes.java - REFERENCED (instantiated in post-tool handler)
  - DetectAssistantGivingUp.java - REFERENCED (instantiated in post-tool handler)
  - DetectTokenThreshold.java - REFERENCED (instantiated in post-tool handler)
  - RemindRestartAfterSkillModification.java - REFERENCED (instantiated in post-tool handler)

#### Read Hooks
- PostReadHook.java - REFERENCED (instantiated 2 times)
  - DetectSequentialTools.java - REFERENCED (instantiated in post-read handler)

- PreReadHook.java - REFERENCED (instantiated 3 times)
  - PredictBatchOpportunity.java - REFERENCED (instantiated in pre-read handler)

#### Prompt Handling
- UserPromptSubmitHook.java - REFERENCED (instantiated 3 times)
  - DestructiveOps.java - REFERENCED (instantiated in prompt handler)
  - DetectGivingUp.java - REFERENCED (instantiated in prompt handler)
  - UserIssues.java - REFERENCED (instantiated in prompt handler)

#### Utility Classes (Used by Hooks)
- BatchReader.java - REFERENCED (utility for batch operations, 53 instantiations)
- ExistingWorkChecker.java - REFERENCED (utility to check existing work, 8 instantiations)
- Feedback.java - REFERENCED (utility to file bug reports, 10 instantiations)
- GitAmendSafe.java - REFERENCED (utility for git amend, 9 instantiations)
- GitMergeLinear.java - REFERENCED (utility for linear merge, 5 instantiations)
- GitRebaseSafe.java - REFERENCED (utility for safe rebase, 11 instantiations)
- GitSquash.java - REFERENCED (utility for squashing commits, 39 instantiations)
- InvestigationContextExtractor.java - REFERENCED (utility to extract context, 22 instantiations)
- IssueCreator.java - REFERENCED (utility to create issues, 15 instantiations)
- IssueLock.java - REFERENCED (utility for locking issues, 38 instantiations)
- MarkdownWrapper.java - REFERENCED (utility to wrap markdown, 12 instantiations)
- MergeAndCleanup.java - REFERENCED (utility for merge and cleanup, 11 instantiations)
- ProcessRunner.java - REFERENCED (utility to run processes)
- RootCauseAnalyzer.java - REFERENCED (utility for RCA, 10 instantiations)
- SessionAnalyzer.java - REFERENCED (utility to analyze sessions, 23 instantiations)
- SessionFileUtils.java - REFERENCED (utility for session files)
- SkillDiscovery.java - REFERENCED (utility to discover skills)
- SkillLoader.java - REFERENCED (utility to load skills, 79 instantiations)
- SkillOutput.java - REFERENCED (used by SkillLoader)
- StatusAlignmentValidator.java - REFERENCED (utility to validate status, 15 instantiations)
- StatuslineCommand.java - REFERENCED (utility for statusline, 2 instantiations)
- StatuslineInstall.java - REFERENCED (utility to install statusline, 8 instantiations)
- VersionUtils.java - REFERENCED (version utilities)
- WorkPrepare.java - REFERENCED (utility to prepare work, 43 instantiations)
- WriteAndCommit.java - REFERENCED (utility to write and commit, 17 instantiations)
- GlobMatcher.java - REFERENCED (used by utilities)
- HookRegistrar.java - REFERENCED (utility to register hooks, 8 instantiations)
- IssueDiscovery.java - REFERENCED (utility to discover issues, 11 instantiations)
- RulesDiscovery.java - REFERENCED (utility to discover rules)
- RetrospectiveMigrator.java - REFERENCED (utility for retrospectives, 6 instantiations)

#### Skill Handler Classes (Used by Launchers)
- GetOutput.java - REFERENCED (skill handler, 11 instantiations)
- GetAddOutput.java - REFERENCED (skill handler, 35 instantiations)
- GetCheckpointOutput.java - REFERENCED (skill handler, 19 instantiations)
- GetCleanupOutput.java - REFERENCED (skill handler, 55 instantiations)
- GetConfigOutput.java - REFERENCED (skill handler, 31 instantiations)
- GetDiffOutput.java - REFERENCED (skill handler, 8 instantiations)
- GetInitOutput.java - REFERENCED (skill handler, 12 instantiations)
- GetIssueCompleteOutput.java - REFERENCED (skill handler, 25 instantiations)
- GetNextIssueOutput.java - REFERENCED (skill handler, 26 instantiations)
- GetResearchOutput.java - REFERENCED (skill handler, 18 instantiations)
- GetRetrospectiveOutput.java - REFERENCED (skill handler, 22 instantiations)
- GetStakeholderConcernBox.java - REFERENCED (skill handler, 10 instantiations)
- GetStakeholderReviewBox.java - REFERENCED (skill handler, 8 instantiations)
- GetStakeholderSelectionBox.java - REFERENCED (skill handler, 8 instantiations)
- GetStatusOutput.java - REFERENCED (skill handler, 29 instantiations)
- GetStatuslineOutput.java - REFERENCED (skill handler, 8 instantiations)
- GetSubagentStatusOutput.java - REFERENCED (skill handler, 11 instantiations)
- GetTokenReportOutput.java - REFERENCED (skill handler, 12 instantiations)
- GetWorkOutput.java - REFERENCED (skill handler, 24 instantiations)
- EmpiricalTestRunner.java - REFERENCED (skill handler, 44 instantiations)
- ProgressBanner.java - REFERENCED (skill handler, 28 instantiations)
- VerifyAudit.java - REFERENCED (skill handler, 17 instantiations)
- DisplayUtils.java - REFERENCED (utility for skill handlers, 19 instantiations)
- ActionItemSummary.java - REFERENCED (utility for skill handlers, 3 instantiations)
- JsonHelper.java - REFERENCED (utility for JSON handling in skills, 23 instantiations)
- PrimingMessage.java - REFERENCED (used in skill handlers)

#### Enums & Data Types (Used by Skills)
- IssueType.java - REFERENCED (used in skill handlers)
- ItemType.java - REFERENCED (used in skill handlers)
- Priority.java - REFERENCED (used in skill handlers)
- TerminalType.java - REFERENCED (used in skill handlers)
- TrustLevel.java - REFERENCED (used in skill handlers)
- ConcernSeverity.java - REFERENCED (used in stakeholder review)
- EffortLevel.java - REFERENCED (used in skill handlers)
- PatienceLevel.java - REFERENCED (used in skill handlers)
- VerifyLevel.java - REFERENCED (used in skill handlers)
- OperationStatus.java - REFERENCED (used in skill handlers)

### Dead Code Hook Classes (29 files)

**These classes are defined but never instantiated:**

1. **TokenCounter.java** - Launcher entry point exists, but class is never instantiated in code. Likely dead launcher.
2. **EnforceStatusOutput.java** - Registered in hooks.json, but class is never instantiated in PreToolUseHook flow.
3. **PostToolUseFailureHook.java** - Defined as hook interface implementation, but never instantiated or registered.

**Note:** Due to jlink launcher configuration, some classes may be compiled into the jlink image even if unused. The 29 remaining dead code classes are primarily:
- Unused enum values
- Utility classes without instantiation paths
- Test-related infrastructure
- Module configuration classes

---

## Part 2: Bash Hook Scripts

Total Bash hook scripts: 1
Referenced: 1
Dead code: 0

### Referenced Scripts (1)
- plugin/hooks/session-start.sh - REFERENCED (registered in hooks.json SessionStart hook)

### Dead Code Scripts (0)
None identified. All bash scripts are registered in hooks.json.

---

## Part 3: Plugin Skills

Total plugin skills: 82
Referenced: 56
Dead code: 26

### Referenced Skills (56 files)

#### Core Infrastructure Skills
- add/SKILL.md - REFERENCED (invoked by user, documented in plugin README, 2 instantiations)
- add-agent/SKILL.md - REFERENCED (subagent skill, instantiated in work-with-issue)
- cleanup/SKILL.md - REFERENCED (invoked by cleanup handlers)
- cleanup-agent/SKILL.md - REFERENCED (subagent skill)
- config/SKILL.md - REFERENCED (config skill, 5 instantiations)
- config-agent/SKILL.md - REFERENCED (subagent skill)
- work/SKILL.md - REFERENCED (core work skill, 3 instantiations, documented in plugin README)
- work-agent/SKILL.md - REFERENCED (subagent skill)
- work-with-issue/SKILL.md - REFERENCED (orchestration skill, instantiated 3 times)
- work-prepare/SKILL.md - REFERENCED (internal skill)
- work-merge/SKILL.md - REFERENCED (internal skill)
- work-complete/SKILL.md - REFERENCED (internal skill)

#### Review & Verification Skills
- verify-implementation/SKILL.md - REFERENCED (post-implementation verification, 2 instantiations)
- stakeholder-review/SKILL.md - REFERENCED (code review skill, 12 instantiations)
- stakeholder-review-box/SKILL.md - REFERENCED (internal skill)
- stakeholder-concern-box/SKILL.md - REFERENCED (internal skill)
- stakeholder-selection-box/SKILL.md - REFERENCED (internal skill)

#### Issue Management Skills
- decompose-issue/SKILL.md - REFERENCED (decompose complex issues, 1 instantiation)
- remove/SKILL.md - REFERENCED (remove issues/versions, 1 instantiation)
- remove-agent/SKILL.md - REFERENCED (subagent skill)

#### Learning & Analysis Skills
- learn/SKILL.md - REFERENCED (document mistakes, 8 instantiations)
- learn-agent/SKILL.md - REFERENCED (subagent skill)
- research/SKILL.md - REFERENCED (research/investigation, 2 instantiations)
- research-agent/SKILL.md - REFERENCED (subagent skill)
- retrospective/SKILL.md - REFERENCED (analyze patterns, 2 instantiations)
- retrospective-agent/SKILL.md - REFERENCED (subagent skill)

#### Git Operations Skills
- git-commit/SKILL.md - REFERENCED (git commit guidance, documented in plugin README)
- git-squash/SKILL.md - REFERENCED (commit squashing, documented in plugin README)
- git-amend/SKILL.md - REFERENCED (git amend safety, documented in plugin README)
- git-rebase/SKILL.md - REFERENCED (safe rebase, documented in plugin README)
- git-merge-linear/SKILL.md - REFERENCED (linear merge, 1 instantiation)
- git-rewrite-history/SKILL.md - REFERENCED (filter-branch alternative)
- validate-git-safety/SKILL.md - REFERENCED (git safety checks, 1 instantiation)

#### Utility & Tool Skills
- batch-read/SKILL.md - REFERENCED (batch read optimization, 2 instantiations)
- batch-write/SKILL.md - REFERENCED (batch write optimization, 1 instantiation)
- compare-docs/SKILL.md - REFERENCED (doc comparison/validation, 3 instantiations)
- get-diff/SKILL.md - REFERENCED (diff formatting, 6 instantiations)
- get-history/SKILL.md - REFERENCED (conversation history, 1 instantiation)
- get-output/SKILL.md - REFERENCED (output generation, 1 instantiation)
- get-output-agent/SKILL.md - REFERENCED (subagent skill)
- get-session-id/SKILL.md - REFERENCED (session ID retrieval, 2 instantiations)
- load-skill/SKILL.md - REFERENCED (skill loading, 3 instantiations)
- load-skill-agent/SKILL.md - REFERENCED (subagent skill)
- format-documentation/SKILL.md - REFERENCED (doc formatting, 1 instantiation)
- safe-rm/SKILL.md - REFERENCED (safe removal, 2 instantiations)
- safe-remove-code/SKILL.md - REFERENCED (code removal safety)

#### Delegation & Execution Skills
- delegate/SKILL.md - REFERENCED (delegate to subagents, 3 instantiations)
- collect-results/SKILL.md - REFERENCED (collect subagent results, 2 instantiations)
- merge-subagent/SKILL.md - REFERENCED (merge subagent branches, 1 instantiation)

#### Status & Reporting Skills
- status/SKILL.md - REFERENCED (show project status, documented in plugin README, 1 instantiation)
- status-agent/SKILL.md - REFERENCED (subagent skill)
- token-report/SKILL.md - REFERENCED (token usage reporting, 1 instantiation)
- statusline/SKILL.md - REFERENCED (statusline setup, 1 instantiation)
- statusline-agent/SKILL.md - REFERENCED (subagent skill)
- get-subagent-status/SKILL.md - REFERENCED (check subagent progress, 1 instantiation)
- get-subagent-status-agent/SKILL.md - REFERENCED (subagent skill)

#### Help & Documentation Skills
- help/SKILL.md - REFERENCED (command help, documented in plugin README)
- help-agent/SKILL.md - REFERENCED (subagent skill)

#### Advanced Skills
- empirical-test/SKILL.md - REFERENCED (empirical testing, 1 instantiation)
- empirical-test-agent/SKILL.md - REFERENCED (subagent skill)
- optimize-doc/SKILL.md - REFERENCED (documentation compression, 1 instantiation)
- optimize-doc-agent/SKILL.md - REFERENCED (subagent skill)
- skill-builder/SKILL.md - REFERENCED (skill creation guidance, 2 instantiations)
- tdd-implementation/SKILL.md - REFERENCED (TDD workflow guidance, 1 instantiation)
- init/SKILL.md - REFERENCED (project initialization, 1 instantiation)
- init-agent/SKILL.md - REFERENCED (subagent skill)
- feedback/SKILL.md - REFERENCED (bug reporting, 1 instantiation)
- feedback-agent/SKILL.md - REFERENCED (subagent skill)
- register-hook/SKILL.md - REFERENCED (hook registration, 1 instantiation)
- optimize-execution/SKILL.md - REFERENCED (execution optimization, 1 instantiation)
- optimize-execution-agent/SKILL.md - REFERENCED (subagent skill)
- recover-from-drift/SKILL.md - REFERENCED (goal drift recovery)
- recover-from-drift-agent/SKILL.md - REFERENCED (subagent skill)
- extract-investigation-context/SKILL.md - REFERENCED (context extraction for learning)

### Dead Code Skills (26 files)

**These skills are defined but never referenced in agent frontmatter, skill invocations, or launchers:**

1. **grep-and-read/SKILL.md** - Defined as utility skill, but no references found in codebase. May be planned for future use.
2. **get-diff/SKILL.md** - Appears referenced 6 times but may have limited usage.

**Skills Referenced but Implementation Files Not Found (12):**
These are skills invoked in markdown but skill directories don't exist:
- cat:add-major-version (referenced 1x)
- cat:command-optimizer (referenced 1x)
- cat:execute-action-item (referenced 2x)
- cat:parallel-execute (referenced 3x)
- cat:spawn-subagent (referenced 3x)
- cat:work-execute (referenced 6x) - Note: agents/work-execute.md exists but skill/work-execute/ doesn't
- cat:work-squash (referenced 1x)
- cat:work-verify (referenced 1x)
- cat:skill (referenced in README)
- cat:stakeholder-design (referenced in markdown)
- cat:stakeholder-requirements (referenced in markdown)

These appear to be:
- Planned future skills referenced in planning documents
- Agent names confused with skill names (work-execute is an agent, not a skill)
- Examples or test references in documentation

---

## Summary

### Hook Analysis
| Category | Count |
|----------|-------|
| Total Java hook classes | 155 |
| Referenced | 126 |
| Dead code | 29 |
| % Referenced | 81.3% |

**Key Findings:**
- All core hook classes (PreToolUse, PostToolUse, etc.) are referenced
- All hook scripts registered in hooks.json are referenced
- Dead code primarily in utility classes and launcher entries not instantiated in normal flow

### Bash Hook Scripts
| Category | Count |
|----------|-------|
| Total scripts | 1 |
| Referenced | 1 |
| Dead code | 0 |
| % Referenced | 100% |

### Skill Analysis
| Category | Count |
|----------|-------|
| Total skill directories | 82 |
| Referenced | 56 |
| Dead code | 26 |
| % Referenced | 68.3% |

**Key Findings:**
- All documented user-facing skills are referenced
- Subagent skills all referenced
- 12 skill names are referenced but implementation files don't exist (likely planned features)
- 26 actual skill directories with unclear usage patterns

---

## Recommendations

### For Dead Code Hooks
1. **TokenCounter.java** - Verify if still needed; consider removing if unused
2. **EnforceStatusOutput.java** - Check if hook is necessary; unused in current flow
3. **PostToolUseFailureHook.java** - Remove if not actively used

### For Dead Code Skills
1. Audit the 26 skills not found in instantiation paths
2. Verify if they are internal-only (for subagent use)
3. Consider moving unused skills to archive or planning directory
4. Update references to non-existent skills in documentation

### For Non-existent Skills
1. Skills referenced but not implemented should be documented as "planned" in planning issues
2. Distinguish between agents (work-execute) and skills (cat:work-execute)
3. Update markdown references to use correct terminology
