<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Per-Agent Skill Markers via Native agent_id

## Goal

Give each agent (main and subagents) its own skill-loaded marker file by deriving `catAgentId` from Claude Code's
native `agent_id`, so subagents receive full first-use skill content instead of abbreviated references.

## Satisfies

None (infrastructure improvement)

## Background

`SkillLoader` tracks which skills have been shown in full vs abbreviated form using a per-agent marker file. Previously
all agents passed `${CLAUDE_SESSION_ID}` as the `catAgentId`, so they shared a single marker file. A skill loaded by
the main agent appeared as "already loaded" to any subagent, causing subagents to receive only the abbreviated
reference instead of full first-use content.

Claude Code's `SubagentStart` hook provides a native `agent_id` field — cryptographically unique per subagent instance.
We use this to build per-agent marker paths without inventing our own ID scheme.

## catAgentId vs agent_id

- **`catAgentId`** — the composite path used for skill marker files. Encodes the full relative path from
  `projects/-workspace/`. Main agent: `{sessionId}`. Subagent: `{sessionId}/subagents/{agent_id}`.
- **`agent_id`** — Claude Code's native per-subagent identifier from `SubagentStart` hook input.

The `catAgentId` is passed as the first positional argument (`$0`) to every skill invocation. Both `SessionStartHook`
and `SubagentStartHook` inject instructions telling the agent its `catAgentId`.

## Marker File Structure

```
{configDir}/projects/-workspace/
  {sessionId}/skills-loaded                          ← main agent marker
  {sessionId}/subagents/{agent_id}/skills-loaded     ← per-subagent marker
```

## SKILL.md Preprocessor Pattern

Each SKILL.md passes `$0` (the catAgentId injected by the hook) to `load-skill`:

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <skill> "$0" "${CLAUDE_PROJECT_DIR}"`
```

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Relies on agents always passing catAgentId as the first skill argument. If an agent forgets,
  `load-skill` fails fast (required parameter). This is intentional — visible failure over silent wrong behavior.
- **Mitigation:** Clear instructions injected at agent start. Fail-fast on missing catAgentId.

## Files Modified

- `SkillLoader.java` — extract catAgentId from `$0` positional arg; build marker path from it
- `ClearSkillMarker.java` (renamed from ClearSkillMarkers) — per-agent marker cleanup
- `InjectCatAgentId.java` (renamed from InjectAgentId) — utility to build catAgentId context strings
- `SubagentStartHook.java` — inject catAgentId into subagent context; fail-fast on blank session_id/agent_id
- `SessionStartHook.java` — inject catAgentId into main agent context; fail-fast on blank session_id
- `plugin/skills/*/SKILL.md` (~50 files) — change agentId arg from `"${CLAUDE_SESSION_ID}"` to `"$0"`
- `plugin/concepts/skill-loading.md` — updated marker file documentation
- `plugin/skills/load-skill/first-use.md` — updated argument documentation
- Test files updated accordingly

## Post-conditions

- [ ] Main agent marker lives at `{sessionDir}/skills-loaded`
- [ ] Subagent markers live at `{sessionDir}/subagents/{agent_id}/skills-loaded`
- [ ] `SessionStartHook` injects main agent's catAgentId (`{sessionId}`) into context
- [ ] `SubagentStartHook` injects subagent's catAgentId (`{sessionId}/subagents/{agent_id}`) into context
- [ ] All SKILL.md `load-skill` invocations reference `"$0"` for the catAgentId argument
- [ ] `load-skill` fails fast if catAgentId (`$0` skill arg) is not provided
- [ ] SessionStartHook and SubagentStartHook fail-fast on blank session_id/agent_id
- [ ] All tests pass (`mvn -f client/pom.xml test`)
