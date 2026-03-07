# Plan: fix-issue-path-worktree-resolution

## Problem

When a worktree is active, work phase agents (implement, confirm, review, merge) construct the PLAN.md path as
`"${ISSUE_PATH}/PLAN.md"` where `ISSUE_PATH` is the main workspace path
`/workspace/.claude/cat/issues/v2/v2.1/issue-name`. The `EnforceWorktreePathIsolation` hook blocks `Read` tool calls
to paths outside the active worktree, so agents fail to read PLAN.md or STATE.md.

## Parent Requirements

None

## Reproduction Code

```
# 1. Run /cat:work → work-prepare returns issue_path=/workspace/.claude/cat/issues/v2/v2.1/issue-name
# 2. work-implement-agent sets: PLAN_MD="${ISSUE_PATH}/PLAN.md"
# 3. Agent invokes: Read("/workspace/.claude/cat/issues/v2/v2.1/issue-name/PLAN.md")
# 4. EnforceWorktreePathIsolation hook blocks: "ERROR: Worktree isolation violation"
```

## Expected vs Actual

- **Expected:** Agents read PLAN.md from `${WORKTREE_PATH}/.claude/cat/issues/v2/v2.1/issue-name/PLAN.md`
  (within worktree)
- **Actual:** Agents attempt to read from main workspace path; blocked by worktree isolation hook

## Root Cause

`WorkPrepare.java` returns `issue_path` as an absolute main-workspace path. Work phase skills use this directly
for PLAN.md/STATE.md reads instead of deriving the equivalent path within the active worktree. The worktree
contains the same planning files (tracked by git), but agents never compute nor receive that worktree-relative path.

The Java code already computes the worktree-scoped path internally (in `updateStateMd()`, lines 1373–1374) but
does not include it in the JSON output.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** All changes are path substitutions; content is identical in both locations. Java handlers
  (e.g., `VerifyAudit.java`) read via Java FileI/O (not the `Read` tool) and are unaffected.
- **Mitigation:** Add a regression test for the new `worktree_issue_path` field; verify all tests pass.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — emit `worktree_issue_path` in JSON
  output
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add regression test for
  `worktree_issue_path` field (create file if it doesn't exist; append test method otherwise)
- `plugin/skills/work/first-use.md` — store `worktree_issue_path` from prepare output; pass it to
  `work-with-issue-agent`; use it for STATE.md/PLAN.md reads in decomposed parent check
- `plugin/skills/work-with-issue-agent/first-use.md` — accept `worktree_issue_path` as argument; forward to each
  phase skill
- `plugin/skills/work-implement-agent/first-use.md` — accept `worktree_issue_path`; use `WORKTREE_ISSUE_PATH` for
  PLAN.md reads and in subagent prompts
- `plugin/skills/work-confirm-agent/first-use.md` — accept `worktree_issue_path`; use `WORKTREE_ISSUE_PATH` for
  PLAN.md path
- `plugin/skills/work-review-agent/first-use.md` — accept `worktree_issue_path`; use `WORKTREE_ISSUE_PATH` for
  PLAN.md path
- `plugin/skills/work-merge-agent/first-use.md` — accept `worktree_issue_path`; use `WORKTREE_ISSUE_PATH` for
  PLAN.md path and ISSUE_GOAL extraction

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `WorkPrepare.java`, after `result.put("issue_path", issuePath.toString())` (line ~496), add:
  ```java
  Path relativeIssuePath = projectDir.relativize(issuePath);
  result.put("worktree_issue_path", worktreePath.resolve(relativeIssuePath).toString());
  ```
  `projectDir` is already in scope at that location (passed to `executeWithLockedIssue`).
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- Add a regression test to verify `worktree_issue_path` appears in JSON output with the correct value
  (`worktree_path + "/" + relative_issue_path`). Look for an existing `WorkPrepareTest.java`; if absent,
  create it in `client/src/test/java/io/github/cowwoc/cat/hooks/test/`. Model the test on
  `EnforceWorktreePathIsolationTest.java` for test structure. The test must use a temporary git repo and
  mock worktree/issue paths — never the real workspace repo.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`

- Run tests: `mvn -f client/pom.xml test`
  - Files: _(build validation only)_

- Commit: `bugfix: emit worktree_issue_path from WorkPrepare`

### Wave 2

- In `plugin/skills/work/first-use.md`:
  1. In the "Store phase 1 results" block, add `worktree_issue_path` to the stored variable list
  2. Change the `work-with-issue-agent` invocation args (line ~226) from:
     `"${CLAUDE_SESSION_ID} ${issue_id} ${issue_path} ${worktree_path} ${issue_branch} ..."`
     to:
     `"${CLAUDE_SESSION_ID} ${issue_id} ${issue_path} ${worktree_path} ${worktree_issue_path} ${issue_branch} ..."`
  3. In the decomposed-parent check (line ~186), change:
     `ISSUE_STATE="${issue_path}/STATE.md"` → `ISSUE_STATE="${worktree_issue_path}/STATE.md"`
  4. In the decomposed-parent check (line ~192), change:
     `cat "${issue_path}/PLAN.md"` → `cat "${worktree_issue_path}/PLAN.md"`
  - Files: `plugin/skills/work/first-use.md`

- In `plugin/skills/work-with-issue-agent/first-use.md`:
  1. Update argument signature line (line ~36): insert `<worktree_issue_path>` after `<worktree_path>`:
     `<issue_id> <issue_path> <worktree_path> <worktree_issue_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>`
  2. Update the `read` parse line (line ~39): insert `WORKTREE_ISSUE_PATH` after `WORKTREE_PATH`:
     `read ISSUE_ID ISSUE_PATH WORKTREE_PATH WORKTREE_ISSUE_PATH BRANCH TARGET_BRANCH ESTIMATED_TOKENS TRUST VERIFY <<< "$ARGUMENTS"`
  3. Update each phase skill invocation (lines ~49, ~69, ~86, ~104): insert `${WORKTREE_ISSUE_PATH}` after
     `${WORKTREE_PATH}` in all four phase skill args strings
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

- In `plugin/skills/work-implement-agent/first-use.md`:
  1. Update argument signature: insert `<worktree_issue_path>` after `<worktree_path>`
  2. Update the `read` parse line: insert `WORKTREE_ISSUE_PATH` after `WORKTREE_PATH`
  3. Update the arguments table row for position 4+: renumber and add `worktree_issue_path` row
  4. Change line ~54: `PLAN_MD="${ISSUE_PATH}/PLAN.md"` → `PLAN_MD="${WORKTREE_ISSUE_PATH}/PLAN.md"`
  5. Change line ~125 (second `PLAN_MD` assignment in the step header): same substitution
  6. Change plan-builder-agent invocation (line ~179): `${ISSUE_PATH}` → `${WORKTREE_ISSUE_PATH}`
  7. Change subagent prompt blocks (lines ~230 and ~330) that contain `ISSUE_PATH: ${ISSUE_PATH}`:
     change to `ISSUE_PATH: ${WORKTREE_ISSUE_PATH}` so subagents also read PLAN.md from the worktree
  - Files: `plugin/skills/work-implement-agent/first-use.md`

- In `plugin/skills/work-confirm-agent/first-use.md`:
  1. Update argument signature: insert `<worktree_issue_path>` after `<worktree_path>`
  2. Update the `read` parse line: insert `WORKTREE_ISSUE_PATH` after `WORKTREE_PATH`
  3. Change line ~46: `PLAN_MD="${ISSUE_PATH}/PLAN.md"` → `PLAN_MD="${WORKTREE_ISSUE_PATH}/PLAN.md"`
  4. In the confirm subagent prompt that reads PLAN.md or passes `ISSUE_PATH` (line ~202 and ~214): change
     `${ISSUE_PATH}/PLAN.md` and `"plan_md_path": "${ISSUE_PATH}/PLAN.md"` to use `${WORKTREE_ISSUE_PATH}`
  - Files: `plugin/skills/work-confirm-agent/first-use.md`

- In `plugin/skills/work-review-agent/first-use.md`:
  1. Update argument signature: insert `<worktree_issue_path>` after `<worktree_path>`
  2. Update the `read` parse line: insert `WORKTREE_ISSUE_PATH` after `WORKTREE_PATH`
  3. Change line ~46: `PLAN_MD="${ISSUE_PATH}/PLAN.md"` → `PLAN_MD="${WORKTREE_ISSUE_PATH}/PLAN.md"`
  - Files: `plugin/skills/work-review-agent/first-use.md`

- In `plugin/skills/work-merge-agent/first-use.md`:
  1. Update argument signature: insert `<worktree_issue_path>` after `<worktree_path>`
  2. Update the `read` parse line: insert `WORKTREE_ISSUE_PATH` after `WORKTREE_PATH`
  3. Change line ~26: `PLAN_MD="${ISSUE_PATH}/PLAN.md"` → `PLAN_MD="${WORKTREE_ISSUE_PATH}/PLAN.md"`
  4. Change ISSUE_GOAL extraction (line ~265):
     `ISSUE_GOAL=$(grep -A1 "^## Goal" "${ISSUE_PATH}/PLAN.md" | tail -n1)` →
     `ISSUE_GOAL=$(grep -A1 "^## Goal" "${WORKTREE_ISSUE_PATH}/PLAN.md" | tail -n1)`
  - Files: `plugin/skills/work-merge-agent/first-use.md`

- Commit: `bugfix: use worktree_issue_path for PLAN.md reads in all work phase skills`

## Post-conditions

- [ ] `WorkPrepare.java` emits `worktree_issue_path` in JSON output alongside `issue_path`
- [ ] All work phase agents (implement, confirm, review, merge) use `WORKTREE_ISSUE_PATH` for reading PLAN.md
- [ ] Regression test in `WorkPrepareTest.java` verifies `worktree_issue_path` field value
- [ ] All existing tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Run `/cat:work` on any open issue; implementation subagent reads PLAN.md without isolation errors
