# Plan: add-subagent-content-relay-anti-pattern

## Goal

Add a "Subagent Content Relay Anti-Pattern" section to the optimize-execution skill and update
Step 5 recommendations to detect this pattern. The main agent should not load content it doesn't
need just to relay it to subagents — subagents should load their own content.

## Satisfies

None — internal tooling improvement

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only change to a skill file
- **Mitigation:** Review that the new section is consistent with existing optimization patterns

## Files to Modify

- `plugin/skills/optimize-execution/first-use.md` — Add new optimization pattern section and update
  Step 5 recommendations

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Add "Subagent Content Relay Anti-Pattern" section to Optimization Pattern Details**
   - Files: `plugin/skills/optimize-execution/first-use.md`
   - Add after the "Script Extraction Opportunities" section, before Step 6
   - Document: definition, detection criteria, correct patterns, anti-pattern examples, impact

2. **Update Step 5 recommendations list**
   - Files: `plugin/skills/optimize-execution/first-use.md`
   - Add item 10: "Content relay detection" to the numbered list in Step 5

## Post-conditions

- [ ] `plugin/skills/optimize-execution/first-use.md` contains a "Subagent Content Relay Anti-Pattern"
  section in the Optimization Pattern Details
- [ ] Step 5 includes content relay detection in the recommendations list
- [ ] The new section clearly states: subagents load their own content unless the main agent already
  has it in context for its own purposes
