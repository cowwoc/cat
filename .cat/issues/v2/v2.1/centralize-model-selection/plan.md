# Plan

## Goal

Rename skill-models.md to model-selection.md, remove explicit `model: sonnet` and `model: opus` frontmatter from all skills and subagents, and add entries to model-selection.md for every skill/subagent that currently lacks a model specification.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/rules/skill-models.md` no longer exists; `plugin/rules/model-selection.md` exists with equivalent + expanded content
- [ ] No skill or subagent SKILL.md contains `model: sonnet` or `model: opus` in frontmatter
- [ ] No agent file in `plugin/agents/` contains `model: sonnet` or `model: opus` in frontmatter
- [ ] `model-selection.md` has an entry for every skill and subagent that does not have a `model:` frontmatter entry, specifying which model it should use
- [ ] All existing `model: haiku` frontmatter entries remain unchanged
- [ ] `plugin/skills/instruction-builder-agent/first-use.md` references `model-selection.md` (not `skill-models.md`)
- [ ] `plugin/tests/rules/skill-models/` no longer exists; `plugin/tests/rules/model-selection/` exists with updated content
- [ ] `client/src/main/java/.../InstructionTestRunner.java` reads `rules/model-selection.md` (not `rules/skill-models.md`)
- [ ] `client/src/test/java/.../InstructionTestRunnerTest.java` references `model-selection.md` (not `skill-models.md`)
- [ ] E2E verification passes: `mvn -f client/pom.xml verify -e`

## Research Findings

### Current State

**File to rename:** `plugin/rules/skill-models.md` → `plugin/rules/model-selection.md`

**Files with `model: sonnet` in frontmatter (must be removed):**
- `plugin/agents/instruction-analyzer-agent.md`
- `plugin/agents/instruction-builder-implement-agent.md`
- `plugin/agents/plan-review-agent.md`
- `plugin/agents/stakeholder-architecture.md`
- `plugin/agents/stakeholder-design.md`
- `plugin/agents/stakeholder-performance.md`
- `plugin/agents/stakeholder-requirements.md`
- `plugin/agents/stakeholder-security.md`
- `plugin/agents/work-execute.md`
- `plugin/agents/work-verify.md`
- `plugin/skills/github-trigger-workflow-agent/SKILL.md`

**Files with `model: opus` in frontmatter (must be removed):**
- `plugin/agents/blue-team-agent.md`
- `plugin/agents/red-team-agent.md`

**Agent file with no model at all:**
- `plugin/agents/instruction-grader-agent.md` → should be `sonnet` (judgment/grading tasks)

**SKILL.md files without `model:` frontmatter** not yet listed in skill-models.md:
- `plugin/skills/claude-runner/SKILL.md` → should be `sonnet` (complex orchestration)
- `plugin/skills/test-runner-isolation-validator/SKILL.md` → should be `sonnet` (analysis task)

**References to `skill-models.md` that need updating:**
- `plugin/skills/instruction-builder-agent/first-use.md` lines 393–395
- `plugin/tests/rules/skill-models/e2e-verification.md` (entire directory rename)
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java` (3 javadoc + 1 runtime reference)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerTest.java` (3 test references)

### Java Code Behavior

`InstructionTestRunner.extractModel()` reads SKILL.md frontmatter and if `model:` is blank, calls
`lookupModelInSkillModels()` which scans `rules/skill-models.md` for `- \`cat:skill-name\`` entries,
returning `"sonnet"` if found or `"haiku"` as default. After this issue, it must scan `rules/model-selection.md` instead.
This lookup only applies to SKILL.md files (not agents); agent model selection is done by the calling agent at runtime.

**Important:** The comment in `first-use.md` saying "`extract-model` binary reads only SKILL.md frontmatter
and falls back to `haiku`; the `skill-models.md` mapping is used at runtime by the calling agent (not by
`extract-model`)" is **incorrect** — the Java code does look up `skill-models.md`. This comment must be
corrected to accurately describe the dual lookup: frontmatter first, then model-selection.md for SKILL.md files.

### New model-selection.md Content

The file must cover:

**Sonnet-preferred skills** (use `model: sonnet` by default; fall back to `model: opus` if Sonnet is rate-limited):
All 31 currently listed skills plus `cat:claude-runner` and `cat:test-runner-isolation-validator`.

**Sonnet agents** (remove frontmatter; list in model-selection.md):
`cat:instruction-analyzer-agent`, `cat:instruction-builder-implement-agent`, `cat:instruction-grader-agent`,
`cat:plan-review-agent`, `cat:stakeholder-architecture`, `cat:stakeholder-design`, `cat:stakeholder-performance`,
`cat:stakeholder-requirements`, `cat:stakeholder-security`, `cat:work-execute`, `cat:work-verify`

**Opus agents** (remove frontmatter; list in model-selection.md):
`cat:blue-team-agent`, `cat:red-team-agent`

## Jobs

### Job 1 — Rename, expand, and clean up plugin files

- Create `plugin/rules/model-selection.md` (see exact content below), then delete `plugin/rules/skill-models.md`
- Remove `model: sonnet` line from each of these agent files: `plugin/agents/instruction-analyzer-agent.md`, `plugin/agents/instruction-builder-implement-agent.md`, `plugin/agents/plan-review-agent.md`, `plugin/agents/stakeholder-architecture.md`, `plugin/agents/stakeholder-design.md`, `plugin/agents/stakeholder-performance.md`, `plugin/agents/stakeholder-requirements.md`, `plugin/agents/stakeholder-security.md`, `plugin/agents/work-execute.md`, `plugin/agents/work-verify.md`
- Remove `model: sonnet` line from `plugin/skills/github-trigger-workflow-agent/SKILL.md`
- Remove `model: opus` line from each of: `plugin/agents/blue-team-agent.md`, `plugin/agents/red-team-agent.md`
- Update `plugin/skills/instruction-builder-agent/first-use.md`: replace both occurrences of `skill-models.md` with `model-selection.md`, and fix the inaccurate comment about `extract-model` (see correction below)
- Rename test directory: move `plugin/tests/rules/skill-models/e2e-verification.md` to `plugin/tests/rules/model-selection/e2e-verification.md`, updating all references inside from `skill-models.md`/`skill-models` to `model-selection.md`/`model-selection`; delete the old directory

### Job 2 — Update Java source and test files

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`:
  - In `lookupModelInSkillModels()` body: change `scope.getPluginRoot().resolve("rules/skill-models.md")` → `scope.getPluginRoot().resolve("rules/model-selection.md")`
  - Update the 3 Javadoc occurrences of `skill-models.md` to `model-selection.md`
- Update `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerTest.java`:
  - Update test method `extractModelUsesSkillModelsMappingForSonnetSkill`: rename to `extractModelUsesModelSelectionMappingForSonnetSkill`, update `rulesDir.resolve("skill-models.md")` → `rulesDir.resolve("model-selection.md")`, update Javadoc
  - Update test method `extractModelFallsBackToHaikuWhenNotInSkillModels`: update `rulesDir.resolve("skill-models.md")` → `rulesDir.resolve("model-selection.md")`, update Javadoc
- Run `mvn -f client/pom.xml verify -e` to verify tests pass; update index.json in the same commit (status: closed, progress: 100%)

## Exact Content for model-selection.md

The new `plugin/rules/model-selection.md` must have this content (preserve exact formatting):

```
---
mainAgent: true
---
## Model Selection for Skills and Agents

When invoking skills via the Skill tool or spawning subagents via the Agent/Task tool, use the following model
preference:

**Sonnet-preferred skills** (use `model: sonnet` by default, fall back to `model: opus` if Sonnet is rate-limited):

- `cat:add-agent`
- `cat:claude-runner`
- `cat:decompose-issue-agent`
- `cat:empirical-test-agent`
- `cat:git-merge-linear-agent`
- `cat:git-rebase-agent`
- `cat:git-rewrite-history-agent`
- `cat:git-squash-agent`
- `cat:github-trigger-workflow-agent`
- `cat:init`
- `cat:instruction-builder-agent`
- `cat:learn`
- `cat:learn-agent`
- `cat:optimize-execution`
- `cat:optimize-execution-agent`
- `cat:plan-builder-agent`
- `cat:rebase-impact-agent`
- `cat:recover-from-drift-agent`
- `cat:research-agent`
- `cat:retrospective-agent`
- `cat:safe-remove-code-agent`
- `cat:skill-comparison-agent`
- `cat:stakeholder-review-agent`
- `cat:tdd-implementation-agent`
- `cat:test-runner-isolation-validator`
- `cat:verify-implementation-agent`
- `cat:work-agent`
- `cat:work-confirm-agent`
- `cat:work-implement-agent`
- `cat:work-merge-agent`
- `cat:work-prepare-agent`
- `cat:work-review-agent`
- `cat:work-with-issue-agent`

**Sonnet-preferred agents** (use `model: sonnet` by default, fall back to `model: opus` if Sonnet is rate-limited):

- `cat:instruction-analyzer-agent`
- `cat:instruction-builder-implement-agent`
- `cat:instruction-grader-agent`
- `cat:plan-review-agent`
- `cat:stakeholder-architecture`
- `cat:stakeholder-design`
- `cat:stakeholder-performance`
- `cat:stakeholder-requirements`
- `cat:stakeholder-security`
- `cat:work-execute`
- `cat:work-verify`

**Opus-preferred agents** (use `model: opus`; these require the highest reasoning capability):

- `cat:blue-team-agent`
- `cat:red-team-agent`

**Fallback behavior:** If Sonnet returns a rate-limit error, retry the same skill invocation using Opus. Do not
ask the user before falling back — rate-limit fallback is automatic.

This applies to ALL Sonnet-model invocations, including subagents spawned within a skill's execution:

- When a Sonnet subagent (Agent/Task tool) hits rate limits, retry the subagent with `model: opus`
- When a Sonnet skill hits rate limits, retry the skill invocation with `model: opus`
- Once Sonnet rate limits are observed, use `model: opus` for all subsequent Sonnet-preferred
  invocations in the same session

Perform the delegated work with opus model instead of manually performing the work inline. Rate-limited
subagent work must be retried via the same delegation mechanism (Agent/Task/Skill tool) with opus, not
absorbed into the calling agent's context.

**Default model:** Skills and agents not listed above, and without a `model:` frontmatter entry, default to `haiku`.
```

## Exact Correction for first-use.md

Lines 391–397 currently read:
```
**CAT plugin skill model convention:** For skills in this plugin, omit the `model:` frontmatter from
`SKILL.md` unless the model is `haiku`. Sonnet-preferred skills are listed in
`${CLAUDE_PLUGIN_ROOT}/rules/skill-models.md` — add or update entries there rather than setting
`model:` in `SKILL.md`. The `extract-model` binary reads only `SKILL.md` frontmatter and falls back to
`haiku`; the `skill-models.md` mapping is used at runtime by the calling agent (not by `extract-model`).
This means SPRT trials for sonnet-preferred skills run with the `haiku` fallback unless the test
explicitly overrides TEST_MODEL — which is acceptable for unit-level skill tests.
```

Replace with:
```
**CAT plugin skill model convention:** For skills and agents in this plugin, omit the `model:` frontmatter
unless the model is `haiku`. Sonnet-preferred and opus-preferred skills and agents are listed in
`${CLAUDE_PLUGIN_ROOT}/rules/model-selection.md` — add or update entries there rather than setting
`model:` in `SKILL.md` or agent files. The `extract-model` binary reads `SKILL.md` frontmatter first; if
`model:` is absent, it falls back to scanning `model-selection.md` for the skill name (returning `sonnet`
if listed, `haiku` otherwise). Agent model selection uses `model-selection.md` at runtime by the calling
agent. This means SPRT trials for sonnet-preferred skills run with the `haiku` fallback unless the test
explicitly overrides TEST_MODEL — which is acceptable for unit-level skill tests.
```

## Commit Type

`refactor:` — this is a structural reorganization that moves model specifications from distributed frontmatter into a central rules file, with no behavior changes for callers.
