# Plan: replace-trim-with-strip

## Goal
Replace all instances of `String.trim()` with `String.strip()` across the Java codebase for Unicode-aware whitespace
handling.

## Satisfies
None - code quality / convention alignment

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** `strip()` removes Unicode whitespace that `trim()` does not; in practice this only adds correctness
- **Mitigation:** All tests must pass after replacement

## Files to Modify
16 files containing `.trim()` calls:
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextTaskOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/GitCommands.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ValidateCommitType.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRenderDiffOutput.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/bash/ComputeBoxLines.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetSessionStartOutputTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java`

## Execution Steps
1. Replace all `.trim()` with `.strip()` across all 16 files
2. Run `mvn -f hooks/pom.xml verify`

## Success Criteria
- [ ] Zero occurrences of `.trim()` in `hooks/src/`
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)
