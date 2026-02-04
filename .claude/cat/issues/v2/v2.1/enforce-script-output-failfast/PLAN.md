# Plan: enforce-script-output-failfast

## Current State
Skills with script-generated output (status, work, help, add) contain fallback instructions to
"run scripts manually" if SCRIPT OUTPUT is missing. This creates two problems:
1. Agents sometimes skip straight to manual script execution
2. Terminology inconsistency: "pre-rendered" vs "SCRIPT OUTPUT"

## Target State
Skills should:
1. Check for SCRIPT OUTPUT section
2. If missing: FAIL FAST and STOP (never manually run scripts)
3. Output script content verbatim
4. Done

Rename all "pre-rendered" terminology to "script output" for consistency.

## Satisfies
None - infrastructure/optimization issue

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - if scripts work correctly, behavior unchanged
- **Mitigation:** Test each skill after refactoring

## Files to Modify
- `plugin/skills/status/SKILL.md` - Remove manual script fallback, rename terminology
- `plugin/skills/work/SKILL.md` - Remove manual script fallback, rename terminology
- `plugin/skills/help/SKILL.md` - Remove manual script fallback, rename terminology
- `plugin/skills/add/SKILL.md` - Remove manual script fallback, rename terminology

## Acceptance Criteria
- [ ] Behavior unchanged when scripts run correctly
- [ ] All tests still pass
- [ ] Skills fail fast if SCRIPT OUTPUT missing (no manual fallback)
- [ ] "pre-rendered" renamed to "script output" throughout
- [ ] No manual script execution instructions remain

## Execution Steps
1. **Step 1:** Audit each skill for manual script fallback patterns
   - Files: plugin/skills/*/SKILL.md
   - Identify: Instructions to run scripts manually if output missing
2. **Step 2:** Refactor status skill
   - Remove manual fallback, add fail-fast
   - Rename "pre-rendered" to "script output"
3. **Step 3:** Refactor work skill
   - Remove manual fallback, add fail-fast
   - Rename "pre-rendered" to "script output"
4. **Step 4:** Refactor remaining skills (help, add)
   - Apply same pattern
5. **Step 5:** Run tests and commit
   - Message: "refactor: enforce script output fail-fast, remove manual fallbacks"
