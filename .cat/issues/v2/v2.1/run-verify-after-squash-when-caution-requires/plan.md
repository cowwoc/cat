# Plan: run-verify-after-squash-when-caution-requires

## Current State

The `cat:work-merge-agent` skill performs the following sequence:
1. Squash commits by topic (Step 8)
2. Rebase onto target branch (Step 9)
3. Post-merge impact analysis (Step 9c)
4. Instruction-builder review (Step 10)
5. Squash before approval gate (Step 11)
6. Approval gate presentation (Step 12)

After Step 8 (squash by topic), the code is committed but not verified to build. If the squash introduces issues or if
combined commits have undetected build problems, the approval gate presents potentially-broken code to the user. When
`caution` setting is "medium" or "high", build verification should run after squash but before the approval gate to
catch and block broken code.

## Target State

After squashing by topic (Step 8), when the caution setting is "medium" or "high", automatically run `mvn -f client/pom.xml verify` to verify:
- All 2759 unit tests pass (TestNG suite)
- Build succeeds with no errors
- No regressions introduced by squash

If verify fails, block progression to the approval gate and report the failure to the user with guidance to fix the
code before re-squashing. This prevents presenting broken code for user approval.

## Parent Requirements

None — workflow improvement to respect caution settings and ensure build correctness before approval.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — adds verification only when caution is set to medium/high (default is medium)
- **Compatibility:** Works with existing squash and rebase logic
- **Performance Impact:** Adds ~30-60 seconds for full Maven verify run (acceptable pre-approval gate checkpoint)

## Alternatives Considered

- **Verify only when caution == "high"**: Too restrictive; medium caution should also verify
- **Move verify to after approval gate**: Defeats purpose; broken code would be approved
- **Manual verify step for user**: Requires user action; better to automate and block if broken
- **Skip verify for small diffs**: Fragile heuristic; all diffs should be verified

## Files to Modify

- `plugin/skills/work-merge-agent/first-use.md` — Step 8 post-condition: add Maven verify call when caution is "medium" or "high", block progression if verify fails

## Pre-conditions

- [ ] `cat:work-merge-agent` skill exists and is invoked by `cat:work-with-issue-agent`
- [ ] `caution` configuration option is read from `.cat/config.json`
- [ ] Maven build in worktree produces passing test suite

## Sub-Agent Waves

### Wave 1

- Modify `plugin/skills/work-merge-agent/first-use.md` Step 8 post-condition:
  - After successful squash commit, read `caution` setting from `.cat/config.json` using `get-config-output effective`
  - If `caution` is "medium" or "high":
    - Run: `cd "${WORKTREE_PATH}" && mvn -f client/pom.xml verify`
    - If verify exits with non-zero status, block with error message identifying the failure (test failures, build error, etc.)
    - Guidance: "Build verification failed after squash. Fix the code in the worktree and re-run squash before retry."
  - If `caution` is "low", skip verify (trust implementation without verification)
  - Document this as "Step 8.5: Conditional Build Verification"

## Post-conditions

- [ ] After Step 8 squash, if caution is "medium" or "high", mvn verify runs automatically
- [ ] If mvn verify fails, merge-agent blocks and reports failure with actionable guidance
- [ ] If caution is "low", verify is skipped (no breaking changes to existing trust=high behavior)
- [ ] No changes to other merge phase steps (rebase, instruction-builder review, approval gate timing)
