# Plan: strip-orphaned-work-docs

## Current State
Three support files in `plugin/skills/work/` contain sections duplicated in active skills, mixed with unique content not found elsewhere.

## Target State
Each file retains only its unique content; all duplicate sections removed.

## Satisfies
None (infrastructure cleanup)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - no active code references these files
- **Mitigation:** Grep verification before removal

## Files to Modify
- `plugin/skills/work/anti-patterns.md` - Remove: Background Task Behavior (M293) [in MEMORY.md], Config settings table (lines 66-70) [in work-with-issue/work-prepare], Configuration-Driven Behavior Summary table [in work-with-issue/work-prepare], M### Series index [retrospectives are source of truth], A### Series index [retrospectives are source of truth]. Keep: Main Agent Boundaries section, Pre-Edit Checkpoint section.
- `plugin/skills/work/commit-rules.md` - Remove: Per-Step Commits section [in CLAUDE.md commit types], Commit type prefixes [in plugin/concepts/commit-types.md]. Keep: Enhanced Commit Message Format template (Problem Solved / Solution Implemented / Decisions Made / Deviations from Plan).
- `plugin/skills/work/deviation-rules.md` - Remove: Duplicate Task Handling section [in plugin/concepts/duplicate-issue.md and plugin/concepts/issue-resolution.md], User Review Checkpoint [in work-with-issue Steps 5-7]. Keep: Deviation Rules (auto-fix bugs, auto-add critical, ask about architectural, log enhancements), Plan Change Checkpoint (M034).

## Acceptance Criteria
- [ ] Operational behavior of /cat:work skill and related commands unchanged
- [ ] Tests passing
- [ ] Each file retains only unique content not found in any other active file
- [ ] No active references broken by removals

## Execution Steps
1. **Step 1:** Edit `plugin/skills/work/anti-patterns.md` - remove Background Task Behavior section (lines 9-24), Config settings list (lines 66-70), entire Anti-Pattern Index section (lines 94-166). Update title/header to reflect remaining content (Main Agent Boundaries).
   - Files: `plugin/skills/work/anti-patterns.md`
2. **Step 2:** Edit `plugin/skills/work/commit-rules.md` - remove Per-Step Commits section (lines 11-25), keep only Enhanced Commit Message Format section and example.
   - Files: `plugin/skills/work/commit-rules.md`
3. **Step 3:** Edit `plugin/skills/work/deviation-rules.md` - remove User Review Checkpoint section (lines 54-73), remove Duplicate Task Handling section (lines 75-87). Keep Deviation Rules and Plan Change Checkpoint.
   - Files: `plugin/skills/work/deviation-rules.md`
4. **Step 4:** Run `grep -r` to verify no active code references content that was removed.

## Success Criteria
- [ ] anti-patterns.md contains only Main Agent Boundaries and Pre-Edit Checkpoint
- [ ] commit-rules.md contains only Enhanced Commit Message Format template
- [ ] deviation-rules.md contains only Deviation Rules and Plan Change Checkpoint
- [ ] No grep hits for removed section headers in active skill files