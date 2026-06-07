# Plan

## Goal

Refactor all skill files to standardize first-use loading by defining the loading contract in one shared rule file and referencing it from all `SKILL.md` files (instead of repeating the same loading instructions per skill).

As part of this refactor, remove model-facing `catAgentId` requirements from skill invocation guidance and remove parser behavior that depends on a `catAgentId` prefix in raw arguments.

## Type

refactor

## Pre-conditions

- [x] The first-load-test approach is confirmed as the canonical loading behavior to replicate.
- [x] Existing skill-loading expectations are enumerated so regressions can be detected.
- [x] All current `catAgentId` callsites and validators are identified before removal.

## Scope

1. Define a shared first-use loading contract in one rule/concept file.
2. Update all `plugin/skills/**/SKILL.md` files to reference the shared loading contract instead of repeating loading boilerplate inline, except dispatcher skills (for example `get-diff`) that must invoke explicit handlers directly from `SKILL.md`.
3. Preserve skill-specific behavior in each skill's `first-use.md` companion while deduplicating common loading instructions.
4. Update loader/listing/startup guidance so model-facing instructions no longer require passing `catAgentId` to invoke skills.
5. Remove remaining references to deprecated loading guidance from skill content and docs.
6. Remove parser behavior that assumes a `catAgentId` prefix in raw arguments (including `work-prepare` handling).
7. Update tests for loading guidance changes and parser-contract removal behavior.
8. ✅ Replace `test-json-handoff.bats` coverage with an equivalent unit test.

## Post-conditions

- [x] A single shared loading contract file defines first-use loading behavior.
- [x] All `plugin/skills/**/SKILL.md` files reference the shared loading contract instead of duplicating loading boilerplate.
- [x] First-load behavior is correct and deterministic for migrated skills.
- [x] Subsequent-load behavior is correct and deterministic for migrated skills.
- [x] Model-facing skill invocation guidance no longer requires passing `catAgentId`.
- [x] Argument parsers no longer fail solely because a `catAgentId` prefix is absent.
- [x] Loader output and invocation flow remain stable (no regressions in unrelated skill behavior).
- [x] `plugin/skills/get-diff/SKILL.md` invokes `!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-output" "$0" get-diff "$1"`` directly instead of referencing `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md`.
- [x] After moving the launcher line into `plugin/skills/get-diff/SKILL.md`, remove `plugin/skills/get-diff/first-use.md`.
- [x] `plugin/skills/status/SKILL.md` invokes `!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-output" status`` directly instead of referencing `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md`.
- [x] After moving the launcher line into `plugin/skills/status/SKILL.md`, remove `plugin/skills/status/first-use.md`.
- [x] `plugin/skills/token-report/SKILL.md` moves the launcher/instruction content from `first-use.md` into `SKILL.md` and uses direct invocation instead of referencing `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md`.
- [x] After moving content into `plugin/skills/token-report/SKILL.md`, remove `plugin/skills/token-report/first-use.md`.
- [x] Tests cover loading-guidance changes and parameter-removal behavior and pass.
- [x] `test-json-handoff.bats` is replaced by an equivalent unit test.
- [x] `plugin/skills/work-confirm/first-use.md` argument-position table is aligned with `<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json_path> <files_changed> <trust> <caution>` and contains no `agent_id` entry.
- [x] `plugin/skills/work-implement/first-use.md` argument-position table is aligned with `<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <caution>` and contains no `agent_id` entry.
- [x] `plugin/skills/work-review/first-use.md` argument-position table is aligned with `<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <caution>` and contains no `agent_id` entry.

## Migration Notes

- This change intentionally removes backward-compatibility shims for the old mechanism.
- Any required data or file-format transitions must be handled via the project migration pattern (`plugin/migrations/`) and be idempotent.
- Closed issues are only modified by migration scripts when needed for format consistency.

## Risks

- Broad, cross-skill migration can introduce inconsistent behavior if some SKILL.md files are not updated to the shared reference pattern.
- First-load/subsequent-load differences are easy to regress without explicit test coverage.
- Removing model-facing `catAgentId` guidance touches parser and invocation contracts and may break workflows unless all call paths are updated together.

## Post-conditions

- [x] Skill loading instructions are standardized via a single shared loading contract referenced by all SKILL.md files.
- [x] Legacy loading guidance references are removed from active code paths.
- [x] Model-facing `catAgentId` requirement is removed from targeted contracts and validated by tests.
- [x] Required migrations (if any) are implemented and idempotent.
- [x] Relevant tests pass.
