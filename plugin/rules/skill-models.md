---
mainAgent: true
---
## Model Selection for Skills

When invoking skills via the Skill tool or spawning subagents via the Agent/Task tool, use the following model
preference:

**Sonnet-preferred skills** (use `model: sonnet` by default, fall back to `model: opus` if Sonnet is rate-limited):

- `cat:add-agent`
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
- `cat:verify-implementation-agent`
- `cat:work-agent`
- `cat:work-confirm-agent`
- `cat:work-implement-agent`
- `cat:work-merge-agent`
- `cat:work-prepare-agent`
- `cat:work-review-agent`
- `cat:work-with-issue-agent`

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

**Skills not listed above** use their SKILL.md `model:` frontmatter (typically `haiku` for lightweight tasks).
Do not override their model selection.
