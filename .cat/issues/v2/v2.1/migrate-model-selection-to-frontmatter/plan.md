# Migrate Model Selection to Frontmatter

## Objective

Replace centralized model selection in `plugin/rules/model-selection.md` with `model` frontmatter in individual skill and agent files.

## Problem

Currently model preferences are maintained in a centralized file (`plugin/rules/model-selection.md`) separate from skill/agent definitions. This creates maintenance overhead and risks drift when new skills are added.

## Solution

Migrate to frontmatter-based model declarations where each skill/agent declares its preferred model directly in its definition file.

## Implementation Plan

### 1. Add model frontmatter to Sonnet-preferred skills

Add `model: sonnet` to the following 32 skill SKILL.md files:
- cat:add-agent
- cat:claude-runner
- cat:empirical-test-agent
- cat:git-merge-linear-agent
- cat:git-rebase-agent
- cat:git-rewrite-history-agent
- cat:git-squash-agent
- cat:github-trigger-workflow-agent
- cat:init
- cat:instruction-builder-agent
- cat:learn
- cat:learn-agent
- cat:optimize-execution
- cat:optimize-execution-agent
- cat:plan-builder-agent
- cat:rebase-impact-agent
- cat:recover-from-drift-agent
- cat:research-agent
- cat:retrospective-agent
- cat:safe-remove-code-agent
- cat:skill-comparison-agent
- cat:stakeholder-review-agent
- cat:tdd-implementation-agent
- cat:test-runner-isolation-validator
- cat:verify-implementation-agent
- cat:work-agent
- cat:work-confirm-agent
- cat:work-implement-agent
- cat:work-merge-agent
- cat:work-prepare-agent
- cat:work-review-agent
- cat:work-with-issue-agent

### 2. Add model frontmatter to Opus-preferred skills

Add `model: opus` to:
- cat:decompose-issue-agent

### 3. Add model frontmatter to Haiku-default skills

Add `model: haiku` to all remaining skills not listed above.

### 4. Add model frontmatter to agent files

Add model frontmatter to all agent .md files in `plugin/agents/` using the same sonnet/opus/haiku categorization.

### 5. Update skill loader to read model from frontmatter

Modify the skill invocation logic to:
- Read `model` field from skill/agent frontmatter
- Use frontmatter value instead of looking up in model-selection.md
- Remove model-selection.md lookup code

### 6. Remove model-selection.md

Delete `plugin/rules/model-selection.md` after migration is complete and verified.

## Acceptance Criteria

- [ ] All skill SKILL.md files have `model` frontmatter field
- [ ] All agent .md files in plugin/agents/ have `model` frontmatter field
- [ ] Skill loader reads model from frontmatter, not model-selection.md
- [ ] plugin/rules/model-selection.md is deleted
- [ ] All tests pass
- [ ] Skill invocations use correct model based on frontmatter

## Testing Strategy

1. Verify all skills have model frontmatter
2. Test skill invocation uses correct model
3. Verify model-selection.md is no longer referenced
4. Run full test suite

## Risks

- Missing a skill/agent file during migration
- Code still referencing model-selection.md after deletion

## Main Agent Jobs

- /cat:tdd-implementation Update GivingUpDetector to permit the phrase 'If you want, I can now continue [...]' only when it is the final sentence, and still flag it when followed by another sentence.

## Jobs

### Job 1
- Add `model: sonnet` frontmatter to the approved contextual skill files under `plugin/skills/*/SKILL.md`, keeping `plugin/skills/config/SKILL.md` unchanged.
- Add `model: sonnet` frontmatter to the approved contextual agent files under `plugin/agents/`.
- Keep mechanical/default targets on `model: haiku`, and keep existing `model: opus` declarations unchanged.

### Job 2
- Update `plugin/rules/execution-model.md` to enforce inline execution after `Launching skill:` handoff, including explicit terminal-state progression and gate re-presentation rules.
- Add/adjust execution-model guidance to prohibit wrapper re-invocation loops without consuming launched phase instructions.

### Job 3
- Update `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java` so the target phrase is only allowed when it is the final sentence and is still flagged if followed by another sentence.
- Add or update tests in `client/src/test/java/io/github/cowwoc/cat/client/test/GivingUpDetectorTest.java` for both final-sentence and followed-by-another-sentence scenarios.

### Job 4
- Fix lock-acquisition error handling in `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/WorkPrepare.java` so `IssueLock.LockResult.Error` is returned as `ERROR` (not READY) in both normal and force-resume acquisition paths.
- Add regression coverage in `client/src/test/java/io/github/cowwoc/cat/client/test/WorkPrepareTest.java` for resume with existing worktree when session already holds a different issue lock.
- Run `mvn -f client/pom.xml verify -e` and ensure all tests pass.
- Update `.cat/issues/v2/v2.1/migrate-model-selection-to-frontmatter/index.json` in the same implementation commit set to mark completion.

### Job 5
- Add missing `model` frontmatter to every remaining `plugin/skills/*/SKILL.md` file that does not yet declare a model, using the plan’s sonnet/opus/haiku mapping.
- Add/extend automated coverage that fails if any skill `SKILL.md` is missing `model` frontmatter, and rerun verification to confirm full skill coverage.

### Job 6
- Remove remaining `model-selection.md` dependencies by updating skill-loader/runtime paths and tests to resolve models exclusively from file frontmatter.
- Update `client/src/test/java/io/github/cowwoc/cat/client/test/InstructionTestRunnerTest.java` and `plugin/skills/instruction-builder/first-use.md` to eliminate `model-selection.md` references.
- Delete `plugin/rules/model-selection.md` and verify there are no remaining repository references.

### Job 7
- Add/repair instruction-test-runner setup so the `work-with-issue/first-use/already-implemented-continues.md` scenario can execute end-to-end instead of failing during isolation-branch extraction.
- Re-run targeted runtime invocation to confirm model selection is applied from frontmatter at execution time.
- Re-run `mvn -f client/pom.xml verify -e` after these fixes and capture passing evidence for acceptance criteria closure.

### Job 8
- Reproduce `create-isolation-branch: extract-turns failed for already-implemented-continues.md` using the canonical 5-argument `instruction-test-runner run-single-test` invocation, then fix the extraction path so this scenario parses successfully.
- Add regression coverage for the `instruction-test-runner` extract-turns/create-isolation-branch flow to fail fast with actionable diagnostics when a scenario file is malformed or parser expectations drift.
- Execute the repaired runtime scenario (`plugin/tests/skills/work-with-issue/first-use/already-implemented-continues`) and assert the launched skill uses the model declared in frontmatter at invocation time.
- Update verification evidence artifacts after the rerun so acceptance criterion "Skill invocations use correct model based on frontmatter" can be marked Done.

### Job 9
- Delete `plugin/rules/model-selection.md` from the worktree and run a repository-wide reference scan to confirm no remaining imports, reads, or docs links to this file.
- Fix the `create-isolation-branch` extraction failure in runtime setup by aligning scenario parsing for `plugin/tests/skills/work-with-issue/first-use/already-implemented-continues.md` with current extract-turns expectations, then add a focused regression test for this exact path.
- Re-run the required E2E command (`instruction-test-runner run-single-test ... already-implemented-continues haiku <session_id>`) and capture PASS evidence proving runtime skill invocation resolves model from frontmatter.
- Refresh `.cat/work/verify/.../criteria-analysis.json` and `.cat/work/verify/.../e2e-test-output.json` with passing results so the remaining criteria (`model-selection.md` deleted, runtime frontmatter model selection, E2E status) can be marked Done.

### Job 10
- Add `model` frontmatter to all missing files reported by verification under `plugin/agents/`: `README.md`, `blue-team-agent.md`, `instruction-design-agent.md`, `stakeholder-architecture.md`, `work-execute.md`, `red-team-agent.md`, `instruction-builder-implement-agent.md`, `instruction-extraction-agent.md`, `stakeholder-requirements.md`, `plan-review-agent.md`, `stakeholder-performance.md`, `stakeholder-design.md`, `stakeholder-security.md`, `work-verify.md`, `instruction-analyzer-agent.md`, and `instruction-grader-agent.md`.
- Re-run the frontmatter coverage check and require `24/24` agent files with model declarations before closing the criterion.
- Restore verification toolchain prerequisites in the worktree environment (ensure `mvn` and `instruction-test-runner` commands resolve on PATH), then rerun `mvn -f client/pom.xml verify -e` and the `instruction-test-runner run-single-test` runtime check.
- If runtime check still fails, update the runtime invocation assertion path to read model from frontmatter at launch time and add/adjust a regression test that proves failure when asserted model differs from file frontmatter.
- Refresh `.cat/work/verify/77fb6191-0df9-46ff-a0f4-e32b7589875a/criteria-analysis.json` and `e2e-test-output.json` with rerun results so failures caused by missing binaries are replaced by true pass/fail signals.

### Job 11
- Fix instruction-grader output generation for `instruction-test-runner run-single-test` so every emitted grade JSON includes a required non-empty `test_case_id`, and add regression coverage that fails on missing/empty `test_case_id`.
- Re-run `instruction-test-runner run-single-test ... already-implemented-continues haiku <session_id>` and the E2E verification flow after the grader schema fix, then refresh `.cat/work/verify/77fb6191-0df9-46ff-a0f4-e32b7589875a/criteria-analysis.json` and `e2e-test-output.json` with conclusive pass/fail evidence for frontmatter-based runtime model selection.

### Job 12
- Rebuild the runtime artifacts from current sources (including the jlink image used by `instruction-test-runner`) before verification reruns, and record the rebuild command output/version markers as evidence that stale binaries are no longer in use.
- Re-run `instruction-test-runner run-single-test ... already-implemented-continues haiku <session_id>` against the rebuilt runtime and capture conclusive launch-time evidence that model selection is resolved from file frontmatter.
- Refresh `.cat/work/verify/77fb6191-0df9-46ff-a0f4-e32b7589875a/criteria-analysis.json` and `e2e-test-output.json` with post-rebuild results, explicitly replacing prior `test_case_id`/stale-artifact inconclusive outcomes with final pass/fail signals.

## Migration Notes

None - this is a refactoring that doesn't change external behavior.
