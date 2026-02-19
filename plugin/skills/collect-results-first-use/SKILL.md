---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Collect Results

## Purpose

Extract work products from a completed subagent's worktree, including commit history, code changes,
token metrics, and status information. Prepares the subagent's work for integration back into the
parent issue branch.

## When to Use

- Subagent has signaled completion
- Subagent has hit context limits and partial results are needed
- Monitoring indicates subagent is stalled or needs intervention
- Before merging subagent branch to issue branch

## Workflow

**Progress Output (MANDATORY):**

Display collection progress using visible feedback symbols:

**On collection start:**
```
◆ Collecting results: {subagent-id}...
```

**On successful collection:**
```
✓ Subagent complete: {N}K tokens · {N} commits
  → Files changed: {N}
  → Status: {success|partial|failed}
```

**On collection with issues:**
```
⚠ Subagent complete with concerns: {N}K tokens · {N} commits
  → Compaction events: {N}
  → Discovered issues: {N}
```

These symbols match the phase-based progress format used in `/cat:work`.

Steps: Verify completion, Extract commits, Parse metrics, Extract issues, Report to user, Update STATE.md

### 1. Verify Subagent Completion

Check for completion marker file (fast path, no session parsing):

```bash
WORKTREE=".claude/cat/worktrees/${ISSUE}-sub-${UUID}"
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
# Get commits made by subagent (since branch creation)
git -C "${WORKTREE}" log --oneline origin/HEAD..HEAD

# Get detailed commit info
git -C "${WORKTREE}" log --format="%H %s" origin/HEAD..HEAD > /tmp/subagent-commits.txt
```

### 3. Parse Token Metrics

**CRITICAL: Token totals must span ALL compaction events.**

Session files contain entries BEFORE and AFTER any compaction. The jq command below parses ALL
assistant entries regardless of when compaction occurred, providing cumulative totals.

**Preferred: Read from completion marker** (already computed by subagent):

```bash
COMPLETION_FILE="${WORKTREE}/.completion.json"
if [ -f "$COMPLETION_FILE" ]; then
  TOTAL_TOKENS=$(jq -r '.tokensUsed // 0' "$COMPLETION_FILE")
  INPUT_TOKENS=$(jq -r '.inputTokens // 0' "$COMPLETION_FILE")
  OUTPUT_TOKENS=$(jq -r '.outputTokens // 0' "$COMPLETION_FILE")
  COMPACTIONS=$(jq -r '.compactionEvents // 0' "$COMPLETION_FILE")
  STATUS=$(jq -r '.status // "unknown"' "$COMPLETION_FILE")
fi
```

**If `.completion.json` is missing: FAIL immediately.**

```bash
if [ ! -f "$COMPLETION_FILE" ]; then
  echo "ERROR: .completion.json not found at ${COMPLETION_FILE}"
  echo ""
  echo "The subagent should have written this file on completion."
  echo "Possible causes:"
  echo "1. Subagent is still running (check /cat:monitor-subagents)"
  echo "2. Subagent crashed before writing completion marker"
  echo "3. Worktree path is incorrect"
  echo ""
  echo "Do NOT proceed with merge until completion file exists."
  exit 1
fi
```

**Why fail-fast?** Proceeding without `.completion.json` means no metrics are recorded. The subagent
work cannot be properly evaluated, and token tracking becomes incomplete. This defeats the purpose
of structured subagent management.

**Why totalTokens from toolUseResult?** The session file stores Task tool completion results with
`totalTokens` which represents the full context the subagent processed. This matches the CLI
"Done (X tool uses · XK tokens · Xm Xs)" display and is the correct metric for monitoring.

### 4. Extract Discovered Issues

If curiosity was medium or high, the subagent may have noted issues in `.completion.json`:

```bash
COMPLETION_FILE="${WORKTREE}/.completion.json"
ISSUES=$(jq -r '.discoveredIssues // []' "$COMPLETION_FILE")
ISSUE_COUNT=$(echo "$ISSUES" | jq 'length')

if [ "$ISSUE_COUNT" -gt 0 ]; then
  echo "Discovered issues: $ISSUE_COUNT"
  echo "$ISSUES" | jq -r '.[] | "- [\(.severity)] \(.file):\(.line) - \(.description)"'
fi
```

**Issue format in .completion.json:**
```json
{
  "discoveredIssues": [
    {
      "file": "src/parser/Lexer.java",
      "line": 142,
      "type": "code-quality",
      "severity": "medium",
      "description": "Duplicate token validation logic could be extracted",
      "benefitCost": 2.5
    }
  ]
}
```

**Important:** The main agent handles these issues based on the `patience` setting (see
work.md handle_discovered_issues step). This skill only extracts them.

### 5. Read Subagent Work Products

```bash
# List modified files
git -C "${WORKTREE}" diff --name-only origin/HEAD..HEAD

# Get full diff for review
git -C "${WORKTREE}" diff origin/HEAD..HEAD > /tmp/subagent-changes.diff
```

### 6. Extract Subagent Status

If subagent maintained a STATE.md or status file:

```bash
# Read subagent's final state
cat "${WORKTREE}/.claude/cat/issues/${ISSUE}/STATE.md"

# Or check for completion report
cat "${WORKTREE}/COMPLETION_REPORT.md" 2>/dev/null
```

### 7. MANDATORY: Report Token Metrics to User

**CRITICAL: Verify token values before reporting - never estimate or guess.**

Before presenting metrics, verify you have ACTUAL measured values:

```bash
# Verify .completion.json exists and contains numeric values
if [ -f "$COMPLETION_FILE" ]; then
  TOTAL=$(jq -r '.tokensUsed // 0' "$COMPLETION_FILE")
  if [ "$TOTAL" -gt 0 ]; then
    echo "Token metrics verified from .completion.json"
  else
    echo "WARNING: No token data in .completion.json - parsing session file"
    # Fall back to session file parsing (see step 3)
  fi
fi

# Sanity check: implementation subagents typically use 30K-150K tokens
# If value seems unreasonably low (< 10K for implementation), verify source
```

**Anti-pattern:** Presenting token metrics without actually reading them from `.completion.json`
or session file. Claiming "subagent used X tokens" without verification is a measurement bug.

**Before updating state, present token metrics to user.**

**CRITICAL: Output directly WITHOUT code blocks.** Markdown `**bold**` renders correctly
when output as plain text, but shows as literal asterisks inside triple-backtick code blocks.

Output format (do NOT wrap in ```):

## Subagent Execution Report

**Subagent:** a1b2c3d4
**Issue:** 1.2-implement-parser
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

**Discovered Issues:** 2 (will be handled by main agent based on patience setting)

**Why mandatory:** Users cannot observe subagent execution. This report is the only visibility
into what happened during subagent execution and whether quality may have degraded.

**If compaction events > 0, add warning:**

```
⚠️ CONTEXT COMPACTION DETECTED

The subagent experienced context pressure and may have produced lower quality output.
Consider invoking /cat:decompose-issue for similar issues in the future.
```

### 8. Update Parent STATE.md

Record collection results in parent's tracking:

```yaml
subagents:
  - id: a1b2c3d4
    issue: 1.2-implement-parser
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

### 9. Prepare for Merge

```bash
# Ensure subagent branch is up to date
git -C "${WORKTREE}" status

# Note any uncommitted changes
if [ -n "$(git -C "${WORKTREE}" status --porcelain)" ]; then
  echo "WARNING: Uncommitted changes in subagent worktree"
  git -C "${WORKTREE}" status --short
fi
```

## Examples

### Successful Collection

```yaml
collection_report:
  subagent_id: a1b2c3d4
  issue: 1.2-implement-parser
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
  issue: 1.3-implement-formatter
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

### Collect cumulative tokens spanning compactions

```bash
# ❌ Only counting post-compaction tokens
TOKENS=$(jq -s 'last | .message.usage | .input_tokens + .output_tokens' "${SESSION_FILE}")
# This misses all pre-compaction token usage!

# ✅ Cumulative totals spanning ALL compactions
TOKENS=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  (.input_tokens + .output_tokens)] | add' "${SESSION_FILE}")
# Sums ALL entries regardless of compaction events
```

**Why this matters:** Token estimates are for the ENTIRE issue. If a subagent uses 50K tokens,
hits compaction, then uses another 30K tokens, the actual usage is 80K - not 30K. Accurate
reporting enables proper estimate validation.

### Preserve partial progress from incomplete work

```bash
# ❌ Discarding incomplete work
if [ "${STATUS}" != "closed" ]; then
  echo "Incomplete, discarding"
  cleanup_worktree "${SUBAGENT}"
fi

# ✅ Preserve partial progress
if [ "${STATUS}" != "closed" ]; then
  echo "Collecting partial results"
  document_remaining_work "${SUBAGENT}"
  # Results can still be merged
fi
```

## Related Skills

- `cat:monitor-subagents` - Check if subagent is ready for collection
- `cat:merge-subagent` - Merge collected results to issue branch
- `cat:token-report` - Detailed analysis of token usage
- `cat:decompose-issue` - Split remaining work after partial collection
