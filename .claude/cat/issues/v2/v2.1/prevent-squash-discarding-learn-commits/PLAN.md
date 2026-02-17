# Plan: prevent-squash-discarding-learn-commits

## Problem
During `/cat:work` Step 6 (squash), the main agent reverts commits made by `/cat:learn` subagents because they modify
files not listed in the current issue's PLAN.md "Files to Modify" section. This silently discards legitimate prevention
fixes (code changes to skills/hooks) that were recorded as learnings during the work session.

## Satisfies
None - infrastructure/workflow issue

## Reproduction Steps
1. Run `/cat:work` on any issue
2. During review phase, a stakeholder review identifies concerns
3. User invokes `/cat:learn` to record the mistake with a prevention fix
4. The learn subagent commits prevention changes to skill files in the worktree (e.g., `plugin/skills/stakeholder-review/first-use.md`)
5. Main agent reaches Step 6 (squash) and sees files modified by learn subagent not in PLAN.md
6. Agent judges these as "out of scope" and reverts them with `git checkout base_branch -- <file>`
7. Prevention fixes are lost — only retrospective metadata (JSON on v2.1) survives

## Expected vs Actual
- **Expected:** Learn prevention fixes survive the squash step and are merged to the base branch
- **Actual:** Prevention fixes are silently reverted during squash, requiring manual recovery from git reflog

## Root Cause
The work-with-issue skill's Step 6 (squash) has no awareness of learn-originated commits. It treats any file not in
PLAN.md's "Files to Modify" as out-of-scope. The learn skill commits to the current worktree (correct behavior — it
needs worktree isolation), but the squash step doesn't distinguish "learn prevention fixes" from "accidental
modifications."

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Squash behavior change could affect non-learn commits if marker detection is too broad
- **Mitigation:** Use explicit commit trailer that only `/cat:learn` produces

## Approaches

### A: Commit Trailer Marker
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Have `/cat:learn` add a `Learn-Prevention: MXXX` trailer to its commits. The squash step reads
  trailers and preserves learn commits as a separate squashed commit alongside the main implementation commit.

### B: Learn Commits Directly to Base Branch
- **Risk:** MEDIUM
- **Scope:** 1 file (minimal)
- **Description:** Change `/cat:learn` to commit prevention fixes directly to the base branch instead of the worktree.
  Simpler but loses worktree isolation (learn fixes could conflict with concurrent work).

### C: Pre-squash Interactive Check
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Before squashing, identify commits not in PLAN.md and ask user whether to include, cherry-pick
  separately, or discard. Most flexible but adds user interaction.

### D: Marker File in Worktree
- **Risk:** LOW
- **Scope:** 3 files (moderate)
- **Description:** Have `/cat:learn` write `.claude/cat/worktrees/<name>/learn-commits.json` listing commit hashes.
  Squash step reads this file and preserves those commits.

## Files to Modify
- `plugin/skills/work-with-issue/first-use.md` - Update Step 6 squash logic to detect and preserve learn commits
- `plugin/skills/learn/first-use.md` - Add commit trailer or marker file when committing prevention fixes

## Acceptance Criteria
- [ ] Learn prevention fixes survive the squash step in `/cat:work`
- [ ] Learn commits are preserved as a separate commit (not mixed into the implementation squash)
- [ ] Non-learn out-of-scope files are still correctly excluded from squash
- [ ] No regressions in normal squash behavior when no learn commits exist

## Execution Steps
1. **Step 1:** Determine selected approach (from user choice or config alignment)
   - Files: N/A
2. **Step 2:** Implement the marker mechanism in `/cat:learn` skill
   - Files: `plugin/skills/learn/first-use.md`
3. **Step 3:** Update Step 6 (squash) in work-with-issue to detect and preserve learn commits
   - Files: `plugin/skills/work-with-issue/first-use.md`
4. **Step 4:** Test by simulating the scenario: create worktree, add learn commit, verify squash preserves it

## Success Criteria
- [ ] Simulated learn commit survives squash in worktree
- [ ] Normal squash (no learn commits) behaves identically to current behavior