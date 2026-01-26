# Plan: deduplicate-embedded-content

## Current State
Multiple handlers duplicate the box-building pattern inline instead of using shared functions.

**Original approach (ABANDONED):** Static template files loaded on-demand.
**Why abandoned:** LLMs cannot count character display widths, so static templates with placeholders
would result in misaligned boxes when values are substituted.

## Target State
Extract `build_header_box()` to `status_handler.py` (where other box utilities live) and refactor
handlers to use the shared function instead of duplicating the pattern inline.

## Duplication Found

| Handler | Occurrences | Lines |
|---------|-------------|-------|
| `add_handler.py` | 2x | 59-76, 107-125 |
| `cleanup_handler.py` | 3x | 100-116, 178-194, 256-272 |
| `stakeholder_handler.py` | 1x (clean implementation) | 11-35 |

## Satisfies
None - infrastructure/optimization task

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - purely internal refactoring
- **Mitigation:** All handlers produce identical output after refactor

## Files to Modify
- `plugin/hooks/skill_handlers/status_handler.py` - Add `build_header_box()` function
- `plugin/hooks/skill_handlers/stakeholder_handler.py` - Import from status_handler
- `plugin/hooks/skill_handlers/add_handler.py` - Use shared function (2 places)
- `plugin/hooks/skill_handlers/cleanup_handler.py` - Use shared function (3 places)

## Acceptance Criteria
- [ ] `build_header_box()` exists in `status_handler.py`
- [ ] `stakeholder_handler.py` imports and uses shared function
- [ ] `add_handler.py` uses shared function (2 places)
- [ ] `cleanup_handler.py` uses shared function (3 places)
- [ ] All tests pass
- [ ] Box output is identical before/after refactor

## Execution Steps
1. **Step 1:** Add `build_header_box()` to `status_handler.py`
   - Copy implementation from `stakeholder_handler.py`
   - Export in module

2. **Step 2:** Update `stakeholder_handler.py` to import from status_handler
   - Remove local `build_header_box()` function
   - Import from status_handler

3. **Step 3:** Refactor `add_handler.py` to use shared function
   - Replace inline box-building (2 places)

4. **Step 4:** Refactor `cleanup_handler.py` to use shared function
   - Replace inline box-building (3 places)

5. **Step 5:** Run tests, verify output matches
