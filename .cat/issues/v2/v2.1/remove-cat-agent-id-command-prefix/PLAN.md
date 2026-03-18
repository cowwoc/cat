# Plan: remove-cat-agent-id-command-prefix

## Problem

Agents must currently embed `CAT_AGENT_ID=<id>` as a prefix in bash commands when removing worktrees or running
`rm -rf` on locked directories. `BlockUnsafeRemoval` parses this prefix from the command string to verify lock
ownership.

Claude Code 2.1.69 added `agent_id` (for subagents) and `agent_type` to hook events. Since `BlockUnsafeRemoval` is a
PreToolUse hook, it now receives `agent_id` in the hook input JSON via stdin. This makes the command-string prefix
protocol unnecessary — the hook can read the agent's identity directly from the hook event.

The current protocol wastes context tokens (instructions injected at SessionStart and SubagentStart explaining the
`CAT_AGENT_ID=` prefix format) and adds friction (agents must remember to prefix commands).

## Solution

Read `agent_id` from `HookInput` in the PreToolUse dispatcher and pass it to `BashHandler.check()`. In
`BlockUnsafeRemoval`, use this `agentId` parameter instead of parsing the `CAT_AGENT_ID=` prefix from the command
string. Remove all instructions telling agents to use the prefix.

### Agent ID Construction

The hook event provides:
- **Main agent:** `agent_id` is absent/empty; use `sessionId` as the agent identifier
- **Subagent:** `agent_id` is the native Claude agent ID (e.g., `task-abc123`)

The composite `catAgentId` used in lock files is `{sessionId}/subagents/{agent_id}` for subagents. The hook must
construct this composite from the two fields when comparing against lock file ownership.

## Satisfies
None (infrastructure simplification)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must handle main agent (no `agent_id` in hook event) and subagent (has `agent_id`) correctly.
  Lock files still store composite `catAgentId`; the hook must construct the same composite for comparison.
- **Mitigation:** The fallback for missing `agent_id` (use `sessionId`) matches the existing main-agent behavior.
  Comprehensive tests already exist from `2.1-fix-block-unsafe-removal-cross-session`.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/BashHandler.java` — add `agentId` parameter to `check()` method
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java` — read `agentId` from `HookInput`, construct
  composite catAgentId, pass to `BashHandler.check()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` — update `BashHandler.check()` call signature
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` — use `agentId` parameter instead of
  parsing `CAT_AGENT_ID=` from command string; remove `parseCatAgentIdPrefix()`, `CatAgentIdParse` record, and
  `CAT_AGENT_ID_PATTERN`
- All other `BashHandler` implementations — update `check()` signature (ignore the new parameter)
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java` — remove "Worktree Removal
  Safety (CAT_AGENT_ID Protocol)" section from injected instructions
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java` — remove `CAT_AGENT_ID` protocol
  instructions from subagent context
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnsafeRemovalTest.java` — update tests: commands no longer
  need `CAT_AGENT_ID=` prefix; pass `agentId` as parameter instead
- `CLAUDE.md` — remove "Worktree Removal Safety (CAT_AGENT_ID Protocol)" section

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Add `agentId` parameter to `BashHandler.check()`:** Add a new `String agentId` parameter. This is the composite
   catAgentId (e.g., `"sessionId"` for main agent, `"sessionId/subagents/agent_id"` for subagents).

   Files: `BashHandler.java`, all implementations

2. **Construct composite agentId in `PreToolUseHook`:** Read `input.getAgentId()`. If non-empty, construct
   `sessionId + "/subagents/" + agentId`. If empty, use `sessionId`. Pass this composite to `handler.check()`.

   Files: `PreToolUseHook.java`

3. **Update `PostToolUseHook`:** Same composite construction and updated `check()` call.

   Files: `PostToolUseHook.java`

4. **Refactor `BlockUnsafeRemoval` to use the `agentId` parameter:** Remove `parseCatAgentIdPrefix()`,
   `CatAgentIdParse`, and `CAT_AGENT_ID_PATTERN`. Use the `agentId` parameter directly for lock ownership comparison.
   Remove error messages about `CAT_AGENT_ID=` prefix and update guidance accordingly.

   Files: `BlockUnsafeRemoval.java`

5. **Remove `CAT_AGENT_ID` protocol instructions from session/subagent context:** Delete the "Worktree Removal Safety"
   section from `InjectSessionInstructions` and `SubagentStartHook`. Update `CLAUDE.md` to remove the corresponding
   section.

   Files: `InjectSessionInstructions.java`, `SubagentStartHook.java`, `CLAUDE.md`

6. **Update tests:** Modify `BlockUnsafeRemovalTest` to pass `agentId` as a method parameter instead of embedding
   `CAT_AGENT_ID=` in command strings. Verify all existing scenarios still pass with the new interface.

   Files: `BlockUnsafeRemovalTest.java`

7. **Run all tests:** `mvn -f client/pom.xml verify` — all tests must pass.

## Post-conditions
- [ ] `BashHandler.check()` accepts an `agentId` parameter
- [ ] `BlockUnsafeRemoval` reads agent identity from the `agentId` parameter, not from the command string
- [ ] No references to `CAT_AGENT_ID=` prefix remain in session instructions, subagent instructions, or CLAUDE.md
- [ ] Lock ownership comparison still works correctly for both main agent and subagents
- [ ] All existing `BlockUnsafeRemoval` test scenarios pass with the new interface
- [ ] `mvn -f client/pom.xml verify` passes

## Commit Type
refactor
