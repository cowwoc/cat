---
issue: 2.1-jvmenv-w3-main
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 3 of 5
---

# Plan: jvmenv-w3-main

## Objective

Update all main-source call sites where `scope` is typed as `JvmScope` but calls
`getClaudeConfigDir()`, `getClaudeSessionsPath()`, or `getClaudeSessionPath()`. After Wave 2,
these methods no longer exist on `JvmScope` and the parameter types must be widened to
`ClaudeTool` or `ClaudeHook` as appropriate.

## Dependencies

- `2.1-jvmenv-w2-interface` must be merged first

## Strategy

For each file below, change the parameter type of the `scope` argument from `JvmScope` (or
`AbstractJvmScope`) to the narrowest supertype that provides the needed method:

- If the class handles hook execution (receives a `ClaudeHook` at call site): use `ClaudeHook`
- If the class is invoked as a skill CLI tool (receives a `ClaudeTool` at call site): use `ClaudeTool`
- If the class is used in both contexts: use the narrowest common supertype, or split the method

## Files to Update

### Uses getClaudeConfigDir()

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillDiscovery.java` (2 occurrences)
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectTokenThreshold.java`

### Uses getClaudeSessionsPath()

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetSubagentStatusOutput.java` (2 occurrences)
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectValidationWithoutEvidence.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`

### Uses getClaudeSessionPath()

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/WarnUnknownTerminal.java`

## Post-conditions

- [ ] No call site in `client/src/main/` calls `scope.getClaudeConfigDir()`,
  `scope.getClaudeSessionsPath()`, or `scope.getClaudeSessionPath()` on a `JvmScope`-typed variable
- [ ] Code compiles
