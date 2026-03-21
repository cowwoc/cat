# Plan

## Goal

Update the conventions to specify that even closed issues should be migrated when updating
the format of planning files. Update all migration scripts (1.0.8.sh, 1.0.9.sh, 1.0.10.sh,
2.0.sh, 2.1.sh) to process closed issues. Update all relevant convention files to reflect
the new policy.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged (existing open-issue migration continues to work correctly)
- [ ] All migration scripts updated to process both open and closed issues
- [ ] Migration scripts remain idempotent (running twice produces no additional changes)
- [ ] CLAUDE.md and all relevant convention files updated to reflect the new policy
- [ ] Tests passing
- [ ] E2E: Run an updated migration script on a repo with closed issues and verify they are migrated
