# Plan: remove-cache-fix-integration

## Goal

Remove all `claude-code-cache-fix` integration from `ClaudeRunner`. The cache fix was added to improve prompt
caching in nested Claude instances, but it is unnecessary: nested instances communicate via `ANTHROPIC_BASE_URL`,
which means caching behaviour is controlled at the API level and the Node.js-level cache patch has no effect on
the native bun binary. Removing this integration simplifies `ClaudeRunner` and eliminates the build-time warning
about missing `NODE_OPTIONS`.

## Parent Requirements

None — infrastructure cleanup

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Cache performance in nested instances may be marginally affected if the fix was providing any
  benefit; however, since the binary is now native bun (not Node.js), `NODE_OPTIONS` has no effect anyway
- **Mitigation:** No functional change in actual caching; warning is removed rather than a feature

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java` — remove
  `isCacheFixDetected()`, the warning block in `resolveClaudeBinary`, and any references to `NODE_OPTIONS`
  for cache-fix detection; remove the `resolveClaudeBinary` Javadoc reference to the cache fix
- `client/src/test/java/io/github/cowwoc/cat/client/test/EmpiricalTestRunnerTest.java` — remove any tests
  that validate cache-fix warning emission or `isCacheFixDetected` behaviour
- `plugin/skills/claude-runner/SKILL.md` — remove any instructions referencing `claude-code-cache-fix` or
  `NODE_OPTIONS` cache-fix detection (if present)
- Open issue `use-cache-fix-for-claude-runner` — close it as superseded (the feature it planned is now
  being removed rather than completed)

## Pre-conditions

- [ ] `fix-claude-runner-native-binary` is closed

## Jobs

### Job 1: Remove cache-fix code from ClaudeRunner

- Delete `isCacheFixDetected(String nodeOptions)` method entirely
- In `resolveClaudeBinary`, remove the `NODE_OPTIONS` read, the `isCacheFixDetected` call, and the
  `stderr.println` warning block; simplify to just read and return `CLAUDE_CODE_EXECPATH`
- Remove `PrintStream stderr` parameter from `resolveClaudeBinary` (no longer needed); update callers
- Remove the `stderr` import if it becomes unused
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java`

### Job 2: Remove cache-fix tests

- Remove `isCacheFixDetectedReturnsTrueWhenPresent`, `isCacheFixDetectedReturnsFalseWhenAbsent`,
  `isCacheFixDetectedReturnsFalseWhenNull`, and any other tests specifically for `isCacheFixDetected`
  or the cache-fix warning
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/test/EmpiricalTestRunnerTest.java`

### Job 3: Remove cache-fix references from plugin skill

- Inspect `plugin/skills/claude-runner/SKILL.md` for any `claude-code-cache-fix` or `NODE_OPTIONS`
  cache-fix references and remove them
  - Files: `plugin/skills/claude-runner/SKILL.md`

### Job 4: Close superseded issue

- Mark `use-cache-fix-for-claude-runner` index.json status as `"closed"` with a note that it was
  superseded by `remove-cache-fix-integration`
  - Files: `.cat/issues/v2/v2.1/use-cache-fix-for-claude-runner/index.json`

## Post-conditions

- [ ] `isCacheFixDetected` method no longer exists in `ClaudeRunner`
- [ ] No `claude-code-cache-fix` or `NODE_OPTIONS` cache-fix references remain in `ClaudeRunner.java`
- [ ] `mvn -f client/pom.xml verify -e` exits 0
- [ ] `use-cache-fix-for-claude-runner` issue is closed
