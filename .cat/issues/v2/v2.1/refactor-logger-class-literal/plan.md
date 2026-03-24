# Plan

## Goal

Refactor LoggerFactory.getLogger to use class literal instead of getClass()

## Type

refactor

## Pre-conditions

(none)

## Post-conditions

- [ ] All LoggerFactory.getLogger(getClass()) calls in client/src/ replaced with LoggerFactory.getLogger(ClassName.class)
- [ ] User-visible behavior unchanged
- [ ] All existing tests pass
- [ ] E2E verification: build succeeds and tests pass after the change
