# Plan: Fix work-implement-agent STATE.md section placement guidance

## Type
bugfix

## Goal
Update the delegation prompt template in `plugin/skills/work-implement-agent/first-use.md` to explicitly specify
that all STATE.md field updates (Target Branch, status, progress, resolution, etc.) must be placed in the
`# State` section at the top of the file, not inside Sub-Agent Waves sections.

## Files to Modify
- `plugin/skills/work-implement-agent/first-use.md`

## Post-conditions
- [ ] The delegation prompt template explicitly states that all STATE.md field updates go in the `# State` section
- [ ] The instruction is unambiguous and clearly separated from the Sub-Agent Waves content guidance
- [ ] The file compiles and passes any validation checks

## Sub-Agent Waves

### Wave 1
1. Read `plugin/skills/work-implement-agent/first-use.md` to understand the current STATE.md update instructions
2. Locate the section that describes STATE.md updates in the delegation prompt
3. Add explicit guidance: "All STATE.md field updates (Target Branch, status, progress, resolution, etc.) MUST be
   placed in the `# State` section at the top of the file. Never place field updates inside Sub-Agent Wave sections
   or implementation steps."
4. Commit the change with message: `bugfix: add STATE.md section placement guidance to work-implement-agent delegation prompt`
