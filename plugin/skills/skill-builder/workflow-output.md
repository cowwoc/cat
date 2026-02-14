<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Clean Output Standards

([BANG] = the exclamation mark, written as placeholder to avoid preprocessor expansion)

## Core Principle

User-facing output should be clean, focused, and demo-ready. Hide internal implementation
noise while showing meaningful progress and results.

## Two Approaches to Clean Output

### 1. Silent Preprocessing (Formatted Output)

For outputs with precise formatting requirements (boxes, tables, aligned text), use
Claude Code's **silent preprocessing** feature with the [BANG]`command` syntax.

**Why?** Agents make errors on:
- Emoji width calculations
- Box border alignment
- Padding/spacing arithmetic
- Character counting

**Pattern:**
```
Skill loads → Commands execute silently → Claude receives rendered content → Output verbatim
```

**The [BANG]`command` syntax:**

```markdown
## My Skill

Here is the current status:
[BANG]`cat-status-display.sh --format=box`

Now analyze the results...
```

**How it works:**
1. When Claude Code loads the skill, it finds [BANG]`command` patterns
2. Each command executes **immediately** during skill expansion
3. The command output **replaces the placeholder** in the skill content
4. Claude receives the fully-rendered prompt with actual data

**Benefits:**
- **Guaranteed correctness**: Output is computed, not approximated by the LLM
- **No visible tool calls**: Users see clean skill output
- **Simple implementation**: Just shell scripts, no Python handlers
- **No LLM manipulation errors**: Prevents formatting mistakes

**Implementation:**
1. Create script in `plugin/scripts/` (e.g., `cat-display.sh`)
2. Script outputs formatted content to stdout
3. Reference in skill with [BANG]`script.sh args`

**Checklist:**
- [ ] Script handles all formatting (boxes, alignment, emoji widths)
- [ ] Script accepts necessary arguments (task ID, phase, etc.)
- [ ] Claude outputs the result exactly as received
- [ ] No manual manipulation of the preprocessed output

---

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
✓ Ready: worktree at .claude/cat/worktrees/2.0-issue-name, estimate 45K tokens

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

### Manual Computation Instead of Preprocessing

```
# BAD: Agent computes formatting manually
Let me calculate the box width...
[Makes arithmetic errors]

# ALSO BAD: Agent "reconstructs" output
[Runs Bash commands to gather data]
[Builds box manually using similar characters]
[Emojis render as dots because agent used wrong characters]

# GOOD: Preprocessed output received
[Skill loaded with [BANG]`command` preprocessing]
[Claude receives already-rendered content]
[Outputs exactly as received]
```

**Self-check for preprocessed output:**
- [ ] Content starts with proper box characters (`╭─`, `╭──`)
- [ ] Emojis are visible, not dots or `?` symbols
- [ ] You did NOT run Bash/Read to gather data yourself
- [ ] You are outputting exactly what the preprocessing provided

## Choosing the Right Approach

| Scenario | Approach |
|----------|----------|
| Formatted output (boxes, tables) | Silent preprocessing ([BANG]`command`) |
| Multi-step data gathering | Subagent batching |
| Simple file reads (1-2 files) | Direct execution |
| User interaction required | Direct execution |
| Deterministic transformation | Silent preprocessing ([BANG]`command`) |
| Exploratory search | Subagent batching |

**Decision tree:**

```
Need formatted output (boxes, tables, alignment)?
  │
  ├─ Yes → Use silent preprocessing ([BANG]`command`)
  │
  └─ No → Multiple tool calls needed?
           │
           ├─ Yes (3+) → Subagent batching
           │
           └─ No → Direct execution
```
