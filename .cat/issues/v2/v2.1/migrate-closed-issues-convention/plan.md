# Plan

## Goal

Update the conventions to specify that even closed issues should be migrated when updating
the format of planning files. Update all migration scripts (1.0.8.sh, 1.0.9.sh, 1.0.10.sh,
2.0.sh, 2.1.sh) to process closed issues. Update all relevant convention files to reflect
the new policy.

## Research Findings

Investigation of the current codebase reveals:

1. **CLAUDE.md** contains the rule: "Do not update closed issue files: Never modify PLAN.md or STATE.md of closed
   issues unless the user explicitly instructs you to. Closed issues are historical records." This rule applies to
   manual agent edits but does not exempt migration scripts. It needs to be updated to clarify that migration scripts
   are an exception.

2. **2.1.sh** has two phases that explicitly skip closed issues:
   - Phase 13 (lines 1095-1100): Skips closed issues when removing deprecated Last Updated, Completed, and Closed
     fields from STATE.md files
   - Phase 14 (lines 1156-1165): Skips closed issues when renaming ## Satisfies → ## Parent Requirements in PLAN.md
   - All other phases (1-12, 15-21) already process all issues including closed ones

3. **1.0.8.sh, 1.0.9.sh, 1.0.10.sh, 2.0.sh**: None of these scripts filter by issue status. They already process
   all issues (open and closed).

4. **`.claude/rules/common.md`** § "No Backwards Compatibility" mentions migration scripts must be idempotent but
   does not address whether closed issues should be migrated.

5. **Multiple closed issue plan.md files** reference "closed issues are historical records" as rationale for skipping
   them during migration. These are historical records themselves and should not be modified.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Migration scripts touching closed issue files could theoretically corrupt historical data
- **Mitigation:** Changes are idempotent (sed replacements are no-ops if already migrated); convention update
  explicitly scopes the exception to automated migration scripts only

## Files to Modify
- `CLAUDE.md` - Update "Do not update closed issue files" rule to exempt migration scripts
- `.claude/rules/common.md` - Add guidance that migration scripts must process all issues including closed ones
- `plugin/migrations/2.1.sh` - Remove closed-issue skipping from phases 13 and 14

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged (existing open-issue migration continues to work correctly)
- [ ] All migration scripts updated to process both open and closed issues
- [ ] Migration scripts remain idempotent (running twice produces no additional changes)
- [ ] CLAUDE.md and all relevant convention files updated to reflect the new policy
- [ ] Tests passing
- [ ] E2E: Run an updated migration script on a repo with closed issues and verify they are migrated

## Jobs

### Job 1
- Update `CLAUDE.md` § "Do not update closed issue files" to add an exception for automated migration scripts.
  The updated text should clarify that the rule applies to manual agent edits during normal workflow, but
  automated migration scripts (under `plugin/migrations/`) must process all issues including closed ones to
  ensure consistent file formats across the entire issue tree.
  - Files: `CLAUDE.md`
- Update `.claude/rules/common.md` § "No Backwards Compatibility" to add a paragraph after the "Idempotency"
  paragraph stating that migration scripts must process all issues regardless of status (open or closed).
  Closed issues contain the same file formats as open issues and must be migrated to maintain consistency.
  The existing CLAUDE.md rule about not modifying closed issues applies only to manual agent edits, not
  automated migrations.
  - Files: `.claude/rules/common.md`
- Update `plugin/migrations/2.1.sh` Phase 13 (around lines 1090-1126):
  1. Remove the `phase13_skipped=0` variable initialization (line 1090)
  2. Remove the 6-line "Skip closed issues" block (lines 1095-1100) that checks for `**Status:** closed` and
     continues
  3. Update the phase title comment (line 1074): change `from open issue-level STATE.md` to
     `from issue-level STATE.md`
  4. Update the log message (line 1077): change `from open issue-level STATE.md` to
     `from issue-level STATE.md`
  5. Update the summary log (line 1126): change
     `"Phase 13 complete: $phase13_changed files changed, $phase13_skipped closed issues skipped"` to
     `"Phase 13 complete: $phase13_changed files changed"`
  - Files: `plugin/migrations/2.1.sh`
- Update `plugin/migrations/2.1.sh` Phase 14 (around lines 1146-1173):
  1. Remove the `phase14_skipped=0` variable initialization (line 1146)
  2. Remove the 10-line "Skip closed issues" block (lines 1156-1165) that checks STATE.md for closed status
     and continues
  3. Update the phase title comment (line 1130): change `in open issue PLAN.md files` to
     `in issue PLAN.md files`
  4. Update the log message (line 1133): change `in open issue PLAN.md files` to `in issue PLAN.md files`
  5. Update the summary log (line 1173): change
     `"Phase 14 complete: $phase14_changed files changed, $phase14_skipped closed issues skipped"` to
     `"Phase 14 complete: $phase14_changed files changed"`
  - Files: `plugin/migrations/2.1.sh`
- Update `plugin/migrations/2.1.sh` header comments (lines 34-37): Remove the "(closed issues are not modified)"
  annotations from items 13 and 14 in the changes list.
  - Files: `plugin/migrations/2.1.sh`
- Verify that 1.0.8.sh, 1.0.9.sh, 1.0.10.sh, and 2.0.sh already process all issues (no closed-issue filtering).
  No changes expected — this is a verification step only.
- Run `mvn -f client/pom.xml test` to verify all tests pass
- Update index.json to status: closed, progress: 100%
