---
name: cat:execute-task
description: Execute task (continues incomplete work)
argument-hint: "[major.minor-task-name]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
  - Task
  - AskUserQuestion
  - SlashCommand
---

<objective>

Execute a task with worktree isolation, subagent orchestration, and quality gates.

This is CAT's core execution command. It:
1. Finds the next executable task (pending + dependencies met)
2. Creates a task worktree and branch
3. Executes the PLAN.md (spawn subagent or work directly)
4. Monitors token usage throughout
5. Runs approval gate (interactive mode)
6. Squashes commits by type
7. Merges task branch to main
8. Cleans up worktrees
9. Updates STATE.md
10. Offers next task

</objective>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/execute-task.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/workflows/subagent-protocol.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/commit-types.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/spawn-subagent/SKILL.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/skills/merge-subagent/SKILL.md

</execution_context>

<context>

Task path: $ARGUMENTS

**Load project state first:**
@.claude/cat/cat-config.json
@.claude/cat/PROJECT.md
@.claude/cat/ROADMAP.md

</context>

<process>

<step name="verify">

**MANDATORY FIRST STEP - Verify planning structure:**

```bash
[ ! -d .claude/cat ] && echo "ERROR: No .claude/cat/ directory. Run /cat:new-project first." && exit 1
[ ! -f .claude/cat/cat-config.json ] && echo "ERROR: No cat-config.json. Run /cat:new-project first." && exit 1
```

**Load configuration:**

Read `.claude/cat/cat-config.json` to determine:
- `yoloMode` - whether approval gates are skipped
- `contextLimit` - total context window size
- `targetContextUsage` - soft limit for task size

</step>

<step name="find_task">

**Identify task to execute:**

**If $ARGUMENTS provided:**
- Parse as `major.minor-task-name` format (e.g., `1.0-parse-tokens`)
- Validate task exists at `.claude/cat/v{major}/v{major}.{minor}/{task-name}/`
- Load its STATE.md and PLAN.md

**If $ARGUMENTS empty:**
- Scan all tasks to find first executable:
  1. Status is `pending` or `in-progress`
  2. All dependencies are `completed`
  3. Parent minor version's dependencies are met

```bash
# Find all task STATE.md files (depth 3 under major version = task level)
find .claude/cat/v*/v*.* -mindepth 2 -maxdepth 2 -name "STATE.md" 2>/dev/null
```

For each task, check:
- Parse STATUS from STATE.md
- Parse DEPENDENCIES from STATE.md
- Verify each dependency task has status: completed

**If no executable task found:**

```
No executable tasks found.

Possible reasons:
- All tasks completed
- Remaining tasks have unmet dependencies
- No tasks defined yet

Use /cat:status to see current state.
Use /cat:add-task to add new tasks.
```

Exit command.

</step>

<step name="load_task">

**Load task details:**

Read the task's:
- `STATE.md` - current status, progress, dependencies
- `PLAN.md` - execution plan with steps
- Parent minor's `STATE.md` - for context
- Parent major's `STATE.md` - for context

Present task overview:

```
## Task: {task-name}

**Version:** {major}.{minor}
**Status:** {status}
**Progress:** {progress}%

**Goal:**
{goal from PLAN.md}

**Approach:**
{approach from PLAN.md}
```

</step>

<step name="create_worktree">

**Create task worktree and branch:**

Branch naming: `{major}.{minor}-{task-name}`

```bash
# Ensure we're on main branch
MAIN_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")

# Create branch if it doesn't exist
TASK_BRANCH="{major}.{minor}-{task-name}"
git branch "$TASK_BRANCH" "$MAIN_BRANCH" 2>/dev/null || true

# Create worktree
WORKTREE_PATH="../.worktrees/$TASK_BRANCH"
git worktree add "$WORKTREE_PATH" "$TASK_BRANCH" 2>/dev/null || \
    echo "Worktree already exists at $WORKTREE_PATH"
```

**Update task STATE.md:**

Set status to `in-progress` and record start time.

</step>

<step name="execute">

**Execute the PLAN.md:**

**MANDATORY: Always spawn subagent for implementation.**

Main agent is the orchestrator. Subagents do the work. This is NOT optional.

| Task Size | Strategy |
|-----------|----------|
| Any task | Spawn subagent via `/cat:spawn-subagent` |
| Large/complex | Consider `/cat:decompose-task` first, then spawn |

**Why subagents are mandatory (not optimization):**
- Fresh context = peak quality (no accumulated noise)
- Token tracking enables proactive decomposition
- Branch isolation provides safe rollback
- Main agent context preserved for orchestration
- Prevents quality degradation from context pressure

**Subagent execution workflow:**

1. Invoke `/cat:spawn-subagent` skill with:
   - Task path
   - PLAN.md contents
   - Worktree path
   - Token tracking enabled

2. Monitor subagent via `/cat:monitor-subagents`:
   - Check for compaction events
   - Track token usage
   - Handle early failures

3. Collect results via `/cat:collect-results`:
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

<step name="token_check">

**Check token usage:**

If subagent reported compaction events:

Use AskUserQuestion:
- header: "Token Warning"
- question: "Task triggered context compaction. This may indicate it's too large. Consider:"
- options:
  - "Continue" - Proceed with current task
  - "Decompose" - Split into smaller tasks via /cat:decompose-task
  - "Abort" - Stop and review

</step>

<step name="approval_gate">

**Approval gate (Interactive mode only):**

Skip if `yoloMode: true` in config.

Present work summary:

```
## Task Complete: {task-name}

**Files Changed:**
- path/to/file1.ext (+10, -5)
- path/to/file2.ext (+25, -0)

**Commits:**
- feature: add feature X
- test: add tests for feature X
- docs: update README

**Review branch:** {task-branch}
```

Use AskUserQuestion:
- header: "Approval"
- question: "Review changes and approve merge to main?"
- options:
  - "Approve" - Merge to main
  - "Review first" - I'll check the changes
  - "Request changes" - Need modifications
  - "Abort" - Discard work

**If "Review first":**
Provide commands to review:
```bash
git log {main}..{task-branch} --oneline
git diff {main}...{task-branch}
```
Wait for user to respond with approval.

**If "Request changes":**
Receive feedback and loop back to execute step.

**If "Abort":**
Clean up worktree and branch, mark task as pending.

</step>

<step name="squash_commits">

**Squash commits by category:**

Group commits into two categories:

**Implementation commits** (squashed together):
- `feature:` - features
- `bugfix:` - bug fixes
- `test:` - tests
- `refactor:` - refactoring
- `docs:` - documentation

**Infrastructure commits** (squashed separately):
- `config:` - configuration and maintenance

Create one squashed commit per category:

```bash
# Example: squash all feat commits
git rebase -i --autosquash {base}
```

Use `/cat:git-squash` skill for safe squashing.

</step>

<step name="merge">

**Merge task branch to main:**

```bash
git checkout {main}
git merge --no-ff {task-branch} -m "$(cat <<'EOF'
Merge {major}.{minor}-{task-name}

{Summary from PLAN.md goal}
EOF
)"
```

Handle merge conflicts:
1. Identify conflicting files
2. Attempt automatic resolution
3. If unresolvable, present to user

</step>

<step name="cleanup">

**Clean up worktree:**

```bash
# Remove worktree
git worktree remove "$WORKTREE_PATH" --force

# Optionally delete branch if autoCleanupWorktrees is true
git branch -d "{task-branch}" 2>/dev/null || true
```

</step>

<step name="update_state">

**Update STATE.md files:**

1. **Task STATE.md:**
   - Set status: `completed`
   - Set progress: `100`
   - Record completion timestamp

2. **Minor STATE.md:**
   - Recalculate progress based on task completion
   - Update status if all tasks complete

3. **Major STATE.md:**
   - Recalculate progress based on minor completion
   - Update status if all minors complete

4. **Dependent tasks:**
   - Find all tasks in the same minor version that list this task in Dependencies
   - For each dependent task, check if ALL its dependencies are now completed
   - If all dependencies met, the task is now executable (no longer blocked)

</step>

<step name="commit_metadata">

**Commit STATE.md updates:**

```bash
git add .claude/cat/
git commit -m "$(cat <<'EOF'
docs: complete task {task-name}

Updates STATE.md for Major {major}, Minor {minor}.

Task ID: v{major}.{minor}-{task-name}
EOF
)"
```

</step>

<step name="next_task">

**Offer next task:**

Find next executable task (pending + dependencies met).

If found:

```
---

## Task Complete

**{task-name}** merged to main.

## Next Up

**{next-task-name}** - {goal from PLAN.md}

<sub>`/clear` first -> fresh context window</sub>

`/cat:execute-task {major}.{minor}/{next-task-name}`

---
```

If no more tasks:

```
---

## Task Complete

**{task-name}** merged to main.

## All Tasks Complete

Minor version {major}.{minor} is complete!

Use `/cat:status` to see overall progress.
Use `/cat:add-task` to add more tasks.
Use `/cat:add-minor-version` to add a new minor version.

---
```

</step>

</process>

<deviation_rules>

During execution, handle discoveries automatically:

1. **Auto-fix bugs** - Fix immediately, document in CHANGELOG
2. **Auto-add critical** - Security/correctness gaps, add and document
3. **Auto-fix blockers** - Can't proceed without fix, do it and document
4. **Ask about architectural** - Major structural changes, stop and ask user
5. **Log enhancements** - Nice-to-haves, propose as new task, continue

Only rule 4 requires user intervention.

</deviation_rules>

<commit_rules>

**Per-Step Commits:**

After each execution step:
1. Stage only files modified by that step
2. Commit with appropriate type prefix
3. Types: feature, bugfix, test, refactor, docs, config, performance

**NEVER use:**
- `git add .`
- `git add -A`
- `git add src/` or any broad directory

**Always stage files individually.**

**Enhanced Commit Message Format (replaces task CHANGELOG.md):**

The final squashed commit message MUST include changelog content. The commit diff
implies Files Modified, Files Created, and Test Coverage - do NOT duplicate these.

```
{type}: {concise description}

## Problem Solved
[WHY this task was needed - what wasn't working or was missing]
- {Problem 1}
- {Problem 2 if applicable}

## Solution Implemented
[HOW the problem was solved - the approach taken]
- {Key implementation detail 1}
- {Key implementation detail 2}

## Decisions Made (optional)
- {Decision}: {rationale}

## Known Limitations (optional)
- {Limitation}: {why accepted or deferred}

## Deviations from Plan (optional)
- {Deviation}: {reason and impact}

Task ID: v{major}.{minor}-{task-name}
```

**Example:**
```
feature: add lambda expression parsing

## Problem Solved
- Parser failed on multi-parameter lambdas: `(a, b) -> a + b`
- 318 parsing errors in Spring Framework codebase

## Solution Implemented
- Added lookahead in parsePostfix() to detect lambda arrow
- Reused existing parameter parsing for lambda parameters
- Handles both inferred and explicit type parameters

## Decisions Made
- Reuse parameter parsing: Maintains consistency with method parameters

Task ID: v1.0-parse-lambdas
```

</commit_rules>

<success_criteria>

- [ ] Task identified and loaded
- [ ] Worktree created with correct branch
- [ ] PLAN.md executed successfully
- [ ] Token usage monitored
- [ ] Approval gate passed (if interactive)
- [ ] Commits squashed by type
- [ ] Branch merged to main
- [ ] Worktree cleaned up
- [ ] STATE.md files updated
- [ ] Next task offered

</success_criteria>
