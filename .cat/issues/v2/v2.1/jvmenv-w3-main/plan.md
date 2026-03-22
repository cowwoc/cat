---
issue: 2.1-jvmenv-w3-main
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 3 of 5
---

# Plan: jvmenv-w3-main

## Objective

Update all main-source call sites to use the new `scope.getClaudeEnv()` accessor instead of
the removed scope methods.

## Dependencies

- `2.1-jvmenv-w2-interface` must be merged first

## Substitutions

For each file listed below:
- `scope.getClaudeSessionId()` → `scope.getClaudeEnv().getSessionId()`
- `scope.getProjectPath()` → `scope.getClaudeEnv().getProjectPath()`
- `scope.getClaudePluginRoot()` → `scope.getClaudeEnv().getPluginRoot()`
- `scope.getClaudeEnvFile()` → `scope.getClaudeEnv().getEnvFile()`

## Files to Update

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` (3 occurrences of getProjectPath)
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCommitBeforeSubagentSpawn.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` (getClaudeSessionId + 2x getProjectPath)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillDiscovery.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RootCauseAnalyzer.java` (verify if already uses ClaudeEnv)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatuslineOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetWorkOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectMainAgentRules.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`

## Post-conditions

- [ ] No call site in `client/src/main/` references `scope.getClaudeSessionId()`,
  `scope.getProjectPath()`, `scope.getClaudePluginRoot()`, or `scope.getClaudeEnvFile()`
- [ ] Code compiles
