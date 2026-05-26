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

## Research Findings

### Class Hierarchy
- `JvmScope` (interface) — root, provides basic JVM infrastructure
- `ClaudeTool` (interface, extends JvmScope) — adds `getClaudeConfigPath()`, `getSessionId()`
- `ClaudeHook` (interface, extends JvmScope) — adds `getClaudeConfigPath()`, `getSessionId()`, hook I/O methods
- `AbstractJvmScope` — abstract class implementing JvmScope with `getClaudeConfigPath()` as abstract protected
- `MainClaudeTool` — concrete implementation for CLI skill tools
- `MainClaudeHook` — concrete implementation for hook handlers
- `MainJvmScope` — concrete implementation for infrastructure tools (no session context)

### File Classification

**Hook Handlers (use ClaudeHook):**
- DetectTokenThreshold — PostToolHandler, constructor takes ClaudeHook
- EnforceStatusOutput — Stop hook handler
- BlockUnauthorizedMergeCleanup — BashHandler, constructor takes ClaudeHook
- RequireSkillForCommand — BashHandler
- WarnApprovalWithoutRenderDiff — handler
- AutoLearnMistakes — PostToolHandler, constructor takes ClaudeHook
- DetectValidationWithoutEvidence — PostToolHandler
- DetectAssistantGivingUp — PostToolHandler
- EnforceApprovalBeforeMerge — hook handler
- SessionEndHandler — SessionEndHandler, constructor takes ClaudeHook
- WarnUnknownTerminal — SessionStartHandler

**CLI Tool/Skill Classes (use ClaudeTool):**
- SessionAnalyzer — main() creates MainClaudeTool
- InvestigationContextExtractor — main() creates MainClaudeTool
- RecordLearning — main() creates MainClaudeTool
- GetSubagentStatusOutput — main() creates MainClaudeTool
- EmpiricalTestRunner — main() creates MainClaudeTool

**Infrastructure Tools (use JvmScope or ClaudeTool):**
- GetSkill — main() creates MainJvmScope, but needs getClaudeSessionsPath(), so must widen to ClaudeTool

**Utility Classes (dual context):**
- SkillDiscovery — has overloads for both ClaudeHook and ClaudeTool contexts

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

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — all changes are internal parameter type widening
- **Mitigation:** Compilation check verifies all call sites are updated correctly

## Jobs

### Job 1
- For each file listed above, check if the `scope` parameter (or field/constructor) is currently typed as
  `JvmScope` or `AbstractJvmScope`. If so, change it to the appropriate type:
  - Hook handlers (classes implementing PostToolHandler, BashHandler, SessionStartHandler, SessionEndHandler,
    or used in hook context with MainClaudeHook): change to `ClaudeHook`
  - CLI tool/skill classes (classes with `main()` creating MainClaudeTool): change to `ClaudeTool`
  - GetSkill.java: if scope is typed as JvmScope/MainJvmScope and calls getClaudeSessionsPath(),
    change the main() to create MainClaudeTool instead of MainJvmScope, or change the method parameter to ClaudeTool
  - SkillDiscovery.java: ensure both overloads (ClaudeHook and ClaudeTool) exist if needed; update any
    JvmScope-typed parameters
- Update import statements accordingly (add imports for ClaudeTool/ClaudeHook, remove unused JvmScope imports)
- If a file already has the correct type (ClaudeTool or ClaudeHook), no changes needed — skip it
- After all changes, run `mvn -f client/pom.xml compile` to verify compilation
- If compilation fails, fix any remaining call sites that reference the removed methods on JvmScope
- Run `mvn -f client/pom.xml test` to verify all tests pass
- Update index.json status to closed, progress 100%

## Post-conditions

- [ ] No call site in `client/src/main/` calls `scope.getClaudeConfigDir()`,
  `scope.getClaudeSessionsPath()`, or `scope.getClaudeSessionPath()` on a `JvmScope`-typed variable
- [ ] Code compiles
