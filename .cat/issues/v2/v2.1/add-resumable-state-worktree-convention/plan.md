# Plan

## Goal

Update project conventions in `.claude/rules/common.md` to specify that resumable progress state
must be written to `.cat/work` inside the issue worktree, not to `/tmp`, because `/tmp` may be
cleared between sessions when resuming a conversation.

## Pre-conditions

(none)

## Post-conditions

- [ ] `.claude/rules/common.md` is updated with a convention specifying `.cat/work` inside the
  issue worktree as the target for resumable progress state
- [ ] The updated convention includes a rationale explaining that `/tmp` is unsuitable for
  resumable progress state because it may be cleared between sessions
- [ ] The existing Multi-Instance Safety section guidance permitting `/tmp` is reconciled to
  clearly scope it to ephemeral (non-resumable) data only, distinguishing it from resumable
  progress state
- [ ] No regressions introduced to existing conventions
- [ ] E2E verification: convention is correctly documented without conflicting with other
  guidance in `common.md`

## Research Findings

The `Multi-Instance Safety` section in `.claude/rules/common.md` currently contains a "Temporary
files/directories" subsection that permits `/tmp` without distinguishing between ephemeral and
resumable state:

```
✅ Use `mktemp -d` or `mktemp` for unique per-invocation isolation
✅ Store in `/tmp` or per-session directories (e.g., `${CLAUDE_CONFIG_DIR}/projects/.../${CLAUDE_SESSION_ID}/`)
```

This guidance is correct for ephemeral state (data used within a single session and discarded
afterward). However, `/tmp` is unsuitable for **resumable progress state** — data written during
one session that must survive session restart and be readable in a subsequent session. On many
systems (Linux with systemd, macOS), `/tmp` is cleared at boot or on a schedule. When a user
resumes a conversation in a new Claude Code session, the previous session's `/tmp` data is gone.

The correct location for resumable progress state is `.cat/work` inside the issue worktree. This
directory is:
- Part of the git worktree (persistent across session restarts)
- Session-isolated (each issue gets its own worktree)
- Not git-tracked by default (listed in `.gitignore` inside worktrees)

The fix requires two changes to the `Multi-Instance Safety` section:
1. Scope the existing "Temporary files/directories" bullets to explicitly say "ephemeral state only"
2. Add a new "Resumable progress state" subsection directing agents to write resumable data to
   `.cat/work` inside the worktree, with rationale explaining why `/tmp` is insufficient.

## Approach

Update `.claude/rules/common.md` in a single targeted edit:
- In the "Temporary files/directories" subsection, add a clarifying note that these patterns apply
  to ephemeral data only (data not needed across session restarts).
- Add a new "Resumable progress state" subsection immediately after "Temporary files/directories"
  in the Multi-Instance Safety "Safe Patterns" section, specifying `.cat/work` as the correct
  location, with the rationale and a code pattern showing correct vs. wrong usage.

**Rejected alternatives:**
- Writing the new convention in a separate file: rejected because the Multi-Instance Safety section
  is the natural home for this guidance (it already covers temp files), and splitting would require
  users to consult two places.
- Adding a top-level section outside Multi-Instance Safety: rejected because this is a specific
  instance of safe-patterns guidance, not a new cross-cutting concern.

## Jobs

### Job 1

- Edit `.claude/rules/common.md` to add a "Resumable progress state" subsection to the
  Multi-Instance Safety → Safe Patterns section, and clarify the existing "Temporary
  files/directories" bullets as ephemeral-only. See exact edit specification below.
- Update `.cat/issues/v2/v2.1/add-resumable-state-worktree-convention/index.json` in the same commit: set `"status"` to `"closed"` and `"progress"` to `"100%"`.

**Exact edit specification for `.claude/rules/common.md`:**

Locate the "Temporary files/directories" subsection under "Multi-Instance Safety → Safe Patterns".
It currently reads:

```markdown
**Temporary files/directories:**
- ✅ Use `mktemp -d` or `mktemp` for unique per-invocation isolation
- ✅ Store in `/tmp` or per-session directories (e.g., `${CLAUDE_CONFIG_DIR}/projects/.../${CLAUDE_SESSION_ID}/`)
- ❌ Use hardcoded paths like `../repo-clean.git` or `/tmp/shared-work/`
- ❌ Use paths like `${WORKTREE_PARENT}/shared-dir/` (multiple instances share the parent)
```

Replace it with:

```markdown
**Temporary files/directories (ephemeral state — not needed across session restarts):**
- ✅ Use `mktemp -d` or `mktemp` for unique per-invocation isolation
- ✅ Store in `/tmp` or per-session directories (e.g., `${CLAUDE_CONFIG_DIR}/projects/.../${CLAUDE_SESSION_ID}/`)
- ❌ Use hardcoded paths like `../repo-clean.git` or `/tmp/shared-work/`
- ❌ Use paths like `${WORKTREE_PARENT}/shared-dir/` (multiple instances share the parent)

**Resumable progress state (data that must survive session restarts):**
- ✅ Write to `.cat/work` inside the issue worktree (e.g., `${WORKTREE_PATH}/.cat/work/progress.json`)
- ❌ Write to `/tmp` — `/tmp` may be cleared between sessions on Linux (systemd-tmpfiles) and macOS,
  causing resumable state to be lost when the user restarts a conversation

```bash
# WRONG: progress state lost if session is restarted
echo '{"step": 3}' > /tmp/cat-progress-${ISSUE_ID}.json

# CORRECT: progress state persists across session restarts
echo '{"step": 3}' > "${WORKTREE_PATH}/.cat/work/progress.json"
```
```

The commit type for this change is `config:` (updating `.claude/` rules).
