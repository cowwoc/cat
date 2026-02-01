# Plan: simplify-prerendered-skills

## Current State
Skills with pre-rendered output (status, work, help, add) contain multi-step procedural instructions
that agents skip, summarize, or "optimize away". This causes PATTERN-008 (14 occurrences) - agents
manually construct boxes instead of using pre-rendered content.

## Target State
Skills with pre-rendered output should have minimal procedural steps:
1. Check for SCRIPT OUTPUT section
2. Output it verbatim
3. Done

No multi-step procedures that agents can skip.

## Satisfies
None - infrastructure/optimization issue addressing PATTERN-008

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - output behavior unchanged
- **Mitigation:** Test each skill after refactoring to verify output matches

## Files to Modify
- `plugin/skills/status/SKILL.md` - Simplify to copy-paste workflow
- `plugin/skills/work/SKILL.md` - Simplify progress banner usage
- `plugin/skills/help/SKILL.md` - Simplify output workflow
- `plugin/skills/add/SKILL.md` - Simplify completion display

## Acceptance Criteria
- [ ] Behavior unchanged - same output before and after
- [ ] All tests still pass
- [ ] Skills reduced to "check → output verbatim → done" pattern
- [ ] No multi-step procedures that could be skipped
- [ ] PATTERN-008 addressed structurally

## Execution Steps
1. **Step 1:** Audit each skill for pre-rendered output patterns
   - Files: plugin/skills/*/SKILL.md
   - Identify: Multi-step procedures around SCRIPT OUTPUT
2. **Step 2:** Refactor status skill
   - Remove procedural steps, keep only copy-paste instruction
   - Verify: /cat:status produces same output
3. **Step 3:** Refactor work skill
   - Simplify progress banner sections
   - Verify: /cat:work displays same banners
4. **Step 4:** Refactor remaining skills (help, add)
   - Apply same pattern
   - Verify: Each skill produces same output
5. **Step 5:** Commit changes
   - Message: "refactor: simplify pre-rendered output skills (PATTERN-008)"
