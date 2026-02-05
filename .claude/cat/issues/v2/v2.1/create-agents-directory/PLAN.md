# Plan: create-agents-directory

## Current State
Plugin has stakeholders in `plugin/stakeholders/` and subagent-style skills scattered in `plugin/skills/`.
No dedicated agents directory exists. Subagents like work-merge cannot preload relevant skills (git-squash,
git-rebase) because they're defined as skills, not proper Claude Code agents.

## Target State
Create `plugin/agents/` directory with proper structure following Claude Code subagent format.
This enables the `skills` frontmatter field which preloads skill content into subagent context.

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - additive change only
- **Mitigation:** Existing files remain until subsequent issues move them

## Research: Claude Code Subagent Format

From https://code.claude.com/docs/en/sub-agents:

### Frontmatter Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier using lowercase letters and hyphens |
| `description` | Yes | When Claude should delegate to this subagent |
| `tools` | No | Tools the subagent can use (inherits all if omitted) |
| `disallowedTools` | No | Tools to deny |
| `model` | No | `sonnet`, `opus`, `haiku`, or `inherit` (default: inherit) |
| `permissionMode` | No | `default`, `acceptEdits`, `dontAsk`, `bypassPermissions`, `plan` |
| `skills` | No | **Skills to load into subagent context at startup** |
| `hooks` | No | Lifecycle hooks scoped to this subagent |
| `memory` | No | Persistent memory scope: `user`, `project`, `local` |

### Key Insight: skills Field

> The full content of each skill is injected into the subagent's context, not just made available
> for invocation. Subagents don't inherit skills from the parent conversation.

This solves the problem of work-merge needing git-* skill knowledge!

### Example Agent File

```markdown
---
name: code-reviewer
description: Expert code review specialist. Use immediately after writing or modifying code.
tools: Read, Grep, Glob, Bash
model: inherit
skills:
  - review-guidelines
  - code-standards
---

You are a senior code reviewer ensuring high standards of code quality and security.

[System prompt continues...]
```

## Files to Create

| File | Purpose |
|------|---------|
| `plugin/agents/` | Directory for all plugin agents |
| `plugin/agents/README.md` | Documents agent format, fields, and usage |

## Acceptance Criteria
- [ ] `plugin/agents/` directory exists
- [ ] `README.md` documents all frontmatter fields from Claude Code spec
- [ ] `README.md` explains the `skills` field and its importance for preloading context
- [ ] `README.md` includes example agent showing proper format

## Execution Steps

1. **Step 1:** Create `plugin/agents/` directory
   - Files: `plugin/agents/.gitkeep` (placeholder until agents added)

2. **Step 2:** Create comprehensive README.md
   - Document all supported frontmatter fields with descriptions
   - Explain `skills` field for preloading skill content into subagent context
   - Include example agent demonstrating the format
   - List agents that will be migrated (stakeholders + subagent-style skills)

3. **Step 3:** Commit changes
   - Commit type: `config:` (Claude-facing documentation)

## Success Criteria
- [ ] Directory structure created
- [ ] README.md is comprehensive enough to guide subsequent migration issues
