# Plan

## Goal

Increase surefire parallel threadCount to 8 per core to speed up test execution.

## Pre-conditions

(none)

## Post-conditions

- [ ] `<threadCount>8</threadCount>` and `<perCoreThreadCount>true</perCoreThreadCount>` are set in `client/pom.xml` surefire configuration
- [ ] All tests pass with the new thread count (no functionality regression)
- [ ] E2E verification: run full test suite and confirm all 2722+ tests pass with the new parallel configuration
