# Clean Up Skill Frontmatter and Delegation

## Objective

Clean up CAT skills so Claude-specific frontmatter and Codex-specific frontmatter are separated into runtime wrappers,
shared instruction body fragments live in `skills/include`, common runtime companion files stay in `skills/common`,
model/effort-sensitive work uses explicit agents, and
deterministic skill logic is moved toward Java CLI commands where practical.

## Pre-conditions

- The work runs in the existing CAT-managed worktree for `2.1-remove-unsupported-codex-skill-frontmatter`.
- The existing issue is expanded and reopened rather than replaced by a new follow-up issue.
- Tests do not enforce repository conventions by scanning the literal contents of Markdown/non-code source files.

## Implementation Plan

1. Update repository development conventions:
   - Add testing guidance that tests must not verify literal contents of non-code files such as Markdown skills,
     rules, plans, or concepts.
   - Update `llm-to-java.md` so a whole-skill Java CLI wrapper is preferred when deterministic end-to-end behavior is
     possible; use multiple CLI calls only when agent judgment is needed between deterministic steps.
2. Update runtime artifact generation:
   - Support runtime-specific skill wrappers that include shared fragments from `skills/include` with `cat:include`.
   - Preserve common companion files such as `first-use.md` when a runtime wrapper replaces the common `SKILL.md`.
3. Split shared skills:
   - Move shared skill body fragments into no-frontmatter files under `skills/include`.
   - Add Claude `SKILL.md` wrappers with Claude-specific frontmatter.
   - Add Codex `SKILL.md` wrappers with only Codex-supported frontmatter.
4. Revise model/effort-sensitive skills:
   - Add explicit runtime agents for `plan-builder`, `decompose-issue`, `learn`, and `research`.
   - Update those skill launchers to delegate reasoning-heavy work to the explicit agents.
   - Clean up stale generic subagent/model examples in secondary skills when encountered.
5. Apply Java CLI extraction opportunistically:
   - Prefer a whole-skill Java CLI wrapper for deterministic skills.
   - Otherwise extract deterministic subsets such as file/JSON updates, validation, git checks, structured output, and
     path/subprocess argument construction.
6. Verify with executable behavior tests and full Maven verification.

## Post-conditions

- [x] Shared skill body fragments are frontmatter-free include targets under `skills/include`.
- [x] Claude skill wrappers retain Claude-only frontmatter such as `model`, `effort`, `context`,
      `user-invocable`, and `disable-model-invocation`.
- [x] Codex skill wrappers contain only Codex-supported skill frontmatter.
- [x] Runtime artifacts include wrapper-expanded skill bodies and required common companion files.
- [x] Reasoning-heavy skills use explicit agents for model/effort-sensitive work.
- [x] Tests avoid asserting literal contents of repository Markdown/non-code files.
- [x] `mvn -f client/pom.xml verify -e` passes.
