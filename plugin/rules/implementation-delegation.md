---
mainAgent: true
subAgents: []
---
## Implementation Delegation
**CRITICAL**: Main agent orchestrates; subagents implement.

The main agent MUST NOT mutate any file by any means — regardless of change size, framing (fix/correction/cleanup),
or whether the request came from the work skill, a direct user request, or an ad-hoc ask. This prohibition covers all
file-modifying tools and Bash commands, including but not limited to:
- Edit, Write, NotebookEdit tools
- Bash commands that write files: `sed -i`, `cat >`, `echo >>`, `tee`, heredoc (`<<EOF`), `awk ... > file`, `cp`,
  `mv`, `touch`, `truncate`, `install`, `patch`, and any other command that creates or modifies file content

All file mutations must be delegated to a subagent via the Task tool.

**Main agent permitted actions — nothing else is allowed**:
- Reading and exploring files for planning (Read, Glob, Grep, Bash read-only commands)
- Orchestration decisions (which task next, how to structure work)
- Spawning subagents via the Task tool to perform implementation
- User interaction and approval gates

**Why delegation matters**:
- Preserves main agent context for orchestration
- Subagent failures don't corrupt main session
- Parallel implementation possible
- Clear separation: main agent = brain, subagent = hands
