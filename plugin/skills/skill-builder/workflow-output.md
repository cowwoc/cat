# Clean Output Standards

## Core Principle

User-facing output should be clean, focused, and demo-ready. Hide internal implementation
noise while showing meaningful progress and results.

## Two Approaches to Clean Output

### 1. Pre-Computation (Deterministic Outputs)

For outputs with precise formatting requirements (boxes, tables, aligned text), extract
computation to skill handlers.

**Why?** Agents make errors on:
- Emoji width calculations
- Box border alignment
- Padding/spacing arithmetic
- Character counting

**Pattern:**
```
Handler pre-computes → Skill outputs verbatim → User sees correct result
```

**Implementation:**
1. Create handler in `plugin/hooks/skill_handlers/`
2. Handler computes ALL output variants before skill runs
3. Skill checks for "OUTPUT TEMPLATE {NAME} OUTPUT" in context
4. If not found: **FAIL immediately** (see error-handling.md)
5. If found: Output the template result exactly

**Checklist:**
- [ ] Skill NEVER invokes scripts via Bash - user sees no tool calls
- [ ] Skill REQUIRES template results - fail-fast if missing
- [ ] No "if not found, compute manually..." fallback patterns

### 2. Subagent Batching (Multi-Step Operations)

For operations involving many tool calls, delegate to subagents. Subagent internal
tool calls are invisible to the parent conversation.

**Why?**

| Approach | Visible Tool Calls | User Experience |
|----------|-------------------|-----------------|
| Direct execution | 20+ Read/Bash/Grep | Noisy, confusing |
| Progress messaging | 20+ (contextualized) | Better, still noisy |
| **Batched subagents** | 3-5 Task invocations | Clean, demo-ready |

**Rule of thumb:** If a phase involves 3+ tool calls, delegate to a subagent.

**Pattern:**
```
Main agent shows progress indicator
  └── Subagent executes (tool calls invisible)
      └── Returns structured result
Main agent shows result summary
```

## Phase Batching for /cat:work

| Phase Batch | Operations Bundled | Returns |
|-------------|-------------------|---------|
| **Preparation** | Read STATE.md, PLAN.md, check deps, create worktree | {ready, worktreePath, estimate} |
| **Exploration** | Search codebase, find patterns, check duplicates | {findings, filesToModify} |
| **Planning** | Make decisions, create implementation spec | {spec, approach, steps} |
| **Implementation** | All code changes, tests, commits | {commits, filesChanged, tokens} |
| **Review** | Spawn reviewers, aggregate results | {status, concerns} |
| **Finalization** (Direct) | Merge, cleanup worktree, update state | {merged, branch} |

## Progress Indicators

With pre-computation or subagent batching, only show:
- `◆ {Phase}...` before operation
- `✓ {Result summary}` after completion

```
◆ Preparing task execution...
✓ Ready: worktree at .worktrees/2.0-task-name, estimate 45K tokens

◆ Exploring codebase...
✓ Found: 3 files to modify, no duplicates

◆ Implementing changes...
✓ Complete: 2 commits, 5 files, 32K tokens
```

## When NOT to Batch/Pre-Compute

- **User approval gates** - Need interactive response
- **Error handling** - May require user decision
- **Final merge** - May need conflict resolution
- **Debugging** - User may need to see individual steps

## Anti-Patterns

### Too Many Small Subagents

```
# BAD: Overhead exceeds benefit
◆ Reading STATE.md...
[Task tool]
◆ Reading PLAN.md...
[Task tool]

# GOOD: Batch related operations
◆ Loading task context...
[Single Task tool that reads both]
```

### Main Agent Doing Subagent Work

```
# BAD: Main agent shows all tool calls
● Read(STATE.md)
● Read(PLAN.md)
● Bash(check dependencies)

# GOOD: Delegate to subagent
◆ Preparing execution context...
[Task tool - internal calls invisible]
✓ Context loaded, ready to proceed
```

### Manual Computation When Handler Exists (M256)

```
# BAD: Agent computes formatting manually
Let me calculate the box width...
[Makes arithmetic errors]

# ALSO BAD: Agent "reconstructs" output instead of copy-pasting
[Runs Bash commands to gather data]
[Builds box manually using similar characters]
[Emojis render as dots because agent used wrong characters]

# GOOD: Use output template VERBATIM
[Locates "OUTPUT TEMPLATE" block in context]
[Copy-pastes exact content without modification]
[Emojis and box characters display correctly]
```

**Self-check before outputting template content:**
- [ ] Content starts with proper box characters (`╭─`, `╭──`)
- [ ] Emojis are visible, not dots or `?` symbols
- [ ] You copied (not retyped) the content
- [ ] You did NOT run Bash/Read to gather data yourself

**If checks fail:** You are reconstructing, not pasting. Find the OUTPUT TEMPLATE block.

## Choosing the Right Approach

| Scenario | Approach |
|----------|----------|
| Formatted output (boxes, tables) | Pre-computation |
| Multi-step data gathering | Subagent batching |
| Simple file reads (1-2 files) | Direct execution |
| User interaction required | Direct execution |
| Deterministic transformation | Pre-computation |
| Exploratory search | Subagent batching |
