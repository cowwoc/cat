# Plan: fix-stale-wave-job-references

## Goal
Fix all stale references to the old `wave` terminology (unit of work in plan.md sections) that was renamed
to `job`. The section heading `## Sub-Agent Waves` and sub-section `### Wave N` were replaced with `## Jobs`
and `### Job N`. Also add a migration phase to update existing plan.md files on disk.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Renaming test helpers and their bats files could break test imports if not updated
  consistently
- **Mitigation:** Update all references in the same commit; run `mvn -f client/pom.xml verify -e` to verify

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — rename
  `EXECUTION_WAVES_PATTERN` constant, update Javadoc, fix regex from `## Execution Waves` to `## Jobs`,
  rename `wavesSection`/`wavesContent` variables, update inline comment
- `README.md` — rename `### Wave` section heading and all wave terminology to `### Job`
- `tests/plan-builder-invocation-helper.bash` — update grep regex from `Sub-Agent Waves` to `Jobs` and
  update comment/output string
- `tests/work-implement-agent-has-steps.bats` — update all `Sub-Agent Waves` fixtures and test names to
  `Jobs`
- `tests/work-implement-agent-plan-builder-invocation.bats` — update fixtures from `## Sub-Agent
  Waves`/`### Wave 1` to `## Jobs`/`### Job 1` and update expected output string
- `tests/work-implement-agent-waves-count.bats` → rename to `tests/work-implement-agent-jobs-count.bats`
  and update all wave references to job
- `tests/waves-count-helper.bash` → rename to `tests/jobs-count-helper.bash` and update all wave
  references to job
- `plugin/skills/work-implement-agent/test-trust-gate-routing.bats` — fix `### Wave 1` fixture to
  `### Job 1`
- `plugin/migrations/2.1.sh` — add a new phase that renames `## Sub-Agent Waves` → `## Jobs` and
  `### Wave N` → `### Job N` in all plan.md files

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Update `WorkPrepare.java`:
  - Rename constant `EXECUTION_WAVES_PATTERN` → `JOBS_PATTERN`
  - Update Javadoc from `"Execution Waves"` to `"Jobs"`
  - Fix regex string from `"## Execution Waves"` to `"## Jobs"`
  - Rename local variable `wavesSection` → `jobsSection` (lines 1454–1455)
  - Rename local variable `wavesContent` → `jobsContent` (line 1457–1458)
  - Update inline comment on line 1452 from `"Count items in Execution Waves (### Wave N sections...)"` to
    `"Count items in Jobs (### Job N sections...)"`
- Update `README.md`:
  - Rename `### Wave` heading → `### Job`
  - Replace all wave-as-unit-of-work terminology in that section with job equivalents
- Fix `plugin/skills/work-implement-agent/test-trust-gate-routing.bats`:
  - Replace `### Wave 1` → `### Job 1` in the test fixture

### Job 2
- Update test helpers:
  - `tests/plan-builder-invocation-helper.bash`: change detect comment, grep regex
    (`Sub-Agent Waves` → `Jobs`), and output string (`Sub-Agent Waves` → `Jobs`)
  - `tests/work-implement-agent-has-steps.bats`: update all `Sub-Agent Waves` references to `Jobs`
  - `tests/work-implement-agent-plan-builder-invocation.bats`: update fixtures and expected output
  - Rename `tests/work-implement-agent-waves-count.bats` → `tests/work-implement-agent-jobs-count.bats`
    and update all internal `Wave`/`WAVES`/`wave` references to `Job`/`JOBS`/`job`
  - Rename `tests/waves-count-helper.bash` → `tests/jobs-count-helper.bash` and update its contents;
    update the `source` directive in the renamed bats file to point to the new helper name
- Add migration phase to `plugin/migrations/2.1.sh`:
  - Add a new phase (next sequential number after existing phases) that:
    - Renames `^## Sub-Agent Waves$` → `## Jobs` in all plan.md files under `.cat/issues/`
    - Renames `^### Wave ` prefix → `### Job ` in all plan.md files under `.cat/issues/`
    - Is idempotent (skip files already using `## Jobs`)
    - Processes all issues including closed ones

## Post-conditions
- [ ] `grep -r "Sub-Agent Waves\|## Execution Waves\|### Wave " tests/ plugin/ client/` returns no matches
  outside of migration script history comments and the migration script's own sed commands
- [ ] `grep -r "EXECUTION_WAVES_PATTERN\|wavesSection\|wavesContent" client/` returns no matches
- [ ] `grep "### Wave" README.md` returns no matches
- [ ] All tests pass: `mvn -f client/pom.xml verify -e`
- [ ] Migration phase is idempotent: running it twice produces no changes on the second run
