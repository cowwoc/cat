# Plan: Drop catAgentId — Use Claude Code's Native agent_id

## Goal
Replace the CAT-invented `catAgentId` concept with Claude Code's native `agent_id`, giving each agent its own
skill-loaded marker file without any custom ID generation. Rename the parameter from `catAgentId` to `agentId`
throughout.

## Satisfies
None (infrastructure improvement)

## Background

`SkillLoader` tracks which skills have been shown in full vs abbreviated form using a per-agent marker file. Currently,
all agents — main and subagents alike — pass `${CLAUDE_SESSION_ID}` as the `catAgentId` CLI argument, so they share a
single marker file. A skill loaded by the main agent appears as "already loaded" to any subagent, causing subagents to
receive only the abbreviated reference instead of full first-use content.

Claude Code's `SubagentStart` hook provides a native `agent_id` field — cryptographically unique (64-bit random,
globally unique) per subagent instance. We can use this directly instead of rolling our own ID scheme.

## New Marker File Structure

```
{configDir}/projects/-workspace/
  {sessionId}/skills-loaded          ← main agent marker
  {sessionId}/subagents/{agentId}/   ← per-subagent directory
    skills-loaded                    ← subagent marker
```

The `agentId` string passed to `SkillLoader` encodes the full relative path from `projects/-workspace/`:

| Agent       | agentId value                            | Resolved marker path                                              |
|-------------|------------------------------------------|-------------------------------------------------------------------|
| Main agent  | `{sessionId}`                            | `{configDir}/projects/-workspace/{sessionId}/skills-loaded`       |
| Subagent    | `{sessionId}/subagents/{agent_id}`       | `{configDir}/projects/-workspace/{sessionId}/subagents/{agentId}/skills-loaded` |

`SkillLoader` constructs the marker file as:
```java
this.agentMarkerFile = scope.getClaudeConfigDir()
    .resolve("projects/-workspace")
    .resolve(agentId)
    .resolve("skills-loaded");
```

## How agentId Reaches load-skill

The SKILL.md preprocessor shell has no access to `agent_id` — it is only available in SubagentStart hook input. The
bridge is a **file written by hooks and read by `load-skill`**:

1. `ClearSkillMarkers` (SessionStart) writes `{sessionId}` to `{sessionDir}/current-agent-id` — establishes the main
   agent's identity and resets it after compaction.
2. `SubagentStartHook` writes `{sessionId}/subagents/{agent_id}` to `{sessionDir}/current-agent-id` when a subagent
   spawns.
3. `load-skill` binary reads `{sessionDir}/current-agent-id` to obtain `agentId`. If the file is absent, it falls back
   to `{sessionId}` (main agent behaviour, fail-safe).
4. `CLAUDE_SESSION_ID` is available in the SKILL.md preprocessor environment, so `load-skill` can locate the file via
   `{configDir}/projects/-workspace/{sessionId}/current-agent-id`.

**Known limitation:** Concurrent subagents racing to write `current-agent-id` may briefly overwrite each other's
value. The worst outcome is a subagent receives redundant full-content (no marker found for the other ID) or skips
first-use (marker exists for the wrong ID). This is a minor token-efficiency issue, not a correctness failure.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
  — rename `catAgentId` → `agentId`; remove CLI arg position 2; read agentId from `current-agent-id` file;
  change marker file construction to `configDir.resolve("projects/-workspace").resolve(agentId).resolve("skills-loaded")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearSkillMarkers.java`
  — write `{sessionId}` to `{sessionDir}/current-agent-id` on SessionStart; update glob to also delete
  `subagents/*/skills-loaded` files
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java`
  — write `{sessionId}/subagents/{agent_id}` to `{sessionDir}/current-agent-id` on SubagentStart
- `plugin/skills/*/SKILL.md` (~50 files)
  — remove the `catAgentId` positional argument (`"${CLAUDE_SESSION_ID}"`) from all `load-skill` invocations;
  new CLI signature: `load-skill <plugin-root> <skill-name> <project-dir> [skill-args...]`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`
  — update to new constructor/CLI signature
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ClearSkillMarkersTest.java`
  — update to verify new file write and subagent glob behaviour

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Update `SkillLoader` — rename `catAgentId` to `agentId`, remove it from CLI arg parsing (drop arg[2]),
   read `agentId` from `{sessionDir}/current-agent-id` (fall back to `sessionId` if absent), change marker file
   construction to `configDir.resolve("projects/-workspace").resolve(agentId).resolve("skills-loaded")`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`

2. **Step 2:** Update `ClearSkillMarkers` — write `{sessionId}` to `{sessionDir}/current-agent-id` at session start;
   extend cleanup to also delete `{sessionDir}/subagents/*/skills-loaded` and the old flat
   `{sessionDir}/skills-loaded-*` pattern (migration cleanup).
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearSkillMarkers.java`

3. **Step 3:** Update `SubagentStartHook` — after existing context injection, write
   `{sessionId}/subagents/{agent_id}` to `{sessionDir}/current-agent-id`, creating the `subagents/` directory if
   needed. Use `agent_id` from `input.getString("agent_id")`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java`

4. **Step 4:** Update all SKILL.md preprocessor directives — remove the `"${CLAUDE_SESSION_ID}"` catAgentId argument
   from every `load-skill` invocation. New form:
   `"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <skill-name> "${CLAUDE_PROJECT_DIR}"`
   - Files: all `plugin/skills/*/SKILL.md` (~50 files)

5. **Step 5:** Update tests — revise `SkillLoaderTest` for the new no-agentId CLI signature and file-based agentId
   resolution; revise `ClearSkillMarkersTest` for new write and glob behaviour; revise `SubagentStartHookTest` for
   agent-id file write.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`,
     `client/src/test/java/io/github/cowwoc/cat/hooks/test/ClearSkillMarkersTest.java`,
     `client/src/test/java/io/github/cowwoc/cat/hooks/test/SubagentStartHookTest.java`

6. **Step 6:** Run `mvn -f client/pom.xml verify` and confirm all tests pass.

## Post-conditions

- [ ] `SkillLoader` has no `catAgentId` parameter; constructor accepts `agentId` read internally from
  `current-agent-id` file
- [ ] Main agent marker lives at `{sessionDir}/skills-loaded`
- [ ] Subagent markers live at `{sessionDir}/subagents/{agentId}/skills-loaded`
- [ ] `ClearSkillMarkers` writes `{sessionId}` to `{sessionDir}/current-agent-id` on SessionStart
- [ ] `SubagentStartHook` writes `{sessionId}/subagents/{agentId}` to `{sessionDir}/current-agent-id` on SubagentStart
- [ ] All SKILL.md `load-skill` invocations use 3-arg form (no catAgentId argument)
- [ ] `load-skill` CLI accepts `<plugin-root> <skill-name> <project-dir> [skill-args...]`
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
