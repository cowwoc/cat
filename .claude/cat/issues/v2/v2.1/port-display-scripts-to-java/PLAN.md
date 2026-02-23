# Plan: port-display-scripts-to-java

## Current State
`render-add-complete.sh` renders completion boxes for the `/cat:add` skill. It depends on
`plugin/scripts/lib/emoji_widths.py` for proper text alignment with emoji characters.

## Target State
Both `render-add-complete.sh` and the emoji width functionality from `emoji_widths.py` rewritten in Java. The emoji
width data (`plugin/scripts/lib/emoji-widths.json`) remains as a data file loaded by the Java implementation.

## Satisfies
None (infrastructure/tech debt)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Emoji width calculations must be exact to maintain box alignment
- **Mitigation:** Port emoji-widths.json data directly; test alignment output

## Files to Modify
- `plugin/scripts/render-add-complete.sh` — remove after port
- `plugin/scripts/lib/emoji_widths.py` — remove after port
- `client/src/main/java/...` — new Java implementations (render-add-complete + emoji width utils)
- `client/src/test/java/...` — new tests
- `plugin/skills/add/first-use.md` — update invocation

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Read `emoji_widths.py` and `emoji-widths.json` to understand width calculation logic
   - Files: `plugin/scripts/lib/emoji_widths.py`, `plugin/scripts/lib/emoji-widths.json`
2. **Step 2:** Write Java emoji width utility class that loads emoji-widths.json
   - Files: `client/src/main/java/...`
3. **Step 3:** Read `render-add-complete.sh` and document all rendering modes and output formats
   - Files: `plugin/scripts/render-add-complete.sh`
4. **Step 4:** Write Java implementation for render-add-complete
   - Files: `client/src/main/java/...`
5. **Step 5:** Write tests for both emoji width utils and render-add-complete
   - Files: `client/src/test/java/...`
6. **Step 6:** Update skill first-use.md to invoke Java tool
   - Files: `plugin/skills/add/first-use.md`
7. **Step 7:** Remove the original scripts
   - Files: `plugin/scripts/render-add-complete.sh`, `plugin/scripts/lib/emoji_widths.py`
8. **Step 8:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged (box rendering identical)
- [ ] All tests passing
- [ ] Code quality improved
- [ ] E2E: Run `/cat:add` and confirm the completion box renders correctly with proper alignment
