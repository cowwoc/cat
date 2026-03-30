# Plan

## Goal

Remove unnecessary @SuppressWarnings("try") annotations from test methods that don't explicitly call scope.close() inside try-with-resources blocks. The suppression is only needed when close() is called explicitly inside a try-with-resources block (to suppress the "already auto-closed" compiler warning). Methods that simply use the scope normally in try-with-resources don't generate this warning and don't need the annotation.

## Type

refactor

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] User-visible behavior unchanged
- [ ] Tests passing — all tests compile and pass after annotation removal
- [ ] Code quality improved — unnecessary @SuppressWarnings("try") annotations removed
- [ ] E2E verification — full test suite passes (mvn verify) after changes
