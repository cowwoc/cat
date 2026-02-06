# Plan: research-executive-summary

## Goal
Enhance /cat:research to produce an executive summary that aggregates findings across all 9 stakeholders into actionable
solution options with tradeoffs, so users can quickly choose based on their preferences rather than reading separate
stakeholder reports.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must preserve detailed stakeholder output while adding summary layer
- **Mitigation:** Add summary as new section, don't remove existing content

## Files to Modify
- plugin/skills/research/SKILL.md - Add executive summary generation step

## Acceptance Criteria
- [ ] Executive summary section added at top of research output
- [ ] 2-4 solution approaches identified with clear pros/cons
- [ ] Options organized by user preferences (cost vs speed vs simplicity vs control)
- [ ] Visual output improved for readability
- [ ] Detailed stakeholder findings preserved below summary

## Execution Steps
1. **Step 1:** Read current research skill
   - Files: plugin/skills/research/SKILL.md
   - Verify: Understand current output structure

2. **Step 2:** Design executive summary format
   - Include: Top solution options, tradeoff matrix, preference-based recommendations
   - Verify: Format is scannable and actionable

3. **Step 3:** Add aggregation step to research workflow
   - After collecting all 9 stakeholder results, synthesize into summary
   - Verify: Summary captures cross-stakeholder insights

4. **Step 4:** Improve visual output formatting
   - Better headers, spacing, visual hierarchy
   - Verify: Output is readable and professional
