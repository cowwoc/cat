# Plan: find-next-task-script

## Goal
Create a shell script that quickly finds the next available task for /cat:work, replacing the
inline file-walking logic currently embedded in work.md.

## Satisfies
None - infrastructure/performance improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Script must handle all edge cases (locks, dependencies, gates) correctly
- **Mitigation:** Match existing logic in work.md exactly; test with various scenarios

## Files to Modify
- plugin/scripts/find-next-task.sh - New script (create)
- plugin/commands/work.md - Update find_task step to call script

## Acceptance Criteria
- [ ] Script outputs next task info in parseable JSON format
- [ ] Script handles: version filtering, dependency checks, lock checks, gate evaluation
- [ ] work.md find_task step updated to invoke the script
- [ ] Script returns appropriate exit codes (0=found, 1=none available)
- [ ] Skill renders the output it receives from the script (no additional processing)

## Execution Steps
1. **Step 1:** Create find-next-task.sh script
   - Files: plugin/scripts/find-next-task.sh
   - Verify: Script runs and outputs JSON with task path/version

2. **Step 2:** Implement task filtering logic
   - Check STATE.md status (pending/in-progress only)
   - Check dependencies are met
   - Check task is not locked
   - Verify entry gates satisfied
   - Verify: Script correctly filters tasks

3. **Step 3:** Update work.md to use script
   - Files: plugin/commands/work.md
   - Verify: /cat:work uses script output instead of inline logic
