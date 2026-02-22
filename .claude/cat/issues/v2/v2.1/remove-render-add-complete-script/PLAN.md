# Plan: remove-render-add-complete-script

## Goal
Remove the last Python/bash display script (`render-add-complete.sh`) by wiring the existing Java
`GetAddOutput` class into the jlink image and updating the `add` skill to call it directly.

## Satisfies
None - infrastructure/tech debt (completes port-display-scripts parent issue)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Multi-issue display (comma-separated names) exists in Python but not in Java class
- **Mitigation:** Port multi-issue support to Java before removing Python script

## Files to Modify
- `client/build-jlink.sh` - Add `get-add-output:skills.GetAddOutput` to HANDLERS array
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java` - Add multi-issue support and CLI main method
- `plugin/skills/add/first-use.md` - Replace `render-add-complete.sh` invocations with `client/bin/get-add-output`
- `plugin/scripts/render-add-complete.sh` - Delete

## Files to Delete
- `plugin/scripts/render-add-complete.sh`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add multi-issue support to GetAddOutput.java:** The Python script handles comma-separated issue
   names (lines 102-112). Port this logic to the Java class's `buildIssueDisplay` method.
2. **Add CLI main method to GetAddOutput.java:** Add `public static void main(String[])` that parses
   `--type`, `--name`, `--version`, `--issue-type`, `--deps`, `--parent`, `--path` arguments and
   calls `getOutput()`. Follow the pattern used by other skill output classes (e.g., `GetStatusOutput`).
3. **Register jlink launcher:** Add `"get-add-output:skills.GetAddOutput"` to the HANDLERS array in
   `client/build-jlink.sh`.
4. **Update add/first-use.md:** Replace the two `render-add-complete.sh` invocations (lines 723 and
   1437) with equivalent calls to `"${CLAUDE_PLUGIN_ROOT}/client/bin/get-add-output"`.
5. **Delete render-add-complete.sh:** Remove `plugin/scripts/render-add-complete.sh`.
6. **Run tests:** Execute `mvn -f client/pom.xml test` to verify no regressions.

## Post-conditions
- [ ] `plugin/scripts/render-add-complete.sh` does not exist
- [ ] `get-add-output` launcher exists in `client/build-jlink.sh` HANDLERS array
- [ ] `plugin/skills/add/first-use.md` does not reference `render-add-complete.sh`
- [ ] `GetAddOutput.java` handles both single and multi-issue display
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: `client/bin/get-add-output --type issue --name test-issue --version 2.1` produces box output
