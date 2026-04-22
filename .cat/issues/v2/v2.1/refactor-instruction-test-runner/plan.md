# Plan

## Goal

Refactor InstructionTestRunner.java (currently 4051 lines) into focused helper classes so that no single file exceeds 1500 lines. The class handles SPRT orchestration, isolation branch management, trial preparation, batch execution, grading, and result reporting — each of these cohesive areas should become its own class.

## Pre-conditions

(none)

## Post-conditions

- [ ] InstructionTestRunner.java is ≤1500 lines
- [ ] No extracted helper class exceeds 1500 lines
- [ ] All existing tests pass after refactor
- [ ] Public API (CLI subcommands: run-full-sprt, run-single-test, run-sprt-batch, prepare-trial, etc.) is unchanged
- [ ] No behavioral changes — refactor only
- [ ] E2E: SPRT runs correctly against instruction-grader-agent tests after refactor
