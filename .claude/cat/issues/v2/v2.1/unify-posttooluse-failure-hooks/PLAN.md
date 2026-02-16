# Plan: unify-posttooluse-failure-hooks

## Goal
Migrate `detect-repeated-failures.sh` (PostToolUseFailure hook) to a Java handler inside the existing dispatcher
framework.

## Current State
`detect-repeated-failures.sh` is registered in hooks.json as a standalone bash script for the PostToolUseFailure hook
type. This hook type was not included in the original unify-hook-dispatchers decomposition.

## Target State
PostToolUseFailure hook handled by a Java dispatcher entry point (hooks/bin binary), with `detect-repeated-failures.sh`
removed.

## Files to Modify
- `plugin/hooks/hooks.json` - Replace bash script registration with Java binary
- `client/src/main/java/io/github/cowwoc/cat/hooks/` - New Java handler class
- `plugin/hooks/detect-repeated-failures.sh` - Delete after migration

## Acceptance Criteria
- [ ] PostToolUseFailure hook dispatched via Java binary
- [ ] `detect-repeated-failures.sh` removed
- [ ] Behavior preserved exactly (failure counting logic identical)
- [ ] Tests cover migrated logic
