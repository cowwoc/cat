<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
<!-- cat:include ../../common/learn/phase-investigate-subagent-mistake.md -->

## Claude Code Subagent Capability Appendix

Use this appendix only when the active runtime is Claude Code.

### Capability Checks

| Subagent Capability | Available? | Evidence to Check |
|---------------------|------------|-------------------|
| Spawn nested subagents | No | Claude Code subagents do not receive the Task tool |
| Invoke skills dynamically | Runtime-dependent | Check whether the Skill tool is available in the subagent transcript |
| Read/Write/Edit files | Yes | Standard file tools are available unless the agent definition restricts them |
| Run bash commands | Yes | Bash is available unless the agent definition restricts it |
| Web search/fetch | Runtime-dependent | Check the subagent's available tools and actual tool errors |

### Impossible Instruction Patterns

| Instruction Pattern | Why Impossible in Claude Code | Correct Design |
|--------------------|-------------------------------|----------------|
| "Spawn reviewer subagents" | Task tool is unavailable to Claude Code subagents | Main agent spawns reviewers directly |
| "Delegate to sub-subagent" | Claude Code subagents cannot create another Task delegation layer | Flatten to one delegation level |

### Record Format

```yaml
technically_impossible_check:
 instruction_required: "Spawn reviewer subagents for each finding"
 runtime: "claude"
 capability_needed: "Task tool"
 available_to_subagent: false
 conclusion: >
  IMPOSSIBLE in Claude Code because Claude Code subagents cannot spawn nested subagents.
 root_cause: "architectural_flaw"
 fix_type: "Redesign workflow so the main agent spawns reviewers directly"
```
