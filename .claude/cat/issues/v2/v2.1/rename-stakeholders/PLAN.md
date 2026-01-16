# Plan: rename-stakeholders

## Goal
Move stakeholder files to new location and rename quality→design, tester→testing stakeholders.

## Satisfies
None - infrastructure/organization task

## Current State
- Stakeholders located at `plugin/.claude/cat/references/stakeholders/`
- Files named `quality.md` and `tester.md`

## Target State
- Stakeholders at `plugin/.claude/cat/stakeholders/`
- Files renamed to `design.md` and `testing.md`
- All references updated across entire codebase

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** External references will break if not all updated
- **Mitigation:** Comprehensive file scan before and after; grep verification

## Files to Modify
- plugin/.claude/cat/references/stakeholders/* - move to new location
- plugin/.claude/cat/stakeholders/quality.md - rename to design.md
- plugin/.claude/cat/stakeholders/tester.md - rename to testing.md
- All files referencing old paths - update references

## Acceptance Criteria
- [ ] `plugin/.claude/cat/stakeholders/` directory exists with moved files
- [ ] `quality.md` renamed to `design.md`
- [ ] `tester.md` renamed to `testing.md`
- [ ] All references updated across all files
- [ ] No broken references remain (grep verification)

## Execution Steps
1. **Step 1:** Find all files referencing stakeholders path
   - Verify: `grep -r "references/stakeholders" plugin/`
2. **Step 2:** Move directory to new location
   - Files: plugin/.claude/cat/references/stakeholders/ → plugin/.claude/cat/stakeholders/
   - Verify: `ls plugin/.claude/cat/stakeholders/`
3. **Step 3:** Rename stakeholder files
   - quality.md → design.md
   - tester.md → testing.md
   - Verify: `ls plugin/.claude/cat/stakeholders/`
4. **Step 4:** Update all references in codebase
   - Update path references
   - Update stakeholder name references (quality→design, tester→testing)
   - Verify: `grep -r "references/stakeholders\|quality\.md\|tester\.md" plugin/`
5. **Step 5:** Run tests
   - Verify: `python3 /workspace/run_tests.py`
