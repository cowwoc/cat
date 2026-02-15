# CAT Plugin Agents

This directory contains Claude Code subagents used by the CAT (Claude Assistant Tool)
plugin. Agents are specialized assistants that can be delegated to for specific tasks.

## Agent Format

Agents are Markdown files with YAML frontmatter defining their capabilities and behavior.

### Frontmatter Fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `name` | Yes | string | Unique identifier using lowercase letters and hyphens |
| `description` | Yes | string | When Claude should delegate to this subagent |
| `tools` | No | array | Tools the subagent can use. If omitted, inherits all parent tools |
| `disallowedTools` | No | array | Tools to deny access to |
| `model` | No | string | Model to use: `sonnet`, `opus`, `haiku`, or `inherit` (default: inherit) |
| `permissionMode` | No | string | Permission level: `default`, `acceptEdits`, `dontAsk`, `bypassPermissions`, `plan` |
| `skills` | No | array | **Skills to preload into subagent context at startup** |
| `hooks` | No | object | Lifecycle hooks scoped to this subagent |
| `memory` | No | string | Persistent memory scope: `user`, `project`, `local` |

### The `skills` Field: Context Preloading

**CRITICAL:** The `skills` field is the primary reason for migrating to the agents
directory. When a subagent is invoked, the full content of each listed skill is
injected into the subagent's context at startup.

**Key behaviors:**
- Skills are **preloaded**, not just made available for invocation
- Subagents **do not inherit** skills from the parent conversation
- Use this to ensure subagents have all necessary guidance without manual skill calls

**Example use case:**
A git merge agent needs to know about merge and rebase procedures. By listing these
skills in the frontmatter, the agent has this knowledge immediately available without
requiring explicit skill invocations during execution. Scripts like `git-squash-quick.sh`
are called directly and do not need skill preloading.

```yaml
skills:
  - git-merge-linear
  - validate-git-safety
```

## Example Agent

```markdown
---
name: code-reviewer
description: Expert code review specialist. Use immediately after writing or modifying code.
tools: Read, Grep, Glob, Bash
model: inherit
skills:
  - review-guidelines
  - code-standards
permissionMode: default
---

You are a senior code reviewer ensuring high standards of code quality and security.

Your responsibilities:
1. Review code for correctness, security, and maintainability
2. Check adherence to project conventions and style guides
3. Identify potential bugs and edge cases
4. Suggest improvements and optimizations

Always provide specific, actionable feedback with code examples.
```

## Agents to Migrate

The following components will be migrated to this directory:

### From `plugin/stakeholders/` (COMPLETED)
All stakeholder files have been migrated to this directory with proper agent format:
- stakeholder-architect.md
- stakeholder-deployment.md
- stakeholder-design.md
- stakeholder-legal.md
- stakeholder-marketing.md
- stakeholder-performance.md
- stakeholder-requirements.md
- stakeholder-sales.md
- stakeholder-security.md
- stakeholder-testing.md
- stakeholder-ux.md

### From `plugin/skills/` (subagent-style skills)
- work-merge (calls git-squash-quick.sh directly, needs git-merge-linear, validate-git-safety preloaded)
- Future skills that use delegation patterns

## Directory Structure

```
plugin/agents/
├── README.md                      # This file
├── work-execute.md                # Implementation specialist (Phase 2)
├── work-merge.md                  # Work merge agent
├── stakeholder-architect.md       # Architecture review
├── stakeholder-deployment.md      # Deployment/release review
├── stakeholder-design.md          # Code quality review
├── stakeholder-legal.md           # Legal/compliance review
├── stakeholder-marketing.md       # Marketing readiness review
├── stakeholder-performance.md     # Performance review
├── stakeholder-requirements.md    # Requirements verification review
├── stakeholder-sales.md           # Sales readiness review
├── stakeholder-security.md        # Security review
├── stakeholder-testing.md         # Test coverage review
└── stakeholder-ux.md              # UX/accessibility review
```

## Usage

Agents are invoked by Claude Code automatically when their description matches the
task context, or explicitly via delegation patterns in skills and commands.

## Development Guidelines

1. **Agent Naming:** Use lowercase-with-hyphens matching the filename
2. **Description Precision:** Write clear descriptions that guide delegation decisions
3. **Skill Preloading:** Always list required skills in frontmatter rather than calling them during execution
4. **Tool Restriction:** Only restrict tools when necessary for safety or focus
5. **Model Selection:** Use `inherit` unless a specific model capability is needed
