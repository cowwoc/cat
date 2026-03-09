# Plan: fix-work-complete-agent-argument-format

## Problem

`work.md` documents invoking `work-complete-agent` with positional arguments:

```
/cat:work-complete-agent ${CLAUDE_SESSION_ID} ${issue_id} ${target_branch}
```

However `GetNextIssueOutput.parseArgs()` expects named flags (`--session-id`, `--completed-issue`,
`--target-branch`, `--project-dir`) and silently ignores positional arguments, which leaves `sessionId`
empty. An empty `sessionId` is then used in `IssueLock` operations, producing lock files with
`session_id='00000000'` that cannot be properly owned or released.

## Parent Requirements

None

## Reproduction Code

```
# Invoke work-complete-agent with positional args as documented in work.md:
/cat:work-complete-agent ${CLAUDE_SESSION_ID} ${issue_id} ${target_branch}

# Inside work-complete-agent first-use.md:
#   INVOKE: Skill("cat:get-output-agent", args="work-complete $1 $2")
# Expands to: work-complete <issue_id> <target_branch>
# Routes to GetIssueCompleteOutput (not GetNextIssueOutput)

# If the intent was to call GetNextIssueOutput:
#   --session-id <uuid> --completed-issue <id> --target-branch <branch>
# But positional args are silently ignored by GetNextIssueOutput.parseArgs()
```

## Expected vs Actual

- **Expected:** `work.md` and `GetNextIssueOutput` use the same argument format; lock release uses
  real session ID
- **Actual:** Documentation uses positional args; `GetNextIssueOutput.parseArgs()` expects named flags;
  mismatch causes empty `sessionId` in lock operations → `session_id='00000000'` in lock files

## Root Cause

`GetNextIssueOutput.parseArgs()` (lines 63–87 of `GetNextIssueOutput.java`) iterates args in pairs
matching `--key value`. Positional arguments have no `--` prefix and fall into the `default ->` branch
which silently ignores them. `sessionId` is never populated, so it remains `""`. The fallback
`scope.getClaudeSessionId()` rescues it only in `getOutput()`, not in `main()`. In the agent-invoked
path (via `GetOutput → GetIssueCompleteOutput`), `GetNextIssueOutput` is bypassed entirely, so the
lock release and session validation in that class never runs.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Clarifying documentation does not change runtime behavior; adding a fail-fast
  validation in `GetNextIssueOutput.getOutput()` will surface misconfigured callers immediately instead
  of silently corrupting lock state
- **Mitigation:** Existing `GetNextIssueOutputTest` suite covers normal behavior; new tests cover
  the fail-fast path

## Research Findings

Deep research revealed that there are two distinct completion paths:

1. **`get-output-agent work-complete $1 $2`** (current path in `work-complete-agent/first-use.md`):
   Routes to `GetIssueCompleteOutput.discoverAndRender(issueName, targetBranch)`. This path does NOT
   call `IssueLock.release()` — the lock is released atomically by the `merge-and-cleanup` Java tool
   in `work-merge-agent` before `work-complete-agent` is even invoked.

2. **`GetNextIssueOutput`** (a different SkillOutput for the `get-next-issue-box` directive): Has its
   own `releaseLock()` logic and named-flag argument parsing. If called with positional args, the
   `sessionId` would be empty.

The `session_id='00000000'` in production locks is more likely caused by `CLAUDE_SESSION_ID`
environment variable being inconsistently set across bash tool calls in Claude Code (as observed in
M523/M524). Regardless, the fix below addresses both the documentation inconsistency and the missing
fail-fast guard.

## Impact Notes

Root cause of M524 — corrupted locks blocked multiple sessions. Discovered during cleanup session
2026-03-09.

## Files to Modify

- `plugin/skills/work/first-use.md` — clarify `work-complete-agent` invocation with correct format
  showing `${CLAUDE_SESSION_ID} ${issue_id} ${target_branch}` positional args (already correct) and
  note that skill-loader maps `$0=catAgentId, $1=completedIssue, $2=targetBranch`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` — add fail-fast
  validation: if `sessionId` is empty after argument parsing AND `scope.getClaudeSessionId()` fallback
  is also empty or matches the placeholder pattern `000000*`, throw `IllegalArgumentException` with
  a clear error message including the received args
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetNextIssueOutputTest.java` — add test for
  the fail-fast path when sessionId is empty

## Test Cases

- [ ] `GetNextIssueOutput.getOutput()` throws with clear message when `--session-id` is absent and
  `CLAUDE_SESSION_ID` is also unset
- [ ] `GetNextIssueOutput.getOutput()` succeeds with valid `--session-id` flag (no regression)
- [ ] `GetNextIssueOutput.main()` prints usage and exits 1 when args are missing

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add fail-fast validation in `GetNextIssueOutput.getOutput()`: after the `sessionId` fallback to
  `scope.getClaudeSessionId()`, if `sessionId` is still blank, throw:
  ```
  IllegalArgumentException("GetNextIssueOutput: sessionId is empty after argument parsing. " +
    "Pass --session-id <uuid> explicitly. Received args: " + Arrays.toString(args))
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`

- Add test `getOutput_throwsWhenSessionIdMissing()` to `GetNextIssueOutputTest.java` that creates a
  `TestJvmScope` with no env values, calls `getOutput(new String[0])`, and expects
  `IllegalArgumentException` with message containing "sessionId is empty"
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetNextIssueOutputTest.java`

### Wave 2

- Run `mvn -f client/pom.xml verify` and confirm all tests pass (including the new fail-fast test)

- Update `plugin/skills/work/first-use.md` `## Next Issue` section to explicitly document the argument
  mapping: add a comment after the invocation showing `# $0=catAgentId, $1=completedIssue,
  $2=targetBranch` so future contributors understand how SkillLoader maps positional args
  - Files: `plugin/skills/work/first-use.md`

- Update STATE.md: status → closed, progress → 100%

## Post-conditions

- [ ] `GetNextIssueOutput.getOutput()` throws `IllegalArgumentException` with a message containing
  "sessionId is empty" when no `--session-id` flag is provided and `CLAUDE_SESSION_ID` is not set
- [ ] All existing `GetNextIssueOutputTest` tests still pass (no regressions)
- [ ] `mvn -f client/pom.xml verify` exits 0
- [ ] `work/first-use.md` documents the catAgentId/$0/$1/$2 positional mapping inline near the
  `work-complete-agent` invocation
