# Plan: fix-status-in-progress-detection

## Problem
`/cat:status` shows in-progress issues as pending (🔳) instead of in-progress (🔄). Root cause: `work-prepare.py`
updates STATE.md in the worktree copy, but `GetStatusOutput.java` reads STATE.md from the main workspace where status
remains `open`.

## Satisfies
None (bug fix for existing functionality)

## Approach
Detect in-progress status using a tiered strategy:

- **Indie tier:** Check for lock files in `.claude/cat/locks/`. Lock files are local to the machine, which matches the
  Indie tier's single-developer use case.
- **Team tier:** Also check for git branches matching `{version}-{issue-name}`. Branches span across machines, enabling
  team members to see each other's in-progress work.

Both checks only apply when STATE.md status is `open` — closed/blocked statuses take precedence.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Branch detection adds a git operation during status display; tier detection adds a dependency on
  licensing/entitlements
- **Mitigation:** Cache branch list once per invocation; lock file check is a simple file existence test

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - Add lock file and branch existence
  checks to override `open` status to `in-progress`

## Acceptance Criteria
- [ ] Indie tier: issues with an active lock file show as 🔄 in `/cat:status`
- [ ] Team tier: issues with an active branch (e.g., `2.1-optimize-verify-subagent-count`) also show as 🔄
- [ ] Issues without a lock file or branch still show as 🔳 (pending/open)
- [ ] Closed issues with leftover lock files or branches are not incorrectly shown as in-progress (closed status takes
  precedence)
- [ ] Branch detection uses local git operations only (no network calls)

## Execution Steps
1. **Add lock file detection:** In `getTaskStatus()`, after reading STATE.md, if status is `open`, check if a lock file
   exists at `.claude/cat/locks/{version}-{issue-name}.lock`. If it does, return `in-progress`.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
2. **Add branch detection for Team tier:** If the current license tier is Team or higher, also check if a git branch
   `{version}-{issue-name}` exists. Cache the branch list once per status invocation.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
3. **Update method signature:** `getTaskStatus()` needs the issue name and version context to construct lock file and
   branch names.
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
4. **Run tests:** Verify existing tests pass and add tests for lock-file and branch-based status detection.
   - Files: `hooks/src/test/java/...`

## Success Criteria
- [ ] `mvn -f hooks/pom.xml test` passes
- [ ] Manual verification: issue with active lock file shows 🔄 in status output
