---
user-invocable: false
---

# Work Phase: Merge

Steps for post-approval work: squash_commits, finalization, next_task.

---

<step name="squash_commits">

**Apply Squash Preference from PROJECT.md:**

```bash
SQUASH_POLICY=$(grep -A10 "### Squash Policy" .claude/cat/PROJECT.md 2>/dev/null | grep "Strategy:" | sed 's/.*Strategy:\s*//' | head -1)

if [[ -z "$SQUASH_POLICY" ]]; then
  SQUASH_POLICY=$(jq -r '.gitWorkflow.squashPolicy // "by-type"' .claude/cat/cat-config.json 2>/dev/null)
fi

echo "Squash policy: $SQUASH_POLICY"

case "$SQUASH_POLICY" in
  "by-type"|"by type"|"Squash by type"|"Group commits by type prefix")
    echo "Applying squash-by-type policy..."
    ;;
  "single"|"Single commit"|"Squash all commits into one")
    echo "Applying single-commit policy..."
    SQUASH_ALL=true
    ;;
  "keep-all"|"keep all"|"Keep all commits")
    echo "Skipping squash per PROJECT.md policy"
    SKIP_SQUASH=true
    ;;
  *)
    echo "Using default squash-by-type policy"
    ;;
esac
```

**If SKIP_SQUASH is true:** Skip to next step (finalization).

**If SQUASH_ALL is true:**
```bash
COMMIT_COUNT=$(git rev-list --count "${BASE_BRANCH}..HEAD")
if [[ "$COMMIT_COUNT" -gt 1 ]]; then
  FIRST_MSG=$(git log --format="%s" "${BASE_BRANCH}..HEAD" | tail -1)
  git reset --soft "$BASE_BRANCH"
  git commit -m "$FIRST_MSG"
fi
```

**Otherwise (default by-type squashing):**

Group commits into two categories:

**Implementation commits** (squashed together):
- `feature:` - features
- `bugfix:` - bug fixes
- `test:` - tests
- `refactor:` - refactoring
- `docs:` - documentation

**Infrastructure commits** (squashed separately):
- `config:` - configuration and maintenance

Use `/cat:git-squash` skill for safe squashing.

</step>

<step name="finalization">

**Spawn Finalization subagent to complete post-approval work.**

This step batches merge, cleanup, state updates, and changelog updates into a single subagent,
hiding tool calls from the user.

**Spawn Finalization subagent:**

```
Task tool invocation:
  description: "Finalize task {task-name}"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Complete post-approval finalization for task.

    CONTEXT:
    - Task: {task-name}
    - Task branch: {task-branch}
    - Worktree path: {worktree-path}
    - Base branch: {base-branch}
    - Completion workflow: {completionWorkflow from config}
    - Merge style: {mergeStyle from config}
    - Task ID: {TASK_ID}
    - Session ID: ${CLAUDE_SESSION_ID}

    STEP 1: MERGE
    Return to main workspace and merge task branch.

    cd /workspace
    CURRENT_BRANCH=$(git branch --show-current)

    If completionWorkflow is "merge":
      If mergeStyle is "fast-forward":
        git merge --ff-only {task-branch}
      If mergeStyle is "merge-commit":
        git merge --no-ff {task-branch}
      If mergeStyle is "squash":
        git merge --squash {task-branch} && git commit -m "{commit-message}"

      If merge fails: Return {"status": "MERGE_CONFLICT", "error": "..."}

    If completionWorkflow is "pr":
      git push -u origin {task-branch}
      If GitHub: gh pr create --base {base} --head {task-branch} --title "..." --body "..."
      Return {"status": "PR_CREATED", "url": "..."}

    STEP 2: CLEANUP
    Remove worktree: git worktree remove "{worktree-path}" --force

    If completionWorkflow is "merge":
      Delete branch: git branch -d "{task-branch}"

    Release lock:
      "${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "{TASK_ID}" "${CLAUDE_SESSION_ID}"

    STEP 3: UPDATE PARENT STATE
    Task STATE.md already committed with implementation.
    Update parent STATE.md files (minor/major progress rollup).

    git add .claude/cat/issues/v*/STATE.md .claude/cat/issues/v*/v*.*/STATE.md
    git commit -m "config: update progress for v{major}.{minor}"

    STEP 4: UPDATE CHANGELOGS
    Update minor CHANGELOG.md: Add task to Tasks Completed table.
    Update major CHANGELOG.md if minor version completes.

    RETURN FORMAT:
    {
      "status": "SUCCESS" | "MERGE_CONFLICT" | "PR_CREATED" | "FAILED",
      "merged": true/false,
      "branch": "{base-branch}",
      "pr_url": "..." (if PR workflow),
      "error": "..." (if failed)
    }

    FAIL-FAST:
    - If merge fails with conflict, return MERGE_CONFLICT immediately
    - If any git operation fails unexpectedly, return FAILED with error
    - Always release lock before returning, even on failure
```

**Handle subagent result:**

**If result.status is "SUCCESS":**
Proceed to next_task step.

**If result.status is "PR_CREATED":**
Display PR URL: `PR created: {result.pr_url}`
Proceed to next_task step.

**If result.status is "MERGE_CONFLICT":**
Use AskUserQuestion: "Force merge commit" | "Rebase first" | "Abort"
- If "Force merge commit": Re-run finalization with merge-commit style
- If "Rebase first": Instruct user to rebase manually, then retry
- If "Abort": Release lock, exit workflow

**If result.status is "FAILED":**
Display error: `Finalization failed: {result.error}`
Use AskUserQuestion: "Retry" | "Abort"
- If "Retry": Re-spawn finalization subagent
- If "Abort": Release lock (if not already released), exit workflow

</step>

<step name="next_task">

**MANDATORY: Provide next steps to user (M120).**

After cleanup, ALWAYS show user their available options.

**Version Boundary Detection:**

```bash
COMPLETED_MAJOR="{major from completed task}"
COMPLETED_MINOR="{minor from completed task}"

# After get-available-issues.sh returns next task
NEXT_MAJOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.major')
NEXT_MINOR=$(echo "$NEXT_TASK_RESULT" | jq -r '.minor // empty')
```

| Scheme | Boundary Crossed When |
|--------|----------------------|
| MAJOR only | `COMPLETED_MAJOR != NEXT_MAJOR` |
| MAJOR.MINOR | `COMPLETED_MAJOR != NEXT_MAJOR OR COMPLETED_MINOR != NEXT_MINOR` |

**If BOUNDARY_CROSSED is true:**
1. Use **VERSION_BOUNDARY_GATE** box from OUTPUT TEMPLATE WORK BOXES
2. Use AskUserQuestion:
   - header: "Version Boundary"
   - question: "All tasks in v{completed-version} complete. Continue to v{next-version}?"
   - options:
     - "Continue to next version"
     - "Exit to publish first"
     - "Stop"

---

**Auto-continue behavior conditionals:**

**MANDATORY: Try to acquire lock before offering next task.**

```bash
NEXT_TASK_ID="${MAJOR}.${MINOR}-${NEXT_TASK_NAME}"

LOCK_RESULT=$("${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" acquire "${CLAUDE_PROJECT_DIR}" "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}")

if echo "$LOCK_RESULT" | jq -e '.status == "locked"' > /dev/null 2>&1; then
  continue  # This task is locked, try the next candidate
fi
```

**Auto-continue behavior (trust >= medium):**

```bash
TRUST_LEVEL=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

| Trust Level | Behavior |
|-------------|----------|
| `high` | Auto-continue to next task immediately (no prompt) |
| `medium` | Auto-continue to next task immediately (no prompt) |
| `low` | Show next task, wait for user to invoke `/cat:work` |

**Scope-aware task selection:**

| WORK_SCOPE | Next Task Selection |
|------------|---------------------|
| `task` | **STOP** - single task complete, don't continue |
| `minor` | Find next task in same minor version only |
| `major` | Find next task in same minor, or first task in next minor within same major |
| `all` | Find next task anywhere (current minor → next minor → next major) |

**If trust >= medium and next task found (within scope):**

Use the **TASK_COMPLETE_WITH_NEXT_TASK** box from OUTPUT TEMPLATE WORK BOXES.
Replace placeholders with actual values.

**Brief pause for user intervention:**

After displaying the message, pause briefly to allow user to type:
- **"stop"** or **"pause"** → Complete current display, do NOT start next task
- **"abort"** or **"cancel"** → Stop immediately, release locks, clean up

If no input received, **loop back to find_task step** with the next task.

**If scope complete (no more tasks within scope):**

Use the **SCOPE_COMPLETE** box from OUTPUT TEMPLATE WORK BOXES.

**If trust == low and next task found:**

Release the lock (user will re-acquire when they invoke the command):

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "$NEXT_TASK_ID" "${CLAUDE_SESSION_ID}"
```

Use the **TASK_COMPLETE_LOW_TRUST** box from OUTPUT TEMPLATE WORK BOXES.

**If no more tasks in the current minor version:**

**→ Load version-completion.md workflow for full handling.**

See `concepts/version-completion.md` for:
- Minor version completion check and celebration
- Stakeholder review prompt
- Major version completion check
- Next steps guidance

---

## Task Complete

**{task-name}** merged to main.

## All Tasks Complete

Minor version {major}.{minor} is complete!

Use `/cat:status` to see overall progress.
Use `/cat:add` to add more tasks or versions.

---

</step>
