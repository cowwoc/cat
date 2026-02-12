# Plan: add-skill-content-headers

## Goal
Add descriptive markdown headers to all skill content.md files that are missing them, ensuring consistent structure across all skills.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Verbatim output skills have minimal content.md files. Headers must not break preprocessing.
- **Mitigation:** Test that skills with headers still function correctly.

## Files to Modify
- plugin/skills/add/content.md - Add header above existing `<objective>` tag
- plugin/skills/config/content.md - Add header above existing `<objective>` tag
- plugin/skills/init/content.md - Add header above existing `<objective>` tag
- plugin/skills/remove/content.md - Add header above existing `<objective>` tag
- plugin/skills/research/content.md - Add header above existing `<objective>` tag
- plugin/skills/help/content.md - Add header before verbatim output instruction
- plugin/skills/render-diff/content.md - Add header before verbatim output instruction
- plugin/skills/render-output/content.md - Add header before verbatim output instruction
- plugin/skills/status/content.md - Add header before verbatim output instruction
- plugin/skills/token-report/content.md - Add header before verbatim output instruction

## Acceptance Criteria
- [ ] All 10 content.md files have a `# Skill Name` markdown header as first line
- [ ] Each header is followed by a brief description paragraph
- [ ] Existing behavior is unchanged (no functional regressions)
- [ ] XML-tagged skills still parse correctly
- [ ] Verbatim output skills still function correctly
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Add headers to XML-tagged skills (5 files)
   - Files: plugin/skills/add/content.md, config/content.md, init/content.md, remove/content.md, research/content.md
   - Pattern: Add `# Skill Name\n\nBrief description.\n\n` before the existing `<objective>` tag
   - Header naming convention: Match existing pattern (e.g., `# Add Issue or Version`, `# CAT Configuration`)
2. **Step 2:** Add headers to verbatim output skills (5 files)
   - Files: plugin/skills/help/content.md, render-diff/content.md, render-output/content.md, status/content.md, token-report/content.md
   - Pattern: Add `# Skill Name\n\nBrief description.\n\n` before the existing instruction text
   - Header naming convention: Match existing pattern (e.g., `# Help`, `# Render Diff`)
3. **Step 3:** Verify no regressions by running existing tests
   - Run: mvn -f hooks/pom.xml verify

## Success Criteria
- [ ] All 10 files updated with consistent header format
- [ ] All existing tests pass with no regressions