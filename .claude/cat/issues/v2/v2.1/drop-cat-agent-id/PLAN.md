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

## How agentId Reaches load-skill

The agentId is passed as a **required** positional argument to `load-skill`, with no default or fallback.

**Main agent:** SKILL.md preprocessor directives pass `${CLAUDE_SESSION_ID}` as the agentId argument. This is already
available in the preprocessor shell environment.

**Subagents:** `SubagentStartHook` injects the subagent's identity into its conversation context at spawn:

> Your CAT agent ID is: `{sessionId}/subagents/{agent_id}`. You MUST pass this as the first argument when invoking any
> skill via the Skill tool.

The subagent passes this value via the Skill tool's `args` parameter. The SKILL.md preprocessor receives it as `$0`
(first positional argument from SkillLoader's argument tokenization). When `$0` is present, it overrides the default
`${CLAUDE_SESSION_ID}` value in the preprocessor directive.

## New Marker File Structure

```
{configDir}/projects/-workspace/
  {sessionId}/skills-loaded                          ← main agent marker
  {sessionId}/subagents/{agentId}/skills-loaded      ← per-subagent marker
```

The `agentId` string encodes the full relative path from `projects/-workspace/`:

| Agent       | agentId value                            | Resolved marker path                                              |
|-------------|------------------------------------------|-------------------------------------------------------------------|
| Main agent  | `{sessionId}`                            | `{configDir}/projects/-workspace/{sessionId}/skills-loaded`       |
| Subagent    | `{sessionId}/subagents/{agent_id}`       | `{configDir}/projects/-workspace/{sessionId}/subagents/{agent_id}/skills-loaded` |

`SkillLoader` constructs the marker file as:
```java
this.agentMarkerFile = scope.getClaudeConfigDir()
    .resolve("projects/-workspace")
    .resolve(agentId)
    .resolve("skills-loaded");
```

## SKILL.md Preprocessor Pattern

Each SKILL.md uses a conditional pattern: if `$0` is provided (subagent passes its agentId), use it; otherwise use
`${CLAUDE_SESSION_ID}` (main agent):

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <skill> "${0:-${CLAUDE_SESSION_ID}}" "${CLAUDE_PROJECT_DIR}"`
```

Wait — this introduces a default/fallback, which the user does not want. Instead, the SKILL.md always passes
`${CLAUDE_SESSION_ID}`. For the main agent this is the correct agentId. For subagents, the SubagentStartHook instructs
them to pass their agentId as `$0`, and the SKILL.md preprocessor uses `$0` when present.

Actually, SkillLoader already supports positional arguments (`$0`, `$1`, etc.) via its `argTokens` list. The SKILL.md
can reference `$0` in the preprocessor command. But `$0` only has a value when the agent passes args via the Skill
tool. The main agent invokes skills without args, so `$0` would be empty/unresolved.

**Revised pattern:** The `load-skill` CLI always requires the agentId argument. Each SKILL.md preprocessor always
passes `${CLAUDE_SESSION_ID}` — this is correct for the main agent. For subagents, `SubagentStartHook` injects
instructions telling the subagent its full agentId (`{sessionId}/subagents/{agent_id}`). The subagent passes this as
the Skill tool's `args`. SkillLoader tokenizes `args` into `$0`. If `$0` is present, the SKILL.md preprocessor can
use it to override the default.

**Simplest correct pattern:** Use shell parameter expansion in the SKILL.md preprocessor:

```
!`AGENT_ID=$0; "${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <skill> "${AGENT_ID:-${CLAUDE_SESSION_ID}}" "${CLAUDE_PROJECT_DIR}"`
```

No — this is still a fallback. The user explicitly said no defaults or fallbacks.

**Final pattern:** The agentId is always the first `$ARGUMENTS` token. Both main agent and subagents must pass it:

- **Main agent:** SessionStart hook injects "Your CAT agent ID is: `{sessionId}`" — the main agent passes this when
  invoking skills.
- **Subagents:** SubagentStartHook injects "Your CAT agent ID is: `{sessionId}/subagents/{agent_id}`" — the subagent
  passes this when invoking skills.

Both agents pass their agentId as the first argument to every skill invocation. SkillLoader receives it as `$0`.

The SKILL.md pattern becomes:
```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" <skill> "$0" "${CLAUDE_PROJECT_DIR}"`
```

For skills that also accept their own arguments, the agentId is always the first arg (`$0`) and skill-specific args
start from `$1`.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Relies on agents always passing agentId as the first skill argument. If an agent forgets, `load-skill`
  fails fast (required parameter). This is intentional — we prefer a visible failure over silent wrong behaviour.
- **Mitigation:** Clear instructions injected at agent start. Fail-fast on missing agentId.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
  — rename `catAgentId` → `agentId`; change from CLI arg to `$0` positional arg from skill invocation;
  change marker file to `configDir.resolve("projects/-workspace").resolve(agentId).resolve("skills-loaded")`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearSkillMarkers.java`
  — update glob to delete `{sessionDir}/skills-loaded` and `{sessionDir}/subagents/*/skills-loaded`;
  also clean up legacy `skills-loaded-*` flat files
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java`
  — inject agentId (`{sessionId}/subagents/{agent_id}`) into subagent context with instructions to pass it
  when invoking skills
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSkillListing.java` (or equivalent SessionStart
  handler) — inject agentId (`{sessionId}`) into main agent context with instructions to pass it when invoking skills
- `plugin/skills/*/SKILL.md` (~50 files)
  — change the agentId argument from `"${CLAUDE_SESSION_ID}"` to `"$0"` in all `load-skill` invocations
- `plugin/skills/load-skill/first-use.md`
  — update the Bash template to use agentId (already passed by the agent)
- `plugin/concepts/skill-loading.md`
  — update marker file path documentation and agentId description
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`
  — update to new constructor/CLI signature and agentId semantics
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ClearSkillMarkersTest.java`
  — update to verify new glob behaviour
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SubagentStartHookTest.java`
  — verify agentId injection into context

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Update `SkillLoader` — rename `catAgentId` to `agentId` throughout; change `main()` CLI signature to
   `<plugin-root> <skill-name> <project-dir> [skill-args...]` (agentId is no longer a CLI arg, it comes from `$0`
   skill arg); change marker file construction to
   `configDir.resolve("projects/-workspace").resolve(agentId).resolve("skills-loaded")`.
   The agentId is extracted from the first skill arg (`$0`) and is required (fail-fast if absent).
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`

2. **Step 2:** Update `ClearSkillMarkers` — extend cleanup to delete `{sessionDir}/skills-loaded` (main marker),
   `{sessionDir}/subagents/*/skills-loaded` (subagent markers), and legacy `{sessionDir}/skills-loaded-*` flat files.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearSkillMarkers.java`

3. **Step 3:** Update `SubagentStartHook` — inject agentId into subagent context. Format:
   "Your CAT agent ID is: `{sessionId}/subagents/{agent_id}`. You MUST pass this as the first argument when invoking
   any skill via the Skill tool."
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java`

4. **Step 4:** Inject agentId into main agent context at SessionStart. Format:
   "Your CAT agent ID is: `{sessionId}`. You MUST pass this as the first argument when invoking any skill via the
   Skill tool."
   - Files: SessionStart handler (e.g., `InjectSkillListing.java` or new handler)

5. **Step 5:** Update all SKILL.md preprocessor directives — change the agentId argument from
   `"${CLAUDE_SESSION_ID}"` to `"$0"` in all `load-skill` invocations. For skills that already use `$0` for other
   purposes, shift their positional args by 1 (agentId becomes `$0`, previous `$0` becomes `$1`, etc.).
   - Files: all `plugin/skills/*/SKILL.md` (~50 files)

6. **Step 6:** Update `plugin/skills/load-skill/first-use.md` and `plugin/concepts/skill-loading.md` — update
   documentation to reflect new agentId semantics and marker file paths.
   - Files: `plugin/skills/load-skill/first-use.md`, `plugin/concepts/skill-loading.md`

7. **Step 7:** Update tests — revise `SkillLoaderTest` for new CLI signature and `$0`-based agentId; revise
   `ClearSkillMarkersTest` for new glob behaviour; revise `SubagentStartHookTest` for agentId context injection.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`,
     `client/src/test/java/io/github/cowwoc/cat/hooks/test/ClearSkillMarkersTest.java`,
     `client/src/test/java/io/github/cowwoc/cat/hooks/test/SubagentStartHookTest.java`

8. **Step 8:** Run `mvn -f client/pom.xml verify` and confirm all tests pass.

## Post-conditions

- [ ] No references to `catAgentId` remain in the codebase
- [ ] `SkillLoader` constructor parameter is named `agentId`
- [ ] Main agent marker lives at `{sessionDir}/skills-loaded`
- [ ] Subagent markers live at `{sessionDir}/subagents/{agentId}/skills-loaded`
- [ ] SessionStart hook injects main agent's agentId (`{sessionId}`) into context
- [ ] SubagentStartHook injects subagent's agentId (`{sessionId}/subagents/{agent_id}`) into context
- [ ] All SKILL.md `load-skill` invocations reference `"$0"` for the agentId argument
- [ ] `load-skill` CLI accepts `<plugin-root> <skill-name> <project-dir> [skill-args...]`
- [ ] `load-skill` fails fast if agentId (`$0` skill arg) is not provided
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
