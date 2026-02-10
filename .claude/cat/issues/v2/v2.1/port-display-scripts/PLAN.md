# Plan: port-display-scripts

## Current State
Display and box-rendering logic is split across 16 Python and bash scripts in `plugin/scripts/`.
Some Java equivalents already exist in the hooks module (e.g., `DisplayUtils.java`, `ComputeBoxLines.java`).

## Target State
All display/box rendering scripts ported to Java classes in the hooks module, invoked directly by hook handlers
without spawning Python or bash subprocesses.

## Satisfies
None - infrastructure/tech debt

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Output format must remain identical (character-for-character)
- **Mitigation:** Diff-based comparison of old vs new output for each script

## Scripts to Port

### Python Display Scripts
- `build_box_lines.py` - Box line rendering (Java: `ComputeBoxLines.java` already exists)
- `build-init-boxes.py` - Init wizard display boxes
- `get-checkpoint-box.py` - Checkpoint display box
- `get-cleanup-display.py` - Cleanup command display
- `get-help-display.py` - Help command display
- `get-issue-complete-box.py` - Issue completion box
- `get-next-task-box.py` - Next task suggestion box
- `get-status-display.py` - Status command display
- `get-work-boxes.py` - Work command display boxes
- `compute-token-table.py` - Token report table rendering

### Bash Wrapper Scripts (eliminated when Python targets are ported)
- `get-help-display.sh` - Wrapper for get-help-display.py
- `get-work-boxes.sh` - Wrapper for get-work-boxes.py
- `get-status-display.sh` - Wrapper for get-status-display.py
- `get-token-report.sh` - Wrapper for compute-token-table.py
- `get-init-boxes.sh` - Wrapper for build-init-boxes.py
- `render-add-complete.sh` - Renders add command completion display

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/` - Add/update display classes
- `plugin/scripts/` - Remove ported scripts
- `plugin/skills/*/SKILL.md` - Update script invocation paths

## Execution Steps
1. **Port Python display scripts to Java:** For each Python script, create corresponding Java class
   in the hooks module matching the exact output format
2. **Update skill handlers:** Modify Java handlers to call display classes directly instead of
   spawning Python subprocesses
3. **Remove bash wrappers:** Delete thin bash wrappers that only called the ported Python scripts
4. **Verify output parity:** Run before/after comparison for each ported script
5. **Run tests:** Execute `mvn -f hooks/pom.xml test` to verify no regressions

## Success Criteria
- [ ] All 10 Python display scripts have Java equivalents
- [ ] All 6 bash wrapper scripts removed
- [ ] Output is character-for-character identical to original scripts
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)
- [ ] No Python/bash subprocess spawning for display rendering