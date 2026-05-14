# Plan: Codex SessionStart Rules Injection

## Goal
Move Codex rule loading out of `AGENTS.md` and into the Codex SessionStart hook so main agents and subagents receive
the correct `.cat/rules/` files automatically.

## Background
`AGENTS.md` currently tells non-Claude agents to load portable rules from `.cat/rules/common/*.md` plus their
runtime-specific rule directory before beginning work. That instruction is a fallback for Codex because rule injection
is not yet guaranteed by the Codex SessionStart hook.

The earlier `audience-aware-rules-injection` issue implemented the rule frontmatter model:

```yaml
---
mainAgent: true
subAgents: []
paths: ["plugin/**", "client/**"]
---
```

All properties are optional. `mainAgent` defaults to `true`, `subAgents` defaults to all subagents, and `paths`
defaults to always inject.

## Current Question
Verify whether the Codex SessionStart hook input contains enough information to distinguish the main agent from a
subagent.

Observed Codex SessionStart input for subagents includes `thread_source: "subagent"` and a top-level `agent_role`.
The implementation must use the top-level `agent_role` as the subagent rule-matching identifier. If a payload is
identified as a subagent but `agent_role` is missing or blank, the hook must fail fast instead of falling back to
nested session metadata.

## Approach

1. Inspect the Codex SessionStart hook input contract empirically with both a main-agent session and a spawned
   subagent session.
2. Confirm the reliable discriminator for main-agent vs subagent contexts:
   - Use `thread_source: "subagent"` or explicit subagent source metadata only to identify that the session is a
     subagent.
   - Use only the top-level `agent_role` value to match `subAgents` frontmatter.
   - Fail fast when a subagent payload has a missing or blank top-level `agent_role`.
3. Update the Codex SessionStart rules injector to discover `.cat/rules/common/*.md` and `.cat/rules/codex/*.md`
   when those directories exist.
4. Parse `mainAgent`, `subAgents`, and `paths` frontmatter using the existing audience-aware rules behavior.
5. Inject rule content into additional context based on:
   - Main agent: files with `mainAgent` omitted or `true`.
   - Subagent: files whose `subAgents` value is omitted or matches the subagent role/type/name used by Codex.
   - Paths: only files whose `paths` frontmatter matches the active context, preserving the default of always inject.
6. Remove the `AGENTS.md` section that tells non-Claude agents to manually load `.cat/rules/common/*.md`.
7. Add regression tests for main-agent injection, subagent injection, omitted/default frontmatter values, explicit
   subagent exclusion, and missing `.cat/rules/codex/`.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Removing the `AGENTS.md` fallback before hook injection is verified could leave Codex agents without
  project rules.
- **Mitigation:** Remove the `AGENTS.md` instruction in the same implementation that proves SessionStart injection
  covers both main-agent and subagent contexts.

## Files to Modify
- `AGENTS.md` - remove the manual non-Claude rule loading instruction section
- `client/**` - Codex SessionStart hook implementation and tests
- `plugin/**` - Codex hook registration or bundled hook resources if required by the existing implementation
- `.cat/rules/codex/` - create only if Codex-specific rule files are needed

## Post-conditions
- [x] `AGENTS.md` no longer instructs non-Claude agents to manually load `.cat/rules/common/*.md`
- [x] Codex SessionStart injects matching `.cat/rules/common/*.md` files automatically
- [x] Codex SessionStart injects matching `.cat/rules/codex/*.md` files when the directory exists
- [x] Injection respects `mainAgent`, `subAgents`, and `paths` frontmatter defaults and explicit values
- [x] Main-agent and subagent detection is covered by tests or an empirical fixture from real Codex hook input
- [x] Claude behavior is unchanged
- [x] `mvn -f client/pom.xml verify -e` passes
