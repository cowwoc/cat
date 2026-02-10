# Plan: hook-sh-fix

## Goal
Fix mutual exclusivity bug in hook.sh where both `-XX:AOTCache` and `-XX:SharedArchiveFile` flags are passed
simultaneously when both files exist. These are mutually exclusive JVM flags; AOTCache subsumes AppCDS.

## Satisfies
Parent: optimize-hook-json-parser (acceptance criterion 4)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Simple conditional logic change
- **Mitigation:** Test that hook.sh still launches handlers correctly

## Files to Modify
- `plugin/hooks/hook.sh` - Change two independent `[[ -f ]]` checks to if/elif so AOTCache takes priority

## Acceptance Criteria
- [ ] hook.sh uses elif for AOTCache vs SharedArchiveFile (mutually exclusive)
- [ ] When both files exist, only AOTCache flag is passed
- [ ] When only SharedArchiveFile exists, it is still used
- [ ] When neither exists, no archive flags are passed

## Execution Steps
1. **Read hook.sh** and locate the two independent `[[ -f ]]` checks for AOTCache and SharedArchiveFile
2. **Change to if/elif:** Make AOTCache the primary check, SharedArchiveFile the elif fallback
3. **Verify** the script still works by examining the logic flow

## Success Criteria
- [ ] hook.sh uses elif for AOTCache/SharedArchiveFile flags
