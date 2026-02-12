# Plan: refactor-hook-output-as-data

## Current State

All hook classes (Get*Output, SessionAnalyzer, utility classes) print warnings and errors directly to
`System.err.println()`. Tests capture stderr using `System.setErr()` which is thread-unsafe and violates
the java.md convention (line 1015). The `logback` dependency exists but is unused.

## Target State

Business logic returns output (stdout content, stderr warnings, errors) as data in return values.
Only `main()` entry points print to stdout/stderr. SLF4J used for unexpected errors/diagnostics.
Tests assert on returned data, produce zero console output on success, and never use `System.setErr()`.

## Satisfies

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Return types of `run()` methods change to include warnings. Internal API change only
  (no external consumers).
- **Mitigation:** Incremental approach - update one dispatcher at a time. All tests must pass after each file.

## Files to Modify

### Hook dispatchers (return warnings as data instead of printing):
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetEditOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetPostOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetReadOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetReadPostOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetTaskOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetWriteEditOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetAskOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetBashOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetBashPostOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSkillOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSessionStartOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSessionEndOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java` - update to handle warnings from return values

### Utility classes (return warnings as data):
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` - return parse warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/IssueCreator.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/WriteAndCommit.java` - return errors as data

### Skill classes (return warnings as data):
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextTaskOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRenderDiffOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/RunGetStatusOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java` - return warnings in result
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` - return warnings in result

### Session classes:
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/SessionUnlock.java` - return errors as data
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpgrade.java` - return errors as data

### Other:
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/TokenCounter.java` - usage errors via return value

### Tests:
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` - remove System.setErr(),
  assert on returned warning data instead

### Config:
- `hooks/src/test/resources/logback-test.xml` - create, set level to ERROR (silent tests)
- `hooks/src/main/resources/logback.xml` - update pattern to `%msg%n` (bare messages, no timestamp)

## Execution Steps

1. **Create logback-test.xml** in `src/test/resources/` with root level ERROR
2. **Update logback.xml** pattern from `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n` to `%msg%n`
3. **For each Get*Output dispatcher class:**
   - Change `run()` method to collect warnings in a `List<String>` and return them as part of the result
   - Move `System.err.println(warning)` from `run()` to `main()` (print returned warnings)
   - Replace `System.err.println` for handler errors with SLF4J `logger.error()`
4. **For each utility class** (SessionAnalyzer, GitSquash, etc.):
   - Change methods to return warnings/errors as part of result objects
   - Move `System.err.println` to `main()` callers
5. **Update HookEntryPointTest:**
   - Remove all `System.setErr()` / `System.setOut()` usage
   - Assert on warning data returned by `run()` methods instead
6. **Run `mvn -f hooks/pom.xml verify`** to confirm all tests pass with zero console output

## Acceptance Criteria

- [ ] Behavior unchanged: hook output identical in production (same messages to stdout/stderr)
- [ ] All tests pass
- [ ] Tests produce zero console output on success
- [ ] `System.setErr()` and `System.setOut()` removed from all tests
- [ ] All `System.err.println` in business logic replaced with return-value data
- [ ] Only `main()` entry points print to stdout/stderr
- [ ] SLF4J used for unexpected errors/diagnostics (not for testable assertions)
- [ ] `logback-test.xml` exists and silences output during tests

## Success Criteria

- [ ] `mvn -f hooks/pom.xml verify` passes with exit code 0
- [ ] Running tests produces zero lines on stdout/stderr (verified by redirecting test output)
- [ ] No `System.setErr` or `System.setOut` calls exist in test code
- [ ] No `System.err.println` calls exist in non-main() business logic methods