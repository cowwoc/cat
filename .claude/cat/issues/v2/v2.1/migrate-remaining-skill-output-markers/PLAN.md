# Plan: Migrate Remaining Skill Output Markers

## Goal
Migrate 6 remaining skills from old "SKILL OUTPUT X" marker pattern to the standardized `<output skill="X">` tag
pattern used by status, config, cleanup, statusline, work-complete, and run-retrospective.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Skills may reference SKILL OUTPUT markers in Java handlers, bash scripts, and multiple skill files
- **Mitigation:** Migrate one skill at a time, test each after migration

## Skills to Migrate

| Skill | Old Marker | New Tag |
|-------|-----------|---------|
| help | SKILL OUTPUT HELP DISPLAY | `<output skill="help">` |
| render-diff | SKILL OUTPUT RENDER DIFF | `<output skill="render-diff">` |
| token-report | SKILL OUTPUT TOKEN REPORT | `<output skill="token-report">` |
| delegate | SKILL OUTPUT DELEGATE PROGRESS | `<output skill="delegate">` |
| init | SKILL OUTPUT INIT BOXES | `<output skill="init">` |
| monitor-subagents | SKILL OUTPUT MONITOR SUBAGENTS | `<output skill="monitor-subagents">` |

## Also Update
- skill-builder/first-use.md: Update documentation references to teach new `<output>` tag pattern
- learn/phase-analyze.md: Minor SKILL OUTPUT reference in example text

## Files to Modify
- plugin/skills/help/first-use.md - Replace SKILL OUTPUT HELP DISPLAY references with output tag
- plugin/skills/render-diff/first-use.md - Replace SKILL OUTPUT RENDER DIFF references with output tag
- plugin/skills/token-report/first-use.md - Replace SKILL OUTPUT TOKEN REPORT references with output tag
- plugin/skills/delegate/first-use.md - Replace SKILL OUTPUT DELEGATE PROGRESS references with output tag
- plugin/skills/init/first-use.md - Replace SKILL OUTPUT INIT BOXES references with output tag
- plugin/skills/monitor-subagents/first-use.md - Replace SKILL OUTPUT MONITOR SUBAGENTS references with output tag
- plugin/skills/skill-builder/first-use.md - Update documentation to teach new pattern
- plugin/skills/learn/phase-analyze.md - Update minor reference
- Java handlers (if any) that produce SKILL OUTPUT headers

## Acceptance Criteria
- [ ] All 6 skills use `<output skill="X">` tags instead of SKILL OUTPUT markers
- [ ] Java handler output matches new tag format (if applicable)
- [ ] skill-builder documentation updated to teach the new pattern
- [ ] All skills still function correctly after migration
- [ ] E2E: Invoke /cat:help, /cat:render-diff, /cat:token-report and confirm output is displayed correctly

## Execution Steps
1. **Audit each skill**: For each of the 6 skills, identify all files that reference SKILL OUTPUT markers and check
   Java handlers for corresponding output format
2. **Migrate help skill**: Replace SKILL OUTPUT HELP DISPLAY with output tag pattern
3. **Migrate render-diff skill**: Replace SKILL OUTPUT RENDER DIFF with output tag pattern
4. **Migrate token-report skill**: Replace SKILL OUTPUT TOKEN REPORT with output tag pattern
5. **Migrate delegate skill**: Replace SKILL OUTPUT DELEGATE PROGRESS with output tag pattern
6. **Migrate init skill**: Replace SKILL OUTPUT INIT BOXES with output tag pattern
7. **Migrate monitor-subagents skill**: Replace SKILL OUTPUT MONITOR SUBAGENTS with output tag pattern
8. **Update skill-builder docs**: Update documentation to teach new pattern
9. **Update learn phase-analyze**: Fix minor reference
10. **Test migrated skills**: Verify each skill works correctly

## Success Criteria
- [ ] grep -r "SKILL OUTPUT" plugin/skills/ returns zero matches (excluding learn example text if kept)
- [ ] All migrated skills produce correct output when invoked
- [ ] No regressions in existing functionality
