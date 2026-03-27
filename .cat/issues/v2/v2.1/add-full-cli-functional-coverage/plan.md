# Plan

## Goal

Achieve 100% functional coverage for all main() CLI entry points — every class with a main() method (except AotTraining) must have tests that invoke run() with every valid combination of command-line arguments and options. Each function involves invoking the CLI using zero or more command-line arguments/options.

## Pre-conditions

(none)

## Post-conditions

- [ ] Every class with a main() method (except AotTraining) has a corresponding *MainTest.java with tests covering every valid CLI argument/option combination
- [ ] Existing MainTest files are audited and enhanced to cover all valid argument combinations, not just error paths
- [ ] Hook entry points (which use HookRunner and read JSON from stdin) have tests that verify the main() → HookRunner → handler pipeline with appropriate stdin input
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] No regressions in existing tests
- [ ] E2E: Run the full test suite and confirm all new and enhanced functional coverage tests execute successfully alongside existing tests
