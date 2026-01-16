# Plan: test-framework-additions

## Objective
add bats test framework with 66 tests

## Details
Add comprehensive test coverage for critical shell scripts:
- task-lock.sh (21 tests): race conditions, atomic operations, lock lifecycle
- git-squash-optimized.sh (18 tests): squash operations, rollback paths
- validate-git-operations.sh (27 tests): git command validation

Includes test helper with git repo setup, JSON assertion utilities,
and cleanup functions.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
