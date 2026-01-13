---
name: cat:spawn-subagent
description: Launch subagent with task context in isolated worktree with token tracking
---

# Spawn Subagent

## Purpose

Launch a Claude Code subagent in an isolated git worktree to execute a specific task. The subagent
operates independently with its own context window while the parent agent continues coordinating.

## When to Use

- Task has a well-defined PLAN.md ready for execution
- Task is independent enough to execute in isolation
- Parent agent needs to continue with other work
- Context window management requires task isolation

## Workflow

### 1. Generate Subagent Identifiers

```bash
# Generate UUID for uniqueness
UUID=$(uuidgen | cut -c1-8)

# Construct names from task context
# Format: {major}.{minor}-{task-name}-sub-{uuid}
WORKTREE_NAME="1.2-implement-parser-sub-a1b2c3d4"
BRANCH_NAME="1.2-implement-parser-sub-a1b2c3d4"
```

### 2. Create Worktree and Branch

```bash
# Create worktree directory
WORKTREE_PATH=".worktrees/${WORKTREE_NAME}"

# Create branch and worktree together
git worktree add -b "${BRANCH_NAME}" "${WORKTREE_PATH}" HEAD
```

### 3. Prepare Task Context

Copy or reference the task's PLAN.md that the subagent will execute:

```bash
# Ensure PLAN.md exists in task directory
TASK_PLAN=".claude/cat/tasks/${MAJOR}.${MINOR}-${TASK_NAME}/PLAN.md"

# Subagent will read this directly - no copy needed
```

### 4. Configure Session Tracking

Create session tracking files:

```bash
# Write session ID to worktree for monitoring script
echo "${SESSION_ID}" > "${WORKTREE_PATH}/.session_id"

# Session file location (for reference)
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"
```

### 5. Launch Subagent

```bash
# Change to worktree and launch Claude Code
cd "${WORKTREE_PATH}"

# Launch with task context - MUST include completion marker requirement
claude --resume "${SESSION_ID}" \
  --prompt "Execute PLAN.md at ${TASK_PLAN}.

COMPLETION REQUIREMENT:
When finished, write completion marker to worktree:
  echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json

Calculate tokens: jq -s '[.[] | select(.type == \"assistant\") | .message.usage | (.input_tokens + .output_tokens)] | add' \"/home/node/.config/claude/projects/-workspace/\${SESSION_ID}.jsonl\"
Count compactions: jq -s '[.[] | select(.type == \"summary\")] | length' same-file"
```

**CRITICAL**: The `.completion.json` marker enables lightweight monitoring. Without it, the parent
must parse session files to detect completion.

### 6. Record Spawn in Parent STATE.md

Update parent's tracking:

```yaml
subagents:
  - id: a1b2c3d4
    task: 1.2-implement-parser
    worktree: .worktrees/1.2-implement-parser-sub-a1b2c3d4
    branch: 1.2-implement-parser-sub-a1b2c3d4
    session: ${SESSION_ID}
    spawned_at: 2026-01-10T14:30:00Z
    status: running
```

## Examples

### Basic Spawn

```bash
# Parent agent spawning subagent for parser task
TASK="1.2-implement-parser"
UUID="a1b2c3d4"
WORKTREE=".worktrees/${TASK}-sub-${UUID}"

git worktree add -b "${TASK}-sub-${UUID}" "${WORKTREE}" HEAD

# Write session ID for monitoring script
echo "${SESSION_ID}" > "${WORKTREE}/.session_id"

# Launch subagent with completion marker requirement
cd "${WORKTREE}"
claude --prompt "Execute task 1.2 PLAN.md.
On completion: echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json"
```

### Spawn with Token Budget

```bash
# Include token budget and completion marker requirement
claude --prompt "Execute task 1.2 PLAN.md. Token budget: 80,000 tokens.
On completion: echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json"
```

## Anti-Patterns

### Do NOT spawn without PLAN.md

```bash
# ❌ Spawning with vague instructions
claude --prompt "Work on the parser"

# ✅ Spawning with concrete plan and completion marker requirement
claude --prompt "Execute PLAN.md at .claude/cat/tasks/1.2-implement-parser/PLAN.md.
On completion: echo '{\"status\":\"success\",...}' > .completion.json"
```

### Do NOT assume subagent will infer implementation details

When specific API usage or patterns are required, provide explicit before/after code examples:

```bash
# ❌ Vague instruction - subagent may find different solution
claude --prompt "Remove the unnecessary cast in LexerTest.java"

# ✅ Explicit code example showing expected change
claude --prompt "Change LexerTest.java line 625:
  FROM: requireThat(token.text() == token.decodedText(), \"sameInstance\").isTrue();
  TO:   requireThat(token.text(), \"token.text()\").isReferenceEqualTo(token.decodedText(), \"token.decodedText()\");"
```

**Why**: Subagents optimize for passing tests/builds. Without explicit examples, they may find alternative
solutions (e.g., @SuppressWarnings) that technically work but don't match the intended approach.

### Do NOT let subagent derive test expected values from actual output

For parser/test tasks, include manual derivation requirement in prompt:

```bash
# ❌ Missing test derivation guidance
claude --prompt "Add parser tests for new feature"

# ✅ Explicit manual derivation requirement
claude --prompt "Add parser tests for new feature.

CRITICAL: Test expected values MUST be manually derived:
1. Analyze source string character by character
2. Determine expected node types from Java grammar
3. Use (0, 0) placeholders for positions initially
4. VERIFY actual positions are correct before updating expected values
5. NEVER copy actual output as expected values without verification"
```

**Why**: Subagents may use placeholder technique incorrectly - copying actual output without verification
creates tests that pass but don't validate correctness.

### Do NOT spawn dependent tasks simultaneously

```bash
# ❌ Spawning B that depends on A's output
spawn-subagent task-a
spawn-subagent task-b  # Depends on task-a!

# ✅ Wait for dependency
spawn-subagent task-a
# ... wait for completion ...
spawn-subagent task-b
```

### Do NOT forget worktree cleanup path

```bash
# ❌ No tracking of worktree location
git worktree add somewhere

# ✅ Track for later cleanup
WORKTREE=".worktrees/${TASK}-sub-${UUID}"
git worktree add "${WORKTREE}"
# Record in STATE.md for merge-subagent skill
```

### Do NOT spawn from within a subagent worktree

Subagents should not spawn further subagents. If a task needs decomposition, report back to the
parent agent.

## Related Skills

- `cat:monitor-subagents` - Check status of spawned subagents
- `cat:collect-results` - Gather results when subagent completes
- `cat:merge-subagent` - Merge subagent work back to task branch
- `cat:parallel-execute` - Spawn multiple subagents concurrently
