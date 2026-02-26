# Plan: cleanup-ported-scripts

## Goal
Verify already-ported Java equivalents work correctly, then remove obsolete Python scripts and bash wrappers.

## Current State
Several scripts already have Java equivalents (GetHelpOutput, GetCleanupOutput, GetWorkOutput, GetTokenReportOutput,
ComputeBoxLines). The Python originals and bash wrappers may still exist and need removal once verified.

## Target State
All obsolete Python display scripts and bash wrappers removed. Only Java implementations remain.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if Java equivalents are verified first
- **Mitigation:** Verify each Java class produces identical output before removing Python original

## Scripts to Verify and Remove

### Python scripts (verify Java equivalent, then remove)
- `build_box_lines.py` → `ComputeBoxLines.java`
- `get-help-display.py` → `GetHelpOutput.java`
- `get-cleanup-display.py` → `GetCleanupOutput.java`
- `get-work-boxes.py` → `GetWorkOutput.java`
- `compute-token-table.py` → `GetTokenReportOutput.java`

### Bash wrappers to remove
- `get-help-display.sh`
- `get-work-boxes.sh`
- `get-status-display.sh` (after port-status-display completes)
- `get-token-report.sh`
- `get-init-boxes.sh` (after port-init-boxes completes)
- `render-add-complete.sh`

## Execution Steps

### Step 1: Code Path Parity Analysis
For each Python→Java pair listed below, perform a side-by-side review:
1. Read the Python script and catalog every code path (conditionals, error branches, edge cases, format variations)
2. Read the Java equivalent and confirm each cataloged path has a corresponding implementation
3. Document any gaps found — these must be fixed before the Python script can be deleted

**Pairs to analyze:**
| Python Script | Java Equivalent |
|---------------|-----------------|
| `build_box_lines.py` | `ComputeBoxLines.java` |
| `get-help-display.py` | `GetHelpOutput.java` |
| `get-cleanup-display.py` | `GetCleanupOutput.java` |
| `compute-token-table.py` | `GetTokenReportOutput.java` |
| `get-work-boxes.py` | `GetWorkOutput.java` |
| `get-checkpoint-box.py` | (identify Java equivalent) |
| `get-issue-complete-box.py` | (identify Java equivalent) |
| `get-next-task-box.py` | (identify Java equivalent) |
| `create-issue.py` | (identify Java equivalent) |

### Step 2: Handler Wiring Verification
For each script being removed, trace the full call chain:
1. Identify which hook handler or skill invokes the script (grep `hooks.json`, skill SKILL.md files, agent `.md` files)
2. Confirm the handler now calls the Java equivalent (via jlink binary), not the Python/bash script
3. If any handler still references the old script, update it before deletion

### Step 3: Full Reference Scan
Grep the entire `plugin/` directory (and `.claude/` config) for references to every file being deleted:
- `grep -r "build_box_lines.py"`, `grep -r "get-help-display.py"`, etc.
- `grep -r "get-help-display.sh"`, `grep -r "get-work-boxes.sh"`, etc.
- Any match outside of the file itself is a blocker — update or remove the reference first

### Step 4: Remove Verified Scripts
Only after Steps 1-3 pass for a given script, remove it from `plugin/scripts/`.

### Step 5: Run Tests
Run `mvn -f client/pom.xml test` and confirm all tests pass.

### Step 6: Smoke Test
Run at least one jlink binary that replaced a deleted script (e.g., `client/bin/get-help-output`) and confirm it
produces reasonable output without errors.

## Dependencies
- 2.1-port-completion-boxes (for checkpoint/issue-complete/next-task scripts)
- 2.1-port-init-boxes (for init-boxes script)
- 2.1-port-status-display (for status-display script)

## Post-conditions
- [ ] Every code path in each Python script has a verified counterpart in the Java equivalent (Step 1)
- [ ] Every handler/skill that referenced old scripts now references Java equivalents (Step 2)
- [ ] Zero references to deleted files remain in the codebase (Step 3)
- [ ] All obsolete Python scripts removed
- [ ] All obsolete bash wrappers removed
- [ ] All tests pass (`mvn -f client/pom.xml test` exit code 0)
- [ ] At least one jlink replacement binary smoke-tested end-to-end
