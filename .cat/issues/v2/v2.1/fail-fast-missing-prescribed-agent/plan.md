# Fail Fast on Missing Prescribed Agent

## Problem

CAT skills can prescribe a dedicated subagent, such as `cat-decompose-issue-agent`, `cat-plan-builder-agent`, or
`cat-research-agent`. When the runtime does not expose that prescribed agent type, the main agent may fall back to a
generic/default agent, which hides installation or runtime-registry problems and can produce lower-quality or stalled
workflow execution.

## Parent Requirements

None

## Reproduction Evidence

- After running `cat-update`, `/workspace/.codex/agents/cat-decompose-issue-agent.toml` existed and parsed correctly.
- The active Codex session's `spawn_agent` schema did not expose `cat-decompose-issue-agent`,
  `cat-plan-builder-agent`, or `cat-research-agent`.
- The workflow fell back to spawning a generic/default agent instead of stopping with a clear agent-unavailable error.

## Expected vs Actual

- **Expected:** If a skill prescribes a specific agent type and that type is unavailable, the workflow fails fast with
  a clear message that names the missing agent and tells the user to restart Codex or refresh the runtime registry.
- **Actual:** The workflow can continue with `agent_type: default`, masking the missing dedicated agent and weakening
  the workflow contract.

## Root Cause

Skill instructions currently allow or imply fallback execution when a dedicated agent cannot be spawned. They do not
distinguish between "runtime cannot spawn agents at all" and "runtime can spawn agents, but the prescribed agent type
is missing from the current registry."

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Overly strict wording could block emergency manual recovery paths.
- **Mitigation:** Define a narrow rule: fail fast only when a skill explicitly prescribes an agent type and the runtime
  has an agent-spawning tool but that specific agent type is unavailable. Manual fallback remains possible only after
  the user explicitly authorizes bypassing the prescribed-agent requirement.

## Files to Modify

- `client/plugin/skills/common/decompose-issue/SKILL.md` - Fail fast when `cat-decompose-issue-agent` is unavailable.
- `client/plugin/skills/common/plan-builder/SKILL.md` - Fail fast when `cat-plan-builder-agent` is unavailable.
- `client/plugin/skills/common/research/SKILL.md` - Fail fast when `cat-research-agent` is unavailable.
- `client/plugin/skills/common/instruction-builder/SKILL.md` and other delegated workflows - Apply the same convention
  where a dedicated agent is prescribed.
- `client/plugin/skills/common/instruction-builder/skill-conventions.md` or the appropriate shared convention file -
  Add a reusable "prescribed agent must exist" rule.
- `client/plugin/tests/**` or `client/cli/src/test/**` - Add regression coverage for the instruction/convention.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Inventory skills and agent-facing instructions that prescribe a dedicated `cat-*` agent type.
- Identify every place that currently permits fallback to `default`, a generic agent, or direct main-agent execution
  when the prescribed agent type is missing.

### Job 2

- Add a plugin convention that says prescribed agent types are part of the workflow contract.
- State that if the runtime exposes an agent-spawning tool but the prescribed agent type is missing, the workflow must
  fail fast and report the missing agent type.
- State that falling back to `default` or a generic agent is prohibited unless the user explicitly requests a manual
  bypass after seeing the fail-fast error.
- Include restart/refresh guidance for Codex sessions whose `.codex/agents/*.toml` files exist but are not exposed in
  the active session.

### Job 3

- Update delegated skills such as decompose-issue, plan-builder, and research to follow the new convention.
- Ensure their wording distinguishes:
  - Runtime has no native agent-spawning capability at all.
  - Runtime has agent spawning, but the prescribed agent type is missing.
  - User explicitly authorizes a manual bypass.

### Job 4

- Add tests or instruction checks that catch prohibited fallback wording in delegated skills.
- Verify tests fail when a prescribed-agent workflow tells the main agent to use `default` for a missing dedicated
  agent.
- Run `mvn -f client/pom.xml verify -e`.

## Post-conditions

- [ ] A shared plugin convention requires fail-fast behavior when a prescribed agent type is unavailable.
- [ ] Delegated skills no longer instruct agents to fall back to `default` or generic agents for missing prescribed
      agents.
- [ ] Error guidance names the missing agent and tells Codex users to restart or refresh the session when agent TOML
      exists but the active registry is stale.
- [ ] Regression coverage catches future prescribed-agent fallback wording.
- [ ] `mvn -f client/pom.xml verify -e` passes.
