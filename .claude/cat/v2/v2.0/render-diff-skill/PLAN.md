# Plan: render-diff-skill

## Goal
Create a `/cat:render-diff` skill that transforms raw git diff output into the Variant 2 "Isolated Changes" format specified in work.md, ensuring consistent and readable diff presentation at approval gates.

## Satisfies
- None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Section identification logic varies by file type
- **Mitigation:** Start with simple heuristics, iterate based on usage

## Files to Create
- skills/render-diff/SKILL.md - Skill documentation
- scripts/render-diff.sh - Bash script to transform git diff

## Files to Modify
- commands/work.md - Update approval gate to invoke skill
- .claude/cat/workflows/work.md - Update diff format section to reference skill
- .claude/cat/workflows/merge-and-cleanup.md - Reference skill for conflict diff display

## Acceptance Criteria
- [ ] Skill accepts git diff output (piped or as argument)
- [ ] Output matches Variant 2 format from work.md
- [ ] Respects terminalWidth from cat-config.json
- [ ] Header box shows file count and +/- line stats
- [ ] Sections identified for: markdown (headings), code (functions), config (keys)
- [ ] Unchanged context shown without markers
- [ ] Only actual changes get +/- markers
- [ ] work.md updated to invoke skill at approval gate

## Execution Steps
1. **Step 1:** Create render-diff.sh script
   - Parse git diff output into structured data
   - Identify sections based on file type
   - Apply width wrapping from config
   - Output Variant 2 format
   - Verify: Script produces correct output for sample diff

2. **Step 2:** Create SKILL.md documentation
   - Document usage and parameters
   - Include examples
   - Verify: Skill appears in /cat:help

3. **Step 3:** Update workflows to reference skill
   - commands/work.md: Replace manual diff format with `/cat:render-diff` invocation
   - workflows/work.md: Update "Diff Format (Variant 2)" section to reference skill
   - workflows/merge-and-cleanup.md: Add skill reference for conflict display
   - Verify: All diff display points invoke the skill
