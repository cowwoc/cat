## Goal

Implement a two-phase hook enforcement mechanism that detects when the main agent completes an Agent tool invocation in work-with-issue context and blocks all subsequent Task/Skill tool calls and session Stop unless `collect-results-agent` (or equivalent) is invoked first. This enforces at the hook level what was previously only documented, preventing the M506 class of mistake (agent displays subagent JSON result and stops).

## Research Findings

**Hook Architecture (as-built):**
- `PostToolUseHook.java` — general dispatcher (no matcher, fires for all tools). Handlers implement `PostToolHandler.check(toolName, toolResult, sessionId, hookData)`. Returns warnings or additional context; cannot block (fires after tool).
- `PreIssueHook.java` — Task|Skill dispatcher. Handlers implement `TaskHandler.check(toolInput, sessionId, cwd)`. Can return `Result.block(reason)`.
- `EnforceStatusOutput.java` — Stop hook. Reads session JSON, checks for status box. Returns `block` if enforcement needed.
- `WorktreeContext.forSession(projectCatDir, projectDir, mapper, sessionId)` — returns non-null iff an active worktree lock exists for the session.
- Session base path: `scope.getSessionBasePath().resolve(sessionId)` — existing directory used for session tracking files.
- Skill name: read from `toolInput.get("skill")` (see `EnforceApprovalBeforeMerge`).
- Agent ID: read from `hookData.get("agent_id")` — empty for main agent, non-empty for subagents.

**Scope of enforcement:**
- Only applies to main agent (agent_id empty).
- Only when active worktree lock exists for session (work-with-issue context).
- Flag lifecycle: set by PostToolUse[Agent], cleared by PreIssueHook when `collect-results-agent` or `merge-subagent-agent` Skill is called.

## Rejected Alternatives

**General PreToolUse hook (no matcher, blocks all tools):** Would block Bash/Read/etc. between Agent completion and collect-results-agent, adding friction without meaningful benefit. Those read-only calls don't advance the workflow incorrectly; blocking them creates false positives. Rejected in favor of blocking only Task|Skill and Stop.

**Stop-hook-only enforcement:** Only fires when agent tries to terminate the turn. Does not prevent the agent from spawning wrong subagents or skipping to wrong phases via Skill. Retained only as a safety net alongside PreIssueHook blocking.

**Context-injection warning only (PostToolUse):** Documentation-level enforcement already failed (M506). A warning that the agent can ignore is not sufficient. Hard block required.

**Per-subagent-type detection in PostToolUse:** Checking `subagent_type` in the Agent tool output to limit flag to work-execute is fragile (subagent type not reliably available in tool result). Using presence of active worktree lock is sufficient and accurate.

## Execution Steps

### Step 1 — Create `SetPendingAgentResult.java`

Create `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java`.

- Package: `io.github.cowwoc.cat.hooks.tool.post`
- Implements `PostToolHandler`
- Constructor: `SetPendingAgentResult(JvmScope scope)`
- `check(toolName, toolResult, sessionId, hookData)`:
  1. If `toolName` != `"Agent"` (case-insensitive) → return `Result.allow()`
  2. Get `agentId` from `hookData.get("agent_id")`; if non-empty → return `Result.allow()` (subagent spawned subagent, not main agent)
  3. Call `WorktreeContext.forSession(scope.getProjectCatDir(), scope.getClaudeProjectDir(), scope.getJsonMapper(), sessionId)`; if null → return `Result.allow()` (not in work-with-issue context)
  4. Compute flag path: `scope.getSessionBasePath().resolve(sessionId).resolve("pending-agent-result")`
  5. Create parent dirs if needed, write flag file (empty content)
  6. Return `Result.allow()`
  7. Catch `IOException` and `RuntimeException` → log and return `Result.allow()` (fail-open: never break the Agent tool)
- License header required (see `.claude/rules/license-header.md`)

### Step 2 — Create `EnforceCollectAfterAgent.java`

Create `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`.

- Package: `io.github.cowwoc.cat.hooks.task`
- Implements `TaskHandler`
- Constructor: `EnforceCollectAfterAgent(JvmScope scope)`
- `check(toolInput, sessionId, cwd)`:
  1. Compute flag path: `scope.getSessionBasePath().resolve(sessionId).resolve("pending-agent-result")`
  2. If flag file does not exist → return `Result.allow()`
  3. Read `skill` from `toolInput.get("skill")` (may be null → empty string)
  4. Read `subagent_type` from `toolInput.get("subagent_type")` (may be null → empty string)
  5. If `skill` is `"cat:collect-results-agent"` or `"cat:merge-subagent-agent"`:
     - Delete the flag file (ignore `IOException`)
     - Return `Result.allow()`
  6. Otherwise: return `Result.block(reason)` where `reason` is:
     ```
     BLOCKED: Agent tool result has not been processed.

     The previous Agent tool invocation completed but collect-results-agent was not called.

     Required next step: Invoke collect-results-agent before any other Task or Skill call.

     Correct invocation:
       Skill tool: skill="cat:collect-results-agent"
       Arguments: "<cat_agent_id> <issue_path> <subagent_commits_json>"

     See plugin/skills/collect-results-agent/SKILL.md for argument details.

     Attempted tool: <toolName> (<skill or subagent_type or "no skill/subagent_type">)
     ```
     Fill in the `<toolName>` from the PreIssueHook's `toolName` context (pass as constructor parameter or read from hookData — see note below).

  **Note on toolName in the block reason:** `TaskHandler.check()` does not receive `toolName`. Pass a `toolName` String to the constructor, OR reconstruct from `toolInput` (if `subagent_type` non-empty → "Task", if `skill` non-empty → "Skill"). Use the skill/subagent_type to describe what was attempted.
- License header required

### Step 3 — Register `SetPendingAgentResult` in `PostToolUseHook.java`

Modify `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java`:
- Add import: `io.github.cowwoc.cat.hooks.tool.post.SetPendingAgentResult`
- Add to `handlers` list (before `ResetFailureCounter`, so it fires first):
  ```java
  new SetPendingAgentResult(scope),
  ```
  Position matters only for ordering; place at the beginning to ensure flag is set before other handlers run.

### Step 4 — Register `EnforceCollectAfterAgent` in `PreIssueHook.java`

Modify `client/src/main/java/io/github/cowwoc/cat/hooks/PreIssueHook.java`:
- Add import: `io.github.cowwoc.cat.hooks.task.EnforceCollectAfterAgent`
- Add to `handlers` list as the **first** handler (before `EnforceCommitBeforeSubagentSpawn`):
  ```java
  new EnforceCollectAfterAgent(scope),
  ```
  First position ensures enforcement fires before other validation that might allow the call through.

### Step 5 — Add pending-flag check to `EnforceStatusOutput.java`

Modify `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`:

In the `check(mapper, transcriptPath, stopHookActive, hookOutput)` method, after reading `sessionId` from input:
1. Compute flag path from `sessionId` using the session base path
2. If flag file exists: return `hookOutput.block(reason)` where reason is:
   ```
   BLOCKED: Agent tool result has not been processed.

   The previous Agent tool invocation completed but collect-results-agent was not called.
   You cannot end your turn until you have collected the subagent result.

   Required next step: Invoke collect-results-agent:
     Skill tool: skill="cat:collect-results-agent"
     Arguments: "<cat_agent_id> <issue_path> <subagent_commits_json>"

   See plugin/skills/collect-results-agent/SKILL.md for argument details.
   ```
3. The existing status box enforcement logic follows (do not remove it).

**Session base path in EnforceStatusOutput:** `EnforceStatusOutput` currently uses `JvmScope` via `HookRunner.execute`. Access session base path via `scope.getSessionBasePath()` from within the `check()` method — refactor signature to pass `scope` as a parameter, or compute the path using `Path.of(System.getProperty("user.home"), ".config", "claude", "projects", "-workspace")` if `scope` is not available. Prefer passing `scope` through for consistency.

### Step 6 — Write unit tests

Create test class `client/src/test/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResultTest.java`:
- Test: Agent tool with empty agentId and active lock → creates flag file
- Test: Agent tool with non-empty agentId → no flag file created
- Test: Non-Agent tool → no flag file created
- Test: Agent tool with no active lock (WorktreeContext returns null) → no flag file
- Test: IOException on flag write → returns allow (fail-open)

Create test class `client/src/test/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgentTest.java`:
- Test: No flag file → allow
- Test: Flag exists + Skill[collect-results-agent] → delete flag, allow
- Test: Flag exists + Skill[merge-subagent-agent] → delete flag, allow
- Test: Flag exists + Skill[work-merge-agent] → block with error message
- Test: Flag exists + Task[cat:work-execute] → block with error message
- Test: Error message contains "collect-results-agent", "BLOCKED", and the attempted tool name

### Step 7 — Run all tests

```bash
mvn -f client/pom.xml test
```

All tests must pass before marking implementation complete.

### Step 8 — Update STATE.md

Update `status: open` → `status: closed` and `progress: 0%` → `progress: 100%` in the issue's STATE.md.

## Success Criteria

- `SetPendingAgentResult` creates `{sessionBasePath}/{sessionId}/pending-agent-result` when Agent tool completes in work-with-issue context on the main agent
- `EnforceCollectAfterAgent` blocks Task and Skill calls when flag exists, except for `cat:collect-results-agent` and `cat:merge-subagent-agent`
- `EnforceCollectAfterAgent` clears the flag when `cat:collect-results-agent` or `cat:merge-subagent-agent` is called
- Stop hook blocks session termination when flag exists
- Block error messages include: the phrase "collect-results-agent", "BLOCKED", the attempted tool name, and correct invocation syntax
- All new unit tests pass
- `mvn -f client/pom.xml test` exits 0
- No regressions in existing tests
