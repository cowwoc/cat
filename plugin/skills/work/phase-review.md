---
user-invocable: false
---

# Work Phase: Review

Steps for review and approval: stakeholder_review, approval_gate.

---

<step name="stakeholder_review">

**Multi-perspective stakeholder review gate:**

**Skip conditions:**

```bash
VERIFY_LEVEL=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
if [[ "$VERIFY_LEVEL" == "none" ]]; then
  echo "⚡ Stakeholder review: SKIPPED (verify: none)"
  # Skip to approval_gate
fi
```

**Review triggering (based on verify level, NOT trust):**

| Verify | Action |
|--------|--------|
| `none` | Skip all stakeholder reviews |
| `changed` | Run stakeholder reviews |
| `all` | Run stakeholder reviews |

**High-risk detection:** Read the task's PLAN.md Risk Assessment section. Task is high-risk if ANY of:
- Risk section mentions "breaking change", "data loss", "security", "production"
- Task modifies authentication, authorization, or payment code
- Task touches 5+ files
- Task is marked as HIGH risk

**MANDATORY: Run stakeholder review BEFORE user approval.**

**Stakeholders:**

| Stakeholder | Focus |
|-------------|-------|
| requirements | Requirement satisfaction |
| architect | System design, modules, APIs |
| security | Vulnerabilities, validation |
| design | Code quality, complexity |
| testing | Test coverage, edge cases |
| performance | Efficiency, resources |

**Execution:**

1. Identify changed files: `git diff --name-only ${MAIN_BRANCH}..HEAD`
2. Spawn 5 subagents in parallel (one per stakeholder)
3. Each reviews implementation against their criteria
4. Collect JSON responses with concerns and severity

**Aggregation rules:**

| Condition | Result |
|-----------|--------|
| Any CRITICAL concern | REJECTED |
| Any stakeholder REJECTED | REJECTED |
| 3+ HIGH concerns total | REJECTED |
| Only MEDIUM concerns | CONCERNS (proceed) |
| No concerns | APPROVED |

**If REJECTED:**

Behavior depends on trust level:

| Trust | Rejection Behavior |
|-------|-------------------|
| `low` | Ask user: Fix / Override / Abort |
| `medium` | Auto-loop to fix (up to 3 iterations) |

Note: `trust: "high"` skips review entirely, so rejection handling doesn't apply.

**For `trust: "low"` (user decides):**

Present concerns to user:

```
## Stakeholder Review: REJECTED

**Critical Concerns (Must Fix):**
{list concerns with locations and recommendations}

**High Priority Concerns:**
{list concerns}
```

Use AskUserQuestion:
- header: "Review Gate"
- question: "Stakeholder review identified concerns that should be addressed:"
- options:
  - "Fix concerns" - Return to implementation with concern list (Recommended)
  - "Override and proceed" - Continue to user approval with concerns noted
  - "Abort" - Stop task execution

**For `trust: "medium"` or `trust: "high"` (auto-fix):**

```
## Stakeholder Review: REJECTED (Auto-fixing)

Iteration {N}/3 - Automatically addressing concerns...

**Concerns being fixed:**
{list concerns with locations}
```

- Record concerns in task context
- Loop back to `execute` step automatically (no user prompt)
- Subagent receives concerns as additional requirements
- Repeat until APPROVED or max iterations (3) reached

**If max iterations reached (any trust level):**
- Force escalation to user
- Present all remaining concerns
- User decides whether to override or abort

**If APPROVED or CONCERNS:**

Proceed to approval_gate with stakeholder summary:

```
## Stakeholder Review: PASSED

| Stakeholder | Status | Concerns |
|-------------|--------|----------|
| architect | ✓ APPROVED | 0 |
| security | ✓ APPROVED | 0 |
| quality | ⚠ CONCERNS | 2 medium |
| tester | ✓ APPROVED | 0 |
| performance | ✓ APPROVED | 0 |

**Medium Priority (Informational):**
{list if any}
```

</step>

<step name="approval_gate">

**Approval gate (Interactive mode only):**

Skip if `trust: "high"` in config.

### Pre-Approval Checklist (MANDATORY - A010)

**BLOCKING: Do NOT present approval until ALL items are verified.**

| # | Check | How to Verify | Fix if Failed |
|---|-------|---------------|---------------|
| 1 | Commits squashed by type | `git log --oneline ${BASE_BRANCH}..HEAD` shows 1-2 commits | Use `/cat:git-squash` skill |
| 2 | STATE.md status = completed | `grep "Status:" STATE.md` shows `completed` | Edit STATE.md |
| 3 | STATE.md progress = 100% | `grep "Progress:" STATE.md` shows `100%` | Edit STATE.md |
| 4 | STATE.md in commit | `git diff --name-only ${BASE_BRANCH}..HEAD \| grep STATE.md` | Amend commit to include |
| 5 | Diff content ready | `git diff ${BASE_BRANCH}..HEAD` output captured | Run diff command |

```bash
BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base")
TASK_STATE=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${TASK_NAME}/STATE.md"

# Check 1: Commit count (should be 1-2 after squash)
COMMIT_COUNT=$(git rev-list --count ${BASE_BRANCH}..HEAD)
if [[ "$COMMIT_COUNT" -gt 3 ]]; then
  echo "FAIL: $COMMIT_COUNT commits - need to squash first"
  exit 1
fi

# Check 2 & 3: STATE.md status and progress
if ! grep -q "Status.*completed" "$TASK_STATE"; then
  echo "FAIL: STATE.md status not 'completed'"
  exit 1
fi
if ! grep -q "Progress.*100%" "$TASK_STATE"; then
  echo "FAIL: STATE.md progress not '100%'"
  exit 1
fi

# Check 4: STATE.md in commit
if ! git diff --name-only ${BASE_BRANCH}..HEAD | grep -q "STATE.md"; then
  echo "FAIL: STATE.md not in commit"
  exit 1
fi

echo "PASS: All pre-approval checks passed"
```

**MANDATORY: Verify commit exists before presenting approval (M072).**

**MANDATORY: Verify STATE.md in implementation commit (M076/M085).**

**Required STATE.md fields for completion (M092):**

```markdown
- **Status:** completed
- **Progress:** 100%
- **Resolution:** implemented
- **Dependencies:** [any-dependencies]
- **Completed:** {YYYY-MM-DD HH:MM}
- **Tokens Used:** {N}
```

Resolution field is MANDATORY. Valid values: `implemented`, `duplicate`, `obsolete`.

---

Present work summary with checkpoint display.

**CRITICAL: Output directly WITHOUT code blocks (M125).**

### Checkpoint Display (BLOCKING - M311)

**STOP: Find "CHECKPOINT_TASK_COMPLETE" in Pre-rendered Work Boxes context.**

The hook handler pre-computes this box with actual values. Display it VERBATIM:

1. Search your context for `--- CHECKPOINT_TASK_COMPLETE ---`
2. Copy the ENTIRE pre-rendered box that follows (already has actual values)
3. Output it EXACTLY as provided - do NOT modify or reconstruct

**BLOCKED if:** You type your own checkpoint format instead of using the pre-rendered output.
**Why:** Custom formats break visual consistency and miss required fields.

### Diff Display (BLOCKING - M312)

**STOP: Complete ALL steps before presenting approval options.**

**Step 1: Generate rendered diff**
```bash
BASE_BRANCH=$(cat "$(git rev-parse --git-dir)/cat-base")
git diff ${BASE_BRANCH}..HEAD | python3 "${CLAUDE_PLUGIN_ROOT}/scripts/render-diff.py"
```

**Step 2: Copy output into your response**
- The output contains 4-column tables with box characters (╭╮╰╯│)
- Copy-paste this output DIRECTLY into your response text
- Do NOT leave it inside the Bash tool result

**Step 2b: If output is persisted to file (M313)**
When Bash output exceeds limits and is saved to a file:
1. Read the persisted file using the Read tool
2. Display the file contents in your response (use multiple Read calls if needed)
3. Do NOT summarize - show the actual rendered diff content
4. Plain text summaries are NOT acceptable substitutes for rendered diff

**Step 3: Verify before proceeding**
- [ ] Diff tables visible in your response text (not collapsed in tool)?
- [ ] Contains box characters (╭╮╰╯│)?
- [ ] Shows 4-column format (line numbers, old code, new code)?

**BLOCKED if:**
- You only ran `git diff --stat` (file list is NOT a diff)
- Diff is only visible inside Bash tool output
- You skipped render-diff.py
- You showed a plain text summary instead of the rendered diff (M313)

**Why (M160/M201/M261/M312/M313):** Users cannot review changes they cannot see.
Approval without visible diff is meaningless. Summaries lose critical detail.

**CRITICAL (M211): Present render-diff output VERBATIM.**

**CRITICAL (M231): Handle large diffs by showing ALL content.**

**CRITICAL (M313): If output persisted to file, READ and DISPLAY file contents.**

Use AskUserQuestion with options:
- header: "Next Step"
- question: "What would you like to do?"
- options:
  - "✓ Approve and merge" - Merge to base branch, continue to next task
  - "✏️ Request changes" - Need modifications before proceeding
  - "✗ Abort" - Discard work entirely

**CRITICAL (M248): Distinguish task version from base branch in approval messages.**

**MANDATORY: Include subtask context for decomposed tasks (M251).**

---

**If "Request changes":**

Capture user feedback and spawn implementation subagent to address concerns.

**MANDATORY: Main agent does NOT implement feedback directly (M063).**

**Step 1: Capture User Feedback**

Use AskUserQuestion to collect specific feedback:
- header: "Feedback"
- question: "What changes would you like made?"
- freeform: true

**Step 2: Gather Context for Subagent**

```bash
BASE_BRANCH=$(git config --get "branch.$(git rev-parse --abbrev-ref HEAD).cat-base" 2>/dev/null || echo "main")
git diff ${BASE_BRANCH}..HEAD > /tmp/current-implementation.diff
```

**Step 3: Spawn Feedback Implementation Subagent**

Invoke `/cat:delegate` with feedback context.

**Step 4: Collect Results**

Invoke `/cat:collect-results`.

**Step 5: Merge Feedback Changes to Task Branch**

Invoke `/cat:merge-subagent`.

**Step 6: RE-PRESENT Approval Gate**

**MANDATORY: Loop back to approval gate with updated changes.**

Use the **CHECKPOINT_FEEDBACK_APPLIED** box from Pre-rendered Work Boxes.

Then re-present approval options via AskUserQuestion.

**Maximum Iterations Safety:**

If iterations exceed 5:
```
⚠️ FEEDBACK ITERATION LIMIT

5 feedback iterations completed without approval.

Options:
1. Continue with current implementation
2. Abort and start fresh
3. Override limit and continue iterations
```

**If "Abort":**
Clean up worktree and branch, mark task as pending.

</step>
