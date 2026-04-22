---
mainAgent: true
---
## Model Selection for Skills

When invoking skills via the Skill tool, use the following model preference.

Subagents declare their model directly in their frontmatter and do not require model selection by the caller.

## Model Version Mappings

The shorthand model names used in `model:` parameters map to these specific Claude 4.5 versions:

- `opus` → `claude-opus-4-5`
- `sonnet` → `claude-sonnet-4-5`
- `haiku` → `claude-haiku-4-5`

These mappings are applied automatically by the Agent/Task/Skill tools when the `model:` parameter is specified.

**Note:** The Agent tool's `model` parameter only accepts shorthand values (`opus`, `sonnet`, `haiku`), not full model IDs. All subagent files now declare their model directly in frontmatter using full model IDs (e.g., `model: claude-opus-4-5`).

**Sonnet-preferred skills** (use `model: sonnet` by default, fall back to `model: opus` if Sonnet is rate-limited):

- `cat:add-agent`
- `cat:claude-runner`
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

**Opus-preferred skills** (use `model: opus`; these require the highest reasoning capability):

- `cat:decompose-issue-agent`

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

**Default model:** Skills not listed above default to `haiku`.
