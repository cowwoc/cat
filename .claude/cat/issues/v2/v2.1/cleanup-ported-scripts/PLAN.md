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
1. **Verify each Java equivalent** produces identical output to its Python original
2. **Check handler references** to confirm handlers call Java, not Python/bash
3. **Remove verified Python scripts** from `plugin/scripts/`
4. **Remove bash wrappers** from `plugin/scripts/`
5. **Update any skill files** that reference removed scripts
6. **Run tests:** `mvn -f hooks/pom.xml test`

## Dependencies
- 2.1-port-completion-boxes (for checkpoint/issue-complete/next-task scripts)
- 2.1-port-init-boxes (for init-boxes script)
- 2.1-port-status-display (for status-display script)

## Success Criteria
- [ ] All Java equivalents verified for output parity
- [ ] All obsolete Python scripts removed
- [ ] All bash wrappers removed
- [ ] No remaining Python/bash subprocess spawning for display rendering
- [ ] All tests pass
