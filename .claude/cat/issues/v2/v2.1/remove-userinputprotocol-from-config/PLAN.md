# Plan: Remove userInputProtocol from cat-config.json

## Goal
Remove the `userInputProtocol` field from `cat-config.json`. This field is agent instructions masquerading as
configuration — it is not consumed by any code and duplicates protocol already hardcoded in
`InjectSessionInstructions.java`.

## Satisfies
- None (cleanup)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — field is not read by any code
- **Mitigation:** Grep confirms zero programmatic references

## Files to Modify
- `.claude/cat/cat-config.json` - Remove `userInputProtocol` key

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Remove userInputProtocol:** Delete the `userInputProtocol` key and its value from `.claude/cat/cat-config.json`
2. **Verify no references:** Grep codebase for `userInputProtocol` to confirm no code depends on it

## Post-conditions
- [ ] `userInputProtocol` does not appear in `cat-config.json`
- [ ] No code references `userInputProtocol`
