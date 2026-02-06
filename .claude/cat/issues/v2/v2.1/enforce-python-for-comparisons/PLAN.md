# Plan: enforce-python-for-comparisons

## Goal
Add PreToolUse detection for !=, ==, // operators in Bash commands, warning to use Python instead. Addresses
A017/PATTERN-015 (shell operator escaping). These operators get shell-escaped by the Bash tool, producing incorrect
results.

## Satisfies
None - infrastructure/retrospective action item

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** False positives for legitimate uses of these operators in scripts
- **Mitigation:** Only warn for inline commands, not script file execution

## Files to Modify
- plugin/hooks/bash_handlers/ - New handler file for operator detection
- tests/ - New test file for the handler

## Acceptance Criteria
- [ ] PreToolUse handler detects !=, ==, // in inline Bash commands
- [ ] Warning message suggests using Python instead
- [ ] Legitimate script execution not affected
- [ ] Tests pass
- [ ] No regressions

## Execution Steps
1. **Step 1:** Create PreToolUse bash handler that inspects Bash command input for problematic operators
   - Files: plugin/hooks/bash_handlers/detect_shell_operators.py
2. **Step 2:** Register handler in plugin/hooks/hooks.json
   - Files: plugin/hooks/hooks.json
3. **Step 3:** Create tests for the handler
   - Files: tests/test_detect_shell_operators.py
4. **Step 4:** Run all tests to verify no regressions
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] Handler correctly identifies problematic operators in inline Bash
- [ ] No false positives for script file execution
- [ ] All existing tests continue to pass
