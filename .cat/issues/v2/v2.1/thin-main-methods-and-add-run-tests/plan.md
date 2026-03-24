# Plan

## Goal

Ensure every non-hook CLI class with a `main()` method:
1. Delegates entirely to a `static void run(scope, args, PrintStream)` method with no
   other logic remaining in `main()`
2. Has unit tests that exercise `run()` directly, covering argument parsing, error paths,
   and representative happy-path inputs

This closes the coverage gap where bugs in `main()` argument handling and scope selection
go undetected because `main()` reads from `System.in` or `System.out` and cannot be
invoked safely from parallel unit tests.

## Background

There are 63 `main()` methods in the codebase split into three categories:

**Already correct — no work needed:**
- Hook handlers (14 classes: `PreToolUseHook`, `SessionStartHook`, etc.): already thin via
  `HookRunner.execute(ClassName::new, args)`
- CLI tools already following the pattern (9 classes with `*MainTest.java`):
  `BatchReader`, `GetDiffOutput`, `GitAmend`, `GitMergeLinear`, `GitRebase`, `GitSquash`,
  `RecordLearning`, `WorkPrepare`, `WriteAndCommit`

**Needs work — CLI tools with `run()` but no `*MainTest.java`** (6 classes):
`GetFile`, `GetSkill`, `IssueLock`, `MergeAndCleanup`, `VerifyDeferPlanGeneration`,
`WriteSessionMarker`

**Needs work — CLI tools with logic in `main()` and no `run()`** (~34 classes):
`AotTraining`, `EmpiricalTestRunner`, `EnforceStatusOutput`, `ExistingWorkChecker`,
`Feedback`, `GetAddOutput`, `GetCheckpointOutput`, `GetCleanupOutput`, `GetConfigOutput`,
`GetIssueCompleteOutput`, `GetNextIssueOutput`, `GetOutput`, `GetRetrospectiveOutput`,
`GetSkill`, `GetStakeholderConcernBox`, `GetStakeholderReviewBox`,
`GetStakeholderSelectionBox`, `GetStatusOutput`, `GetStatuslineOutput`,
`GetSubagentStatusOutput`, `GetTokenReportOutput`, `InvestigationContextExtractor`,
`IssueCreator`, `MarkdownWrapper`, `ProgressBanner`, `RetrospectiveMigrator`,
`RootCauseAnalyzer`, `SessionAnalyzer`, `StatusAlignmentValidator`, `StatuslineCommand`,
`StatuslineInstall`, `TokenCounter`, `VerifyAudit`

## Pre-conditions

- All 63 `main()` classes exist in `client/src/main/java/`

## Post-conditions

- [ ] Every non-hook CLI class has a `public static void run(scope, String[] args, PrintStream out)`
  method (or equivalent signature matching the class's scope type)
- [ ] Every `main()` in a non-hook CLI class contains exactly: scope instantiation +
  `run(scope, args, System.out)` + unexpected-error catch block — no argument parsing,
  no business logic, no conditional branches
- [ ] Every non-hook CLI class has a `*MainTest.java` (or equivalent tests in its existing
  test file) that calls `run()` directly with:
  - Missing/invalid arguments → verifies error output and/or exception
  - Representative valid arguments → verifies output contains expected content
- [ ] `mvn -f client/pom.xml verify -e` passes

## TDD Approach

For each class in the "needs work" categories:
1. Write failing tests for `run()` (argument validation, error paths, happy path)
2. Extract `run()` from `main()` logic (or add it where absent)
3. Thin `main()` to scope + `run()` delegation only
4. Verify tests pass

## Implementation Notes

- The `run()` method signature must match the class's scope type:
  - Infrastructure CLI tools (no session vars): `JvmScope`
  - Session CLI tools: `ClaudeTool`
- Argument parsing and all error handling (including `IOException` and
  `IllegalArgumentException`) belong in `run()`, not `main()`
- `main()` catches only `RuntimeException | AssertionError` for unexpected errors
- `GetSkill` is covered by `2.1-fix-get-skill-uses-main-claude-hook` — skip it here
