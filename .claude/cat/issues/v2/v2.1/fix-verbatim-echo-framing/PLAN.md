# Plan: fix-verbatim-echo-framing

## Problem
Verbatim output skills use the instruction "The user wants you to respond with the content...verbatim" in first-use.md
files. This framing triggers analytical/interpretive mode in the LLM instead of mechanical echo behavior. The agent runs
the output script via Bash but leaves the result in the tool output block rather than echoing it as response text (M507).

## Satisfies
None - quality fix from M507 learning

## Reproduction Code
```
1. Invoke /cat:status
2. Agent runs get-status-output via Bash tool
3. Output appears in Bash tool result block, not in agent's text response
4. User sees raw tool output instead of formatted status display
```

## Expected vs Actual
- **Expected:** Agent echoes preprocessed script output as text in its response
- **Actual:** Agent runs script via Bash and leaves output in tool result block

## Root Cause
The phrase "The user wants you to respond with...verbatim" triggers analytical mode. The word "Echo" is a mechanical
command that aligns with LLM training for copy-paste behavior.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only changing instruction text in skill files
- **Mitigation:** Verify skills still load and display correctly after changes

## Files to Modify
- `plugin/skills/status/first-use.md` - Change verbatim instruction framing
- `plugin/skills/token-report/first-use.md` - Change verbatim instruction framing
- `plugin/skills/help/first-use.md` - Change verbatim instruction framing
- `plugin/skills/render-diff/first-use.md` - Change verbatim instruction framing
- `plugin/skills/work/first-use.md` - Change verbatim instruction framing
- `plugin/skills/render-output/first-use.md` - Change verbatim instruction framing
- `plugin/skills/skill-builder/first-use.md` - Change verbatim instruction framing (2 locations: usage + template)

## Acceptance Criteria
- [ ] All 7 files use "Echo" framing instead of "The user wants you to respond with" framing
- [ ] No remaining instances of "The user wants you to respond with" in plugin/skills/ (excluding learn/phase-analyze.md
  which documents the pattern)
- [ ] All affected skills load correctly via load-skill.sh

## Execution Steps
1. **Step 1:** Update verbatim instruction in each first-use.md file
   - Replace "The user wants you to respond with the content from X above, verbatim." with "Echo the content from X
     above exactly as it appears, with no changes."
   - Replace "The user wants you to respond with this text verbatim:" with "Echo the following text exactly as it
     appears, with no changes:"
   - Replace "The user wants you to respond with the preparing banner from X above, verbatim." with "Echo the preparing
     banner from X above exactly as it appears, with no changes."
   - For skill-builder template example, update both the usage instruction and the template pattern
   - Files: `plugin/skills/status/first-use.md`, `plugin/skills/token-report/first-use.md`,
     `plugin/skills/help/first-use.md`, `plugin/skills/render-diff/first-use.md`, `plugin/skills/work/first-use.md`,
     `plugin/skills/render-output/first-use.md`, `plugin/skills/skill-builder/first-use.md`

2. **Step 2:** Verify no remaining instances of old pattern
   - Grep plugin/skills/ for "The user wants you to respond with" — only learn/phase-analyze.md should remain
   - Files: all plugin/skills/**/*.md

3. **Step 3:** Update STATE.md to closed
   - Files: issue STATE.md

## Success Criteria
- [ ] Zero instances of "The user wants you to respond with" in plugin/skills/ except learn/phase-analyze.md
- [ ] All 7 files contain "Echo" framing
