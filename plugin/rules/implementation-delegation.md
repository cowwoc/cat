---
mainAgent: true
subAgents: []
---
## Implementation Delegation
**CRITICAL**: Main agent orchestrates; subagents implement.

When implementing code changes within a task, delegate to a subagent via the Task tool.
Main agent should NOT directly edit files for implementation work.

**Delegate via Task tool when**:
- Fixing multiple violations (PMD, Checkstyle, lint)
- Renaming/refactoring across files
- Any implementation requiring more than 2-3 edits
- Mechanical transformations (format changes, renames)

**Main agent directly handles**:
- Single-line config changes
- Reading/exploring code for planning
- Orchestration decisions (which task next)
- User interaction and approval gates

**Why delegation matters**:
- Preserves main agent context for orchestration
- Subagent failures don't corrupt main session
- Parallel implementation possible
- Clear separation: main agent = brain, subagent = hands
