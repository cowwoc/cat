# Plan: detect-duplicate-functionality

## Goal
Enhance the design stakeholder to scan the entire codebase for existing functionality that duplicates what a task introduces. When duplicate functionality is found (in codebase, JDK, or project dependencies), report a violation requiring the new code to reuse existing implementations.

## Satisfies
None - quality gate enhancement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** False positives flagging intentionally different implementations
- **Mitigation:** Provide clear violation messages with specific locations; allow context-aware dismissal

## Files to Modify
- plugin/stakeholders/design.md - Add duplicate functionality detection rules

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions

## Execution Steps
1. **Step 1:** Read current design stakeholder file
   - Files: plugin/stakeholders/design.md
   - Verify: Understand current checks performed

2. **Step 2:** Add duplicate functionality detection section
   - Add check for duplicate string handling utilities
   - Add check for duplicate null-safe comparisons
   - Add check for duplicate collection utilities
   - Add check for patterns already in JDK/dependencies
   - Verify: Section exists with clear violation criteria

3. **Step 3:** Update violation reporting format
   - Include location of existing functionality
   - Include recommendation to reuse
   - Verify: Format matches other stakeholder violations

4. **Step 4:** Run tests
   - Verify: `python3 /workspace/run_tests.py`
