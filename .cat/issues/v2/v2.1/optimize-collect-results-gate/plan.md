# PLAN: optimize-collect-results-gate

## Goal

Skip the `collect-results-agent` gate for Agent/Task calls whose `subagent_type` is not `cat:work-execute`.
The gate currently fires after every Agent tool completion when a worktree lock is active, including
adversarial agents (red-team, blue-team, diff-validation) that run without `isolation: worktree`. Each
unnecessary gate invocation costs ~17s in LLM inference. The fix records the `subagent_type` of the completed Agent call
and skips the gate unless it is `cat:work-execute`, which produces worktree artifacts needing collection.

---

## Context

### How the gate works today

1. **PostToolUse – `SetPendingAgentResult`**
   (`client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java`)
   When the main agent (empty `agent_id`) completes an `Agent` tool call and an active worktree lock
   exists, this handler creates a flag file at:
   `{sessionBasePath}/{sessionId}/pending-agent-result`
   It does not inspect `isolation` in `tool_input` — it fires for every Agent call unconditionally.

2. **PreToolUse – `EnforceCollectAfterAgent`**
   (`client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`)
   Blocks all subsequent `Task`/`Skill` invocations while `pending-agent-result` exists.
   Clears the flag only when `cat:collect-results-agent` or `cat:merge-subagent-agent` is called.

3. **The PostToolUse hook dispatcher** (`PostToolUseHook.java`) passes `input.getRaw()` as `hookData`
   to each handler. `hookData` contains `tool_input` at the top level, which includes the `subagent_type`
   field for the Agent call (e.g. `"subagent_type": "cat:work-execute"`).

### The problem

Adversarial agents (red-team, blue-team, diff-validation) have `subagent_type` values other than `cat:work-execute`.
They complete, the flag is written, and the next Skill/Task is blocked until `collect-results-agent`
runs. `collect-results-agent` finds nothing to collect but still incurs the full LLM round-trip cost
(~17s per call). The gate was designed to track worktree subagent commits from `cat:work-execute` — it is meaningless
for other agent types.

### The fix

In `SetPendingAgentResult.check()`, inspect `hookData.get("tool_input").get("subagent_type")` before
writing the flag file. Only write the flag when `subagent_type` equals `"cat:work-execute"` (case-insensitive).
When `subagent_type` is absent, null, or any other value, skip flag creation — the gate is unnecessary.

No changes are needed in `EnforceCollectAfterAgent` or `PreIssueHook` — the gate logic is correct;
only the condition under which the flag is set needs tightening.

---

## Files to Modify

| File | Change |
|------|--------|
| `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java` | Add `subagent_type: cat:work-execute` check before writing flag |
| `client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java` | Add tests for new subagent-type-gating behavior |

**No other files require changes.** `EnforceCollectAfterAgent`, `PreIssueHook`, and `PostToolUseHook`
are unaffected.

---

## Sub-Agent Waves

### Wave 1

### Step 1 — Write failing tests in `SetPendingAgentResultTest`

Add the following test methods to
`client/src/test/java/io/github/cowwoc/cat/hooks/test/SetPendingAgentResultTest.java`:

**Test A — `agentToolWithWorkExecuteSubagentCreatesFlagFile`**
- Setup: active lock + worktree dir (reuse `createLockFile` + `createWorktreeDir` helpers)
- `hookData` includes `"tool_input": {"subagent_type": "cat:work-execute"}`
- Assert flag file exists after `handler.check("Agent", toolResult, sessionId, hookData)`

**Test B — `agentToolWithNoSubagentTypeDoesNotCreateFlagFile`**
- Setup: active lock + worktree dir
- `hookData` includes `"tool_input": {}` (no `subagent_type` field)
- Assert flag file does NOT exist

**Test C — `agentToolWithNonWorkExecuteSubagentDoesNotCreateFlagFile`**
- Setup: active lock + worktree dir
- `hookData` includes `"tool_input": {"subagent_type": "cat:red-team-agent"}`
- Assert flag file does NOT exist

**Test D — `agentToolWithWorkExecuteSubagentCaseInsensitiveCreatesFlagFile`** (optional robustness)
- `hookData` includes `"tool_input": {"subagent_type": "CAT:WORK-EXECUTE"}`
- Assert flag file IS created (confirms case-insensitive check)

Existing tests must continue to pass. The existing test
`agentToolWithEmptyAgentIdAndActiveLockCreatesFlagFile` passes `hookData` without `tool_input`
(it uses `{"session_id": "test"}`). After this change, that test's `hookData` will no longer trigger
flag creation because `tool_input` is absent. **Update that test** to include
`"tool_input": {"subagent_type": "cat:work-execute"}` so it continues to verify the primary "flag is created"
contract.

Run tests to confirm all new tests fail (red):
```bash
mvn -f client/pom.xml test -Dtest=SetPendingAgentResultTest
```

### Step 2 — Implement the fix in `SetPendingAgentResult`

Edit
`client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java`.

After the existing check `if (context == null) return Result.allow();`, add:

```java
// Only enforce collect-results gate for cat:work-execute subagents.
// Other agent types (adversarial, red-team, blue-team, diff-validation) produce no worktree artifacts.
JsonNode toolInputNode = hookData != null ? hookData.get("tool_input") : null;
boolean isWorkExecute = false;
if (toolInputNode != null)
{
  JsonNode subagentTypeNode = toolInputNode.get("subagent_type");
  if (subagentTypeNode != null && subagentTypeNode.isString())
    isWorkExecute = "cat:work-execute".equalsIgnoreCase(subagentTypeNode.asString());
}
if (!isWorkExecute)
  return Result.allow();
```

The existing flag-write code follows unchanged.

**Important:** The `hookData` parameter in `check(String toolName, JsonNode toolResult, String sessionId,
JsonNode hookData)` is the full raw hook JSON (passed from `input.getRaw()` in `PostToolUseHook`). It
contains `tool_input` at the top level. Do NOT look inside `toolResult` for `subagent_type` — that is the
Agent's output, not input.

### Step 3 — Run all tests

```bash
mvn -f client/pom.xml test
```

All tests must pass. Specifically verify:
- All new `SetPendingAgentResultTest` tests pass (agentToolWithWorkExecuteSubagentCreatesFlagFile,
  agentToolWithNoSubagentTypeDoesNotCreateFlagFile, agentToolWithNonWorkExecuteSubagentDoesNotCreateFlagFile,
  and the case-insensitive test)
- The updated existing test `agentToolWithEmptyAgentIdAndActiveLockCreatesFlagFile` passes
- All existing `EnforceCollectAfterAgentTest` tests pass (no changes to that class)
- No regressions in other hook tests

### Step 4 — Verify the Javadoc is accurate

In `SetPendingAgentResult.java`, the class-level Javadoc says:
> When the main agent (not a subagent) completes an Agent tool invocation and an active worktree lock
> exists for the session, this handler creates a flag file ...

Update the Javadoc to mention the `subagent_type: cat:work-execute` precondition:
> When the main agent (not a subagent) completes an Agent tool invocation **with `subagent_type: cat:work-execute`**
> and an active worktree lock exists for the session, this handler creates a flag file ...

---

## Edge Cases

| Case | Expected Behavior |
|------|------------------|
| `tool_input` absent from `hookData` | No flag created (treated as non-work-execute) |
| `tool_input` present but `subagent_type` field absent | No flag created |
| `subagent_type` = `"cat:red-team-agent"` | No flag created |
| `subagent_type` = `"cat:work-execute"` (lowercase) | Flag created |
| `subagent_type` = `"CAT:WORK-EXECUTE"` (uppercase) | Flag created (case-insensitive) |
| `subagent_type` present but node type is not string (e.g. null JSON node) | No flag created |
| `hookData` itself is null | No flag created (null guard on `hookData`) |
| Main agent + active lock + `subagent_type: cat:work-execute` | Flag created (existing behavior preserved) |
| Subagent (non-empty `agent_id`) + `subagent_type: cat:work-execute` | No flag (subagent guard fires first) |

---

## Post-conditions

1. `mvn -f client/pom.xml test` exits 0 with all tests green.
2. New tests `agentToolWithWorkExecuteSubagentCreatesFlagFile`,
   `agentToolWithNoSubagentTypeDoesNotCreateFlagFile`, and
   `agentToolWithNonWorkExecuteSubagentDoesNotCreateFlagFile` exist and pass.
3. Existing test `agentToolWithEmptyAgentIdAndActiveLockCreatesFlagFile` has been updated to include
   `"subagent_type": "cat:work-execute"` in `tool_input` and continues to pass.
4. `SetPendingAgentResult.java` contains `isWorkExecute` logic gating the flag write on
   `"cat:work-execute".equalsIgnoreCase(subagent_type)`.
5. Javadoc for `SetPendingAgentResult` mentions `subagent_type: cat:work-execute` as a precondition.
6. `EnforceCollectAfterAgent.java` is unchanged.
7. `PreIssueHook.java` is unchanged.

---

## Out of Scope

- Changing how `EnforceCollectAfterAgent` clears the flag — the clearing logic is correct.
- Adding new hook matchers or modifying `hooks.json`.
- Changing behavior of the Task tool (vs. Agent tool) — the `SetPendingAgentResult` handler already
  only fires for the `Agent` tool name.
- Migrating existing flag files — the flag is transient (exists only within a session); no migration
  needed.
