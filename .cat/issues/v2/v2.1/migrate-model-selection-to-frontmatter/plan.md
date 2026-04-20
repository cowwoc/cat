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

## Migration Notes

None - this is a refactoring that doesn't change external behavior.
