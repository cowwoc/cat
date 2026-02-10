# Plan: port-init-boxes

## Goal
Port `build-init-boxes.py` to a Java class in the hooks module.

## Current State
`build-init-boxes.py` (301 lines) generates 8 pre-formatted template boxes for the init skill output.
Java `ComputeBoxLines.java` and `DisplayUtils.java` already exist as shared utilities.

## Target State
Java class replaces the Python script. Init handler invokes Java directly without spawning Python subprocess.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Output format must remain character-for-character identical
- **Mitigation:** Diff-based comparison of old vs new output

## Scripts to Port
- `build-init-boxes.py` (301 lines) - Generates init wizard display boxes

## Files to Create/Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetInitOutput.java` - New or update existing
- Update init handler to call Java class instead of Python script

## Execution Steps
1. **Read `build-init-boxes.py`** to understand exact output format and logic
2. **Check if `GetInitOutput.java` already exists** and what it contains
3. **Read `.claude/cat/conventions/java.md`** for coding conventions
4. **Read existing Java patterns** (`DisplayUtils.java`, `GetWorkOutput.java`) to match style
5. **Port `build-init-boxes.py`** to Java
6. **Update init handler** to call Java class instead of Python script
7. **Remove `get-init-boxes.sh`** bash wrapper
8. **Run tests:** `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] Java class created matching exact Python output
- [ ] Init handler updated to call Java instead of Python
- [ ] Bash wrapper removed
- [ ] All tests pass
