# Plan: port-status-display

## Goal
Port `get-status-display.py` to a Java class in the hooks module.

## Current State
`get-status-display.py` (605 lines) is the most complex display script. It reads CAT state (versions, issues,
locks, progress), computes statistics, and renders a comprehensive multi-level status display with progress bars.

## Target State
Java class replaces the Python script. Status handler invokes Java directly without spawning Python subprocess.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Output format must remain character-for-character identical
- **Mitigation:** Diff-based comparison of old vs new output; incremental porting with section-by-section validation

## Scripts to Port
- `get-status-display.py` (605 lines) - Comprehensive project status display

## Files to Create/Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - New or update existing
- Update status handler to call Java class instead of Python script
- Remove `get-status-display.sh` bash wrapper

## Execution Steps
1. **Read `get-status-display.py`** thoroughly to understand all rendering sections
2. **Check if `GetStatusOutput.java` already exists** and what it contains
3. **Read `.claude/cat/conventions/java.md`** for coding conventions
4. **Read existing Java patterns** (`DisplayUtils.java`, other Output classes) to match style
5. **Port state-reading logic** (version discovery, issue parsing, lock checking)
6. **Port statistics computation** (progress bars, completion percentages)
7. **Port display rendering** (box formatting, section layout)
8. **Update status handler** to call Java class instead of Python script
9. **Remove `get-status-display.sh`** bash wrapper
10. **Verify output parity** with diff-based comparison
11. **Run tests:** `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] Java class created matching exact Python output
- [ ] Status handler updated to call Java instead of Python
- [ ] Bash wrapper removed
- [ ] Output is character-for-character identical
- [ ] All tests pass
