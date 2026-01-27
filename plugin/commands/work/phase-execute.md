# Work Phase: Execute

Steps for task execution: execute, collect_and_report, aggregate_token_report, token_check, handle_discovered_issues, verify_changes.

---

<step name="execute">

**Execute the PLAN.md:**

**MANDATORY: Always spawn subagent for implementation.**

Main agent is the orchestrator. Subagents do the work. This is NOT optional.

| Task Size | Strategy |
|-----------|----------|
| Any task | Spawn subagent via `/cat:delegate` |
| Large/complex | Consider `/cat:decompose-task` first, then spawn |

**Why subagents are mandatory (not optimization):**
- Fresh context = peak quality (no accumulated noise)
- Token tracking enables proactive decomposition
- Branch isolation provides safe rollback
- Main agent context preserved for orchestration
- Prevents quality degradation from context pressure

**Subagent execution workflow:**

1. Read preferences and include in subagent prompt:
   ```bash
   CURIOSITY=$(jq -r '.curiosity // "medium"' .claude/cat/cat-config.json)
   ```

   **Curiosity instruction** (for IMPLEMENTATION subagent):
   | Level | Implementation Subagent Instruction |
   |-------|-------------------------------------|
   | `low` | "Focus ONLY on the assigned task. Do NOT note or report issues outside the immediate scope." |
   | `medium` | "While working, NOTE obvious issues in files you touch. Report them in .completion.json but do NOT fix them." |
   | `high` | "Actively look for issues and improvement opportunities. Report ALL findings in .completion.json but do NOT fix them." |

   **IMPORTANT:** The implementor subagent NEVER fixes discovered issues directly.

2. Invoke `/cat:delegate` skill with:
   - Task path
   - PLAN.md contents
   - Worktree path
   - Token tracking enabled
   - Curiosity instruction

3. Monitor subagent via `/cat:monitor-subagents`:
   - Check for compaction events
   - Track token usage
   - Handle early failures

4. Collect results via `/cat:collect-results`:
   - Get execution summary
   - Get token usage report
   - Get any issues encountered

**Error Handling:**

If execution fails:
- Capture error details
- Update STATE.md with error
- Present to user with remediation options
- Use AskUserQuestion:
  - "Retry" - Attempt again
  - "Skip" - Mark task blocked, continue to next
  - "Abort" - Stop execution entirely

</step>

<step name="collect_and_report">

**MANDATORY: Collect results and report token metrics to user.**

After subagent completes, invoke `/cat:collect-results` and present metrics.

Output format (do NOT wrap in ```):

## Subagent Execution Report

**Task:** {task-name}
**Status:** {success|partial|failed}

**Token Usage:**
- Total tokens: {N} ({percentage}% of context)
- Input tokens: {input_N}
- Output tokens: {output_N}
- Compaction events: {N}

**Work Summary:**
- Commits: {N}
- Files changed: {N}
- Lines: +{added} / -{removed}

**Why mandatory:** Users cannot observe subagent execution. Token metrics are the only visibility
into execution quality.

**MANDATORY: Compare actual vs estimated tokens:**

```bash
ACTUAL_TOKENS={total_tokens_from_collect_results}
VARIANCE_THRESHOLD=125  # 25% higher = 125% of estimate

ACTUAL_PERCENT=$((ACTUAL_TOKENS * 100 / ESTIMATED_TOKENS))

if [ "${ACTUAL_PERCENT}" -ge "${VARIANCE_THRESHOLD}" ]; then
  echo "⚠️ TOKEN ESTIMATE VARIANCE DETECTED"
  # MANDATORY: Invoke learn-from-mistakes
fi
```

**If actual >= estimate × 1.25 (25% or more higher):**

Invoke `/cat:learn-from-mistakes` with:
- Description: "Token estimate underestimated actual usage by {variance}%"
- Estimated tokens: {ESTIMATED_TOKENS}
- Actual tokens: {ACTUAL_TOKENS}
- Task: {task-name}

</step>

<step name="aggregate_token_report">

**Aggregate token usage across all subagents (multi-subagent tasks):**

For tasks that spawned multiple subagents (parallel execution or decomposed tasks), aggregate
token metrics from all `.completion.json` files.

```bash
CONTEXT_LIMIT=...
HARD_LIMIT_PCT=...
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))

# Find all subagent completion files for this task
TASK_WORKTREES=$(find .worktrees -name ".completion.json" -path "*${TASK_ID}*" 2>/dev/null)

TOTAL_TOKENS=0
TOTAL_EXCEEDED=0

echo "## Aggregate Token Report"
echo "| Subagent | Type | Tokens | % of Limit | Status |"
echo "|----------|------|--------|------------|--------|"

for completion_file in $TASK_WORKTREES; do
  SUBAGENT_NAME=$(dirname "$completion_file" | xargs basename)
  TOKENS=$(jq -r '.tokensUsed // 0' "$completion_file")
  SUBAGENT_TYPE=$(jq -r '.subagentType // "implementation"' "$completion_file")
  PERCENT=$((TOKENS * 100 / CONTEXT_LIMIT))
  TOTAL_TOKENS=$((TOTAL_TOKENS + TOKENS))

  if [ "$TOKENS" -ge "$HARD_LIMIT" ]; then
    STATUS="EXCEEDED"
    TOTAL_EXCEEDED=$((TOTAL_EXCEEDED + 1))
  elif [ "$TOKENS" -ge "$((HARD_LIMIT * 90 / 100))" ]; then
    STATUS="WARNING"
  else
    STATUS="OK"
  fi

  echo "| $SUBAGENT_NAME | $SUBAGENT_TYPE | $TOKENS | ${PERCENT}% | $STATUS |"
done

echo "**Total tokens:** $TOTAL_TOKENS"
echo "**Subagents exceeded hard limit:** $TOTAL_EXCEEDED"
```

**If any subagent exceeded hard limit:**

Trigger learn-from-mistakes for each violation.

</step>

<step name="token_check">

**Evaluate token metrics for decomposition:**

**→ Load token-warning.md workflow if compaction events > 0 or tokens exceed threshold.**

See `concepts/token-warning.md` for:
- Compaction event warning and user decision
- High token usage informational warning
- Decomposition recommendations
- Token estimate variance check (M099)

**Quick reference:**
- Compaction events > 0 → Strong decomposition recommendation with user choice
- High tokens, no compaction → Informational warning only

</step>

<step name="handle_discovered_issues">

**Handle issues discovered by subagent (patience setting):**

After collecting results, check `.completion.json` for discovered issues:

```bash
COMPLETION_FILE="${WORKTREE}/.completion.json"
ISSUES=$(jq -r '.discoveredIssues // []' "$COMPLETION_FILE")
ISSUE_COUNT=$(echo "$ISSUES" | jq 'length')

if [ "$ISSUE_COUNT" -gt 0 ]; then
  PATIENCE=$(jq -r '.patience // "high"' .claude/cat/cat-config.json)
  echo "Found $ISSUE_COUNT discovered issues. Patience: $PATIENCE"
fi
```

**If no issues discovered:** Skip to verify_changes.

**If issues discovered, handle based on patience:**

| Patience | Action |
|----------|--------|
| `high` | Create tasks in FUTURE version backlog (prioritized by benefit/cost) |
| `medium` | Create tasks in CURRENT version backlog |
| `low` | Resume PLANNER subagent to update plan, then re-execute |

**For patience: high**
```
📋 DISCOVERED ISSUES → FUTURE BACKLOG

{N} issues noted during implementation have been added to the backlog
for future versions (sorted by benefit/cost ratio).

Issues will not block current task completion.
```

**For patience: medium**
```
📋 DISCOVERED ISSUES → CURRENT VERSION

{N} issues noted during implementation have been added as tasks
in the current minor version.

Issues will not block current task completion.
```

**For patience: low**

Resume the PLANNER subagent to incorporate fixes:
```
🔄 DISCOVERED ISSUES → IMMEDIATE ACTION

{N} issues noted during implementation. Patience is LOW.
Resuming planner subagent to update the plan with fixes...
```

Use Task tool with `resume: PLANNER_ID`.

</step>

<step name="verify_changes">

**Run verification based on verify setting:**

```bash
VERIFY_LEVEL=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
echo "Verification level: $VERIFY_LEVEL"
```

**MANDATORY (M110): Check for actual source changes first:**

```bash
CAT_BASE_FILE="$(git rev-parse --git-dir)/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
  echo "ERROR: Base branch file not found: $CAT_BASE_FILE"
  exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")
SOURCE_CHANGES=$(git diff --name-only ${BASE_BRANCH}..HEAD | grep -v "\.claude/cat/" | grep -v "CHANGELOG.md" | head -1)

if [[ -z "$SOURCE_CHANGES" ]]; then
  echo "⚡ VERIFICATION: SKIPPED (no source files changed)"
fi
```

| verify | Action |
|--------|--------|
| `none` | Skip verification entirely (fastest iteration) |
| `changed` | Run tests on changed files/modules only |
| `all` | Run full project verification (build + all tests) |

**For verify: none**
```
⚡ VERIFICATION: SKIPPED (verify: none)
```
Skip to stakeholder_review.

**For verify: changed**

Run targeted verification on changed files/modules only:

| Type | Targeted Test Command |
|------|----------------------|
| Maven | `./mvnw test -pl {changed-modules} -am` |
| Node | `npm test -- --findRelatedTests {files}` |
| Python | `pytest {changed-modules}` |
| Go | `go test {changed-packages}` |
| Rust | `cargo test {changed-crates}` |

**For verify: all**

Run full project verification (build + all tests):

| Type | Full Verification Command |
|------|--------------------------|
| Maven | `./mvnw verify` |
| Node | `npm run build && npm test` |
| Python | `pytest` |
| Go | `go build ./... && go test ./...` |
| Rust | `cargo build && cargo test` |

**On verification failure:**

Block progression and present options via AskUserQuestion:
- "Fix issues and retry"
- "Override and proceed (not recommended)"
- "Abort task"

</step>
