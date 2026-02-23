# Plan: port-statusline-scripts-to-java

## Current State
Two bash scripts handle statusline functionality: `statusline-command.sh` (generates statusline content, referenced in
`.claude/settings.json`) and `statusline-install.sh` (copies statusline-command.sh to the project directory).

## Target State
Both scripts rewritten as Java tools in the jlink bundle.

## Satisfies
None (infrastructure/tech debt)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** `statusline-command.sh` is registered in `.claude/settings.json`; path must be updated
- **Mitigation:** Update settings.json atomically with the port

## Files to Modify
- `plugin/scripts/statusline-command.sh` — remove after port
- `plugin/scripts/statusline-install.sh` — remove after port
- `client/src/main/java/...` — new Java implementations
- `client/src/test/java/...` — new tests
- `.claude/settings.json` — update statusline command path
- `plugin/skills/statusline/first-use.md` — update invocations

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Read `statusline-command.sh` and document output format and behavior
   - Files: `plugin/scripts/statusline-command.sh`
2. **Step 2:** Write Java implementation for statusline-command
   - Files: `client/src/main/java/...`
3. **Step 3:** Read `statusline-install.sh` and document behavior
   - Files: `plugin/scripts/statusline-install.sh`
4. **Step 4:** Write Java implementation for statusline-install
   - Files: `client/src/main/java/...`
5. **Step 5:** Write tests for both Java implementations
   - Files: `client/src/test/java/...`
6. **Step 6:** Update `.claude/settings.json` to point to Java tool
   - Files: `.claude/settings.json`
7. **Step 7:** Update skill first-use.md to invoke Java tools
   - Files: `plugin/skills/statusline/first-use.md`
8. **Step 8:** Remove the original bash scripts
9. **Step 9:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged (statusline displays same content)
- [ ] All tests passing
- [ ] Code quality improved
- [ ] E2E: Run the statusline command and confirm it produces correct output
