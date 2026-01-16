---
name: cat:collect-results
description: Gather results from completed subagent including commits, metrics, and state updates
---

# Collect Results

## Purpose

Extract work products from a completed subagent's worktree, including commit history, code changes,
token metrics, and status information. Prepares the subagent's work for integration back into the
parent task branch.

## When to Use

- Subagent has signaled completion
- Subagent has hit context limits and partial results are needed
- Monitoring indicates subagent is stalled or needs intervention
- Before merging subagent branch to task branch

## Workflow

**Progress Output (MANDATORY):**

Display progress at each step using this format:
```
[Step N/8] Step description (P% | Xs elapsed | ~Ys remaining)
✅ Completed: result summary
```

Steps: 1. Verify completion, 2. Extract commits, 3. Parse metrics, 4. Read work products, 5. Extract
status, 6. Report metrics to user, 7. Update STATE.md, 8. Prepare for merge

### 1. Verify Subagent Completion

Check for completion marker file (fast path, no session parsing):

```bash
WORKTREE=".worktrees/${TASK}-sub-${UUID}"
COMPLETION_FILE="${WORKTREE}/.completion.json"

# Check for completion marker (preferred - lightweight)
if [ -f "$COMPLETION_FILE" ]; then
  echo "Subagent completed"
  cat "$COMPLETION_FILE"  # Contains status, tokensUsed, compactionEvents, summary
else
  echo "Subagent not yet complete or marker not written"
fi
```

**Why completion marker?** Reading `.completion.json` (~200 bytes) is far cheaper than parsing
the session JSONL file (potentially megabytes of conversation history).

### 2. Extract Commit History

```bash
cd "${WORKTREE}"

# Get commits made by subagent (since branch creation)
git log --oneline origin/HEAD..HEAD

# Get detailed commit info
git log --format="%H %s" origin/HEAD..HEAD > /tmp/subagent-commits.txt
```

### 3. Parse Token Metrics

**Preferred: Read from completion marker** (already computed by subagent):

```bash
COMPLETION_FILE="${WORKTREE}/.completion.json"
if [ -f "$COMPLETION_FILE" ]; then
  TOTAL_TOKENS=$(jq -r '.tokensUsed // 0' "$COMPLETION_FILE")
  COMPACTIONS=$(jq -r '.compactionEvents // 0' "$COMPLETION_FILE")
  STATUS=$(jq -r '.status // "unknown"' "$COMPLETION_FILE")
fi
```

**Fallback: Parse session file** (only if marker unavailable):

```bash
SESSION_ID=$(cat "${WORKTREE}/.session_id" 2>/dev/null)
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

if [ -f "$SESSION_FILE" ]; then
  TOTAL_TOKENS=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
    (.input_tokens + .output_tokens)] | add // 0' "${SESSION_FILE}")
  COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")
fi
```

### 4. Read Subagent Work Products

```bash
cd "${WORKTREE}"

# List modified files
git diff --name-only origin/HEAD..HEAD

# Get full diff for review
git diff origin/HEAD..HEAD > /tmp/subagent-changes.diff
```

### 5. Extract Subagent Status

If subagent maintained a STATE.md or status file:

```bash
# Read subagent's final state
cat "${WORKTREE}/.claude/cat/tasks/${TASK}/STATE.md"

# Or check for completion report
cat "${WORKTREE}/COMPLETION_REPORT.md" 2>/dev/null
```

### 6. MANDATORY: Report Token Metrics to User

**Before updating state, present token metrics to user:**

```
## Subagent Execution Report

**Subagent:** a1b2c3d4
**Task:** 1.2-implement-parser
**Status:** success

**Token Usage:**
- Total tokens: 65,000 (32.5% of 200K context)
- Input tokens: 45,000
- Output tokens: 20,000
- Compaction events: 0
- Execution quality: Good ✓

**Work Summary:**
- Commits: 5
- Files changed: 12
- Lines: +450 / -120
```

**Why mandatory:** Users cannot observe subagent execution. This report is the only visibility
into what happened during subagent execution and whether quality may have degraded.

**If compaction events > 0, add warning:**

```
⚠️ CONTEXT COMPACTION DETECTED

The subagent experienced context pressure and may have produced lower quality output.
Consider invoking /cat:decompose-task for similar tasks in the future.
```

### 7. Update Parent STATE.md

Record collection results in parent's tracking:

```yaml
subagents:
  - id: a1b2c3d4
    task: 1.2-implement-parser
    status: collected  # Changed from 'running'
    collected_at: 2026-01-10T15:00:00Z
    results:
      commits: 5
      files_changed: 12
      lines_added: 450
      lines_removed: 120
    metrics:
      total_tokens: 65000
      input_tokens: 45000
      output_tokens: 20000
      compaction_events: 0
    ready_for_merge: true
    reported_to_user: true  # MANDATORY - metrics must be shown to user
```

### 8. Prepare for Merge

```bash
# Ensure subagent branch is up to date
cd "${WORKTREE}"
git status

# Note any uncommitted changes
if [ -n "$(git status --porcelain)" ]; then
  echo "WARNING: Uncommitted changes in subagent worktree"
  git status --short
fi
```

## Examples

### Successful Collection

```yaml
collection_report:
  subagent_id: a1b2c3d4
  task: 1.2-implement-parser
  collection_status: success

  commits:
    - hash: abc123
      message: "feature: implement basic parser structure"
    - hash: def456
      message: "feature: add expression parsing"
    - hash: ghi789
      message: "test: add parser unit tests"

  metrics:
    total_tokens: 65000
    efficiency: 0.89  # commits per 10K tokens
    compactions: 0

  files_summary:
    - src/parser/Parser.java (new)
    - src/parser/ExpressionParser.java (new)
    - test/parser/ParserTest.java (new)

  next_action: ready_for_merge
```

### Partial Collection (Context Limit Hit)

```yaml
collection_report:
  subagent_id: b2c3d4e5
  task: 1.3-implement-formatter
  collection_status: partial

  reason: context_limit_reached
  compaction_events: 2

  commits:
    - hash: jkl012
      message: "feature: implement basic formatter"
    # Only 1 of 3 planned commits completed

  remaining_work:
    - "Implement indent handling"
    - "Add line wrapping"

  metrics:
    total_tokens: 195000
    efficiency: 0.05  # Low due to compaction overhead

  next_action: decompose_remaining
  recommendation: "Spawn new subagent for remaining work"
```

## Anti-Patterns

### Wait for completion before collecting

```bash
# ❌ Interrupting active work
collect-results "${SUBAGENT}"  # Still processing!

# ✅ Wait for completion or explicit intervention reason
if is_complete "${SUBAGENT}" || needs_intervention "${SUBAGENT}"; then
  collect-results "${SUBAGENT}"
fi
```

### Handle uncommitted changes before proceeding

```bash
# ❌ Proceeding with dirty worktree
collect-results "${SUBAGENT}"
merge-subagent "${SUBAGENT}"

# ✅ Handle uncommitted work
if has_uncommitted_changes "${SUBAGENT}"; then
  echo "WARNING: Uncommitted changes detected"
  # Either commit them or document as lost
fi
```

### Always collect full metrics

```bash
# ❌ Only grabbing commits
git log --oneline > results.txt
# Done!

# ✅ Full metrics for learning
collect_commits "${SUBAGENT}"
collect_token_metrics "${SUBAGENT}"
collect_compaction_events "${SUBAGENT}"
update_parent_state "${SUBAGENT}"
```

### Preserve partial progress from incomplete work

```bash
# ❌ Discarding incomplete work
if [ "${STATUS}" != "complete" ]; then
  echo "Incomplete, discarding"
  cleanup_worktree "${SUBAGENT}"
fi

# ✅ Preserve partial progress
if [ "${STATUS}" != "complete" ]; then
  echo "Collecting partial results"
  document_remaining_work "${SUBAGENT}"
  # Results can still be merged
fi
```

## Related Skills

- `cat:monitor-subagents` - Check if subagent is ready for collection
- `cat:merge-subagent` - Merge collected results to task branch
- `cat:token-report` - Detailed analysis of token usage
- `cat:decompose-task` - Split remaining work after partial collection
