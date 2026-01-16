# Plan: split-retrospective-files-by-time

## Current State
The retrospective files (mistakes.json, retrospectives.json) grow unbounded over time.
mistakes.json already exceeds 25K tokens, causing read errors.

## Target State
- Files split by time period (e.g., mistakes-2026-01.json, mistakes-2026-02.json)
- Skills updated to read/write across split files
- Old data archived but still accessible

## Satisfies
None - infrastructure/maintenance task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skills that read retrospective data need updates
- **Mitigation:** Update all skills atomically, test with existing data

## Files to Modify
- .claude/cat/retrospectives/*.json - Split existing files
- plugin/skills/learn-from-mistakes/SKILL.md - Update to handle splits
- plugin/skills/run-retrospective/SKILL.md - Update to handle splits
- Any scripts that read mistakes.json directly

## Acceptance Criteria
- [ ] mistakes.json split by year/month
- [ ] retrospectives.json split by year/month
- [ ] learn-from-mistakes skill reads/writes to correct split file
- [ ] run-retrospective skill aggregates across split files
- [ ] Existing data preserved and accessible

## Execution Steps
1. **Design split strategy** - Decide on time granularity (monthly vs quarterly)
2. **Create migration script** - Split existing data into time-based files
3. **Update skills** - Modify skills to handle split files
4. **Test** - Verify all operations work with split data
5. **Document** - Update any references to the file structure
