# Plan

## Goal

Update work-agent to skip non-stale locked issues and find the next available issue automatically,
silently. work-prepare should expose a `stale: bool` field in its ERROR response (based on
IssueLock.STALE_LOCK_THRESHOLD = 4 hours), so work-agent can decide whether to skip or offer cleanup
without duplicating the threshold.

When work-prepare returns ERROR with an existing worktree locked by another session:
- If `stale == false` (lock_age_seconds < 14400): work-agent silently retries work-prepare to find
  the next available issue, without presenting any AskUserQuestion dialog
- If `stale == true` (lock_age_seconds >= 14400): work-agent preserves the existing cleanup-offer
  behavior (Clean up and retry / Abort)

## Pre-conditions

(none)

## Post-conditions

- [x] Bug fixed: work-agent no longer offers cleanup when encountering a non-stale locked issue
- [x] work-agent automatically retries work-prepare to find the next available issue when it receives
      ERROR with `stale == false`, without presenting any AskUserQuestion dialog
- [x] work-prepare ERROR response (existing worktree locked by another session) includes a boolean
      `stale` field, set to `true` when `lock_age_seconds >= 14400`, `false` otherwise
- [x] Staleness threshold sourced exclusively from `IssueLock.STALE_LOCK_THRESHOLD` in Java — no
      hardcoded `14400` appears in skill Markdown code
- [x] Regression test added: work-agent skips non-stale locked issues and finds next available issue
- [x] Regression test added: work-agent preserves the cleanup-offer behavior when `stale == true`
- [x] Regression test added: work-agent stops cleanly when retry cap is exhausted (all issues locked)
- [ ] No new issues introduced
- [ ] E2E verification: reproduce the scenario (work-prepare returns ERROR with `stale == false`) and
      confirm work-agent automatically finds the next available issue without any dialog

## Research Findings

### Java side: WorkPrepare.java

File: `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/WorkPrepare.java`

In the `ExistingWorktree` block (~line 419–436), when `lockCheck instanceof IssueLock.LockResult.CheckLocked locked`
and `locked.sessionId()` is NOT the current session and `resume == false`, the ERROR response is built:

```java
Map<String, Object> errorResult = new LinkedHashMap<>();
errorResult.put("status", "ERROR");
errorResult.put("message", "Issue " + existingWorktree.issueId() +
  " has an existing worktree locked by another session");
errorResult.put("issue_id", existingWorktree.issueId());
errorResult.put("locked_by", locked.sessionId());
errorResult.put("lock_age_seconds", locked.ageSeconds());
errorResult.put("worktree_path", existingWorktree.worktreePath());
return mapper.writeValueAsString(errorResult);
```

The fix: add a `stale` boolean field before `return`, computed as:
```java
errorResult.put("stale", locked.ageSeconds() >= IssueLock.STALE_LOCK_THRESHOLD.toSeconds());
```

`IssueLock.STALE_LOCK_THRESHOLD` is `Duration.ofHours(4)` at line 74 of `IssueLock.java`.
`Duration.toSeconds()` returns the total duration in seconds (14400).

### Java tests: WorkPrepareTest.java

File: `client/src/test/java/io/github/cowwoc/cat/client/test/WorkPrepareTest.java`

Existing test at line ~669: `executeReturnsErrorWithLockedByWhenForeignSessionLockAndWorktreeExists`
- Uses `recentTimestamp = Instant.now().getEpochSecond()` (non-stale, `stale: false`)
- Currently does NOT assert the `stale` field — add assertion: `stale` == `false`

New test to add: `executeReturnsErrorWithStaleFieldTrueWhenLockIsOldAndWorktreeExists`
- Same setup but with `oldTimestamp = Instant.now().getEpochSecond() - IssueLock.STALE_LOCK_THRESHOLD.toSeconds() - 1`
- Assert `stale` == `true`

### Skill side: work-agent/first-use.md

File: `plugin/skills/work-agent/first-use.md`

The "ERROR: Existing Worktree Handling" section (around line 189) controls what happens when
`work-prepare` returns ERROR referencing an existing worktree.

Currently: always displays the error verbatim and offers an AskUserQuestion with cleanup options.

New behavior: Before displaying the error or showing a dialog, check the `stale` field:
- If `stale == false`: log a note that the issue is actively locked by another session, then
  silently call work-prepare again (same ARGUMENTS) to find the next available issue. This is a
  single silent retry — if the next call also returns ERROR with `stale == false`, it means another
  issue is also locked. The retry loop continues until work-prepare returns a non-ERROR-stale-false
  result (READY, NO_ISSUES, LOCKED, or ERROR with `stale == true` or no `stale` field). Cap at 10
  retries to prevent infinite loops.
- If `stale == true` or `stale` field absent: preserve existing behavior (display verbatim + AskUserQuestion)

The check should happen in the "ERROR (existing worktree)" handler, BEFORE the existing
AskUserQuestion display logic. The distinction is based on whether the ERROR response has
`locked_by` present (locked by another session) AND `stale == false`.

### Skill tests: work-agent

Directory: `plugin/tests/skills/work-agent/first-use/`

Three test files (TC1–TC4, TC2 is reserved/unused):
1. `offer-cleanup-for-stale-locked-issue.md` (TC1) — verifies work-agent shows cleanup dialog when stale=true
2. `retry-cap-exhausted.md` (TC3) — verifies work-agent stops cleanly with a message when all 10 retries are exhausted
3. `skip-non-stale-locked-issue.md` (TC4) — verifies work-agent silently retries when stale=false

## Jobs

### Job 1
- In `WorkPrepare.java`, add `stale` boolean to the ERROR response for "locked by another session
  with existing worktree" — after `errorResult.put("lock_age_seconds", locked.ageSeconds())`, add:
  `errorResult.put("stale", locked.ageSeconds() >= IssueLock.STALE_LOCK_THRESHOLD.toSeconds())`
- In `WorkPrepareTest.java`, update `executeReturnsErrorWithLockedByWhenForeignSessionLockAndWorktreeExists`
  to assert `stale == false`
- In `WorkPrepareTest.java`, add test `executeReturnsErrorWithStaleFieldTrueWhenLockIsOldAndWorktreeExists`
  that creates a lock with `oldTimestamp = Instant.now().getEpochSecond() - IssueLock.STALE_LOCK_THRESHOLD.toSeconds() - 1`
  and asserts `stale == true`
- Run `mvn -f client/pom.xml verify -e` and confirm all tests pass
- Update index.json: status=closed, progress=100%

### Job 2
- In `plugin/skills/work-agent/first-use.md`, updated the "ERROR: Existing Worktree Handling" section
  with a decision table and explicit WRONG/CORRECT examples covering:
  - If ERROR response contains `locked_by` AND `stale == false`: silently retry work-prepare, cap at
    10 retries (1 initial + 9 silent), no output, no dialog. On cap exhaustion: display
    "All available issues are actively locked by other sessions." and stop.
  - If `stale == true`, `stale` absent, or `locked_by` absent: display full ERROR JSON verbatim as
    first action, then offer AskUserQuestion (Clean up and retry / Abort).
- Created `plugin/tests/skills/work-agent/first-use/offer-cleanup-for-stale-locked-issue.md` (TC1)
- Created `plugin/tests/skills/work-agent/first-use/retry-cap-exhausted.md` (TC3)
- Created `plugin/tests/skills/work-agent/first-use/skip-non-stale-locked-issue.md` (TC4)
- SPRT testing with claude-haiku-4-5 validates all three scenarios; iterative fixes applied to
  first-use.md during testing (decision table, WRONG examples, retry-semantics explanation)
- To satisfy **No new issues introduced**, add focused regression coverage in
  `client/src/test/java/io/github/cowwoc/cat/client/test/WorkPrepareTest.java` and
  `client/src/test/java/io/github/cowwoc/cat/client/test/GetAddOutputPlanningDataTest.java` for the
  stale-lock skip path and planning-data output shape, so the finalized behavior is pinned by tests.
- To satisfy **Unit tests pass**, fix the four currently failing unit tests by updating the impacted
  implementation and expectations in
  `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java`,
  `client/src/test/java/io/github/cowwoc/cat/client/test/WorkPrepareTest.java`,
  `client/src/test/java/io/github/cowwoc/cat/client/test/GetAddOutputPlanningDataTest.java`, and
  `client/src/test/java/io/github/cowwoc/cat/client/test/InstructionTestRunnerMainTest.java`.
- To satisfy **E2E failed**, remove the missing-binary failure mode by adding a codex-runtime build
  prerequisite in `client/pom.xml` that produces
  `client/cli/target/jlink/codex/bin/instruction-test-runner` before runtime-native invocation.
- To satisfy **E2E verification: reproduce the scenario...**, extend
  `plugin/tests/skills/work-agent/first-use/skip-non-stale-locked-issue.md` with a deterministic
  runtime-E2E case that asserts silent retry on `stale == false`, no AskUserQuestion, and successful
  selection of the next available issue.

### Job 3
- In `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ModelIdResolver.java`, add a
  runtime-safe version resolver used by test runners that does not hard-fail when `claude` is
  unavailable in codex runtime. Keep the existing strict `detectClaudeCodeVersion()` path for
  claude-runner, and add a fallback mapping version derived from `VERSION_MAPPINGS.lastKey()`.
- In `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java`,
  replace the unconditional `ModelIdResolver.detectClaudeCodeVersion()` call in `run(...)` with the
  new runtime-safe resolver so codex `instruction-test-runner` can execute without `claude --version`.
- In `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java`, retain
  strict version detection for the `resolve-model` subcommand but route through shared resolver
  helpers so parsing/validation behavior is consistent with `ModelIdResolver`.
- In `client/src/test/java/io/github/cowwoc/cat/client/test/ModelIdResolverTest.java`, add coverage
  for the runtime-safe fallback path (missing `claude` binary returns latest mapping version instead
  of throwing) and preserve existing strict-failure assertions for `detectClaudeCodeVersion()`.
- In `client/src/test/java/io/github/cowwoc/cat/client/test/InstructionTestRunnerMainTest.java`, add
  an integration-style test that runs `InstructionTestRunner.run(...)` in a codex-like environment
  without a `claude` binary and verifies it reaches command validation/business error handling instead
  of failing on version detection.
- In `client/pom.xml`, ensure the codex jlink runtime test phase includes the binaries required by
  runtime-native E2E (instruction-test-runner and any runtime-specific CLI wrappers referenced by the
  new resolver path) so tests do not depend on host-global executables.
- Re-run runtime E2E with
  `/home/node/.cat/worktrees/2.1-fix-work-agent-skip-active-locks/client/cli/target/jlink/codex/bin/instruction-test-runner`
  against `plugin/tests/skills/work-agent/first-use/skip-non-stale-locked-issue.md` and capture
  evidence that the scenario completes without any AskUserQuestion prompt.

### Job 4
- For the requirements concern (runtime E2E post-condition still unverified), extend
  `plugin/tests/skills/work-agent/first-use/skip-non-stale-locked-issue.md` with an explicit
  runtime fixture contract for deterministic execution (named turn files and expected replay order),
  then run
  `/home/node/.cat/worktrees/2.1-fix-work-agent-skip-active-locks/client/cli/target/jlink/codex/bin/instruction-test-runner`
  against that test and record passing evidence for stale=false silent retry/no-dialog behavior.
- For the testing concern (missing `tc4_turn1.md` fixture), add
  `plugin/tests/skills/work-agent/first-use/tc4_turn1.md` containing the stale=false initial error
  payload (or inline equivalent in the scenario if fixture loading is not required by the runner),
  then re-run the same runtime E2E test to confirm assertions execute instead of failing in setup.
- For the security concern (`ModelIdResolver` over-broad fallback), modify
  `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ModelIdResolver.java` so
  `detectClaudeCodeVersionOrLatestMapping()` falls back only when the `claude` executable is
  unavailable; parse errors, malformed output, and non-zero exit failures must propagate as errors.
  Add/adjust tests in
  `client/src/test/java/io/github/cowwoc/cat/client/test/ModelIdResolverTest.java` for: missing
  executable => fallback allowed, malformed output => failure, non-zero exit => failure.
- For the architecture concern (boolean `resume` controlling foreign-lock takeover), refactor
  `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/WorkPrepare.java` and associated call
  sites/tests to replace `resume` with an explicit lock-conflict strategy type (for example, skip,
  prompt-cleanup, force-reclaim), and enforce stale-threshold plus ownership preconditions inside
  `WorkPrepare` before any force-release path is permitted.
