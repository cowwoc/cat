<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Shared Agent Bodies

This directory contains engine-neutral role bodies for CAT agents. Engine-specific wrappers live in:

- `plugin/agents/claude/` for Claude Code custom agents
- `plugin/agents/codex/` for Codex custom-agent definitions

Do not add Claude Code frontmatter, Codex model names, tool lists, or engine-specific invocation syntax to files in
this directory. Put those details in the engine wrapper and keep the role body here focused on behavior,
responsibilities, inputs, outputs, and review criteria.

## Directory Structure

```
plugin/agents/common/
├── blue-team-agent.md
├── diff-validation-agent.md
├── instruction-analyzer-agent.md
├── instruction-builder-implement-agent.md
├── instruction-design-agent.md
├── instruction-extraction-agent.md
├── instruction-grader-agent.md
├── plan-review-agent.md
├── red-team-agent.md
├── stakeholder-*.md
├── work-execute.md
├── work-merge.md
├── work-squash.md
└── work-verify.md
```

## Wrapper Contract

Every shared body should have matching engine wrappers with the same filename:

- Claude wrapper: `plugin/agents/claude/{name}.md`
- Codex wrapper: `plugin/agents/codex/{name}.toml`

The wrapper owns engine-specific metadata. The shared body owns the agent role.
