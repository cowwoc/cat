# Plan: fix-status-in-progress-detection

## Problem
`/cat:status` shows in-progress issues as pending (🔳) instead of in-progress (🔄). Root cause: `work-prepare.py`
updates STATE.md in the worktree copy, but `GetStatusOutput.java` reads STATE.md from the main workspace where status
remains `open`.

## Satisfies
None (bug fix for existing functionality)

## Approach
Detect in-progress status by checking for the existence of a git branch matching the issue name pattern
`{version}-{issue-name}`. Branches are preferable to lock files because they span across machines, while lock files are
machine-specific.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Branch detection adds a git operation per issue during status display
- **Mitigation:** Use `git branch --list` which is fast for local branch checks; only check open issues

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - Add branch existence check to override
  `open` status to `in-progress` when a matching branch exists

## Acceptance Criteria
- [ ] Issues with an active branch (e.g., `2.1-optimize-verify-subagent-count`) show as 🔄 in `/cat:status`
- [ ] Issues without a branch still show as 🔳 (pending/open)
- [ ] Closed issues with leftover branches are not incorrectly shown as in-progress (closed status takes precedence)
- [ ] Branch detection uses local git operations only (no network calls)

## Execution Steps
1. **Modify GetStatusOutput.java:** Update `getTaskStatus()` to accept the issue name and version context. After reading
   STATE.md status, if status is `open`, check if a git branch `{version}-{issue-name}` exists. If it does, return
   `in-progress` instead.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
2. **Add branch detection helper:** Create a method that checks local branch existence using JGit or `git branch --list`.
   Cache the branch list once per status invocation to avoid repeated git calls.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
3. **Run tests:** Verify existing tests pass and add test for branch-based status detection.
   - Files: `hooks/src/test/java/...`

## Success Criteria
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] Manual verification: issue with active branch shows 🔄 in status output
