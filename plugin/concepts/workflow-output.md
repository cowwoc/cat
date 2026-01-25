# Workflow Output Standards

## Core Principle: Subagent Batching

Hide tool call noise by delegating batched operations to subagents. Subagent internal
tool calls are invisible to the parent conversation.

## Why Subagent Batching

| Approach | Visible Tool Calls | User Experience |
|----------|-------------------|-----------------|
| Direct execution | 20+ Read/Bash/Grep | Noisy, confusing |
| Progress messaging | 20+ (contextualized) | Better, still noisy |
| **Batched subagents** | 3-5 Task invocations | Clean, demo-ready |

## Batching Strategy

Aggregate overhead: Spawning a subagent has cost. Batch enough work to justify it.

**Rule of thumb:** If a phase involves 3+ tool calls, delegate to a subagent.

## Phase Batches for /cat:work

| Phase Batch | Operations Bundled | Returns |
|-------------|-------------------|---------|
| **Preparation** | Read STATE.md, PLAN.md, check deps, analyze size, create worktree | JSON: {ready, worktreePath, estimate} |
| **Exploration** | Search codebase, find patterns, check duplicates | JSON: {findings, filesToModify} |
| **Planning** | Make decisions, create implementation spec | JSON: {spec, approach, steps} |
| **Implementation** | All code changes, tests, commits | JSON: {commits, filesChanged, tokens} |
| **Review** | Spawn reviewers, aggregate results | JSON: {status, concerns} |
| **Finalization** | Merge, cleanup worktree, update state | JSON: {merged, branch} |

## Progress Indicators (Minimal)

With subagent batching, only show:
- `◆ {Phase}...` before Task invocation
- `✓ {Result summary}` after completion

Example:
```
◆ Preparing task execution...
[Task tool - collapsed]
✓ Ready: worktree at .worktrees/2.0-task-name, estimate 45K tokens

◆ Exploring codebase...
[Task tool - collapsed]
✓ Found: 3 files to modify, no duplicates

◆ Implementing changes...
[Task tool - collapsed]
✓ Complete: 2 commits, 5 files, 32K tokens
```

## When NOT to Batch

- User approval gates (need interactive response)
- Error handling that requires user decision
- Final merge (may need conflict resolution)

## Anti-Patterns

### Too Many Small Subagents
```
# BAD: Overhead exceeds benefit
◆ Reading STATE.md...
[Task tool]
◆ Reading PLAN.md...
[Task tool]
```

### Main Agent Doing Subagent Work
```
# BAD: Main agent reads files directly
● Read(STATE.md)
● Read(PLAN.md)
● Bash(check dependencies)
```

## Integration with Existing Subagent Types

- **Exploration subagent**: Extended to handle preparation + exploration
- **Planning subagent**: Handles decision-making, returns spec
- **Implementation subagent**: Executes spec mechanically
- **General-purpose**: Finalization, review orchestration
