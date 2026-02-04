# Bugfix: strengthen-hook-patterns

## Problem

Hook regex patterns fail to match command variations like `git -C /path command`. The M398 mistake documented that a worktree hook pattern did not account for the `-C` flag, allowing operations to bypass validation.

## Implements

- **A014**: Strengthen hook patterns: (1) Account for git -C flag in worktree hooks, (2) Test hook patterns against command variations, (3) Add hook coverage tests

## Satisfies

None (infrastructure/reliability improvement)

## Acceptance Criteria

- [ ] Bug fixed: Hook patterns match git commands with -C flag
- [ ] Regression test added: Test cases for git -C command variations
- [ ] No new issues introduced

## Implementation Notes

1. Identify all hook patterns that match git commands
2. Update regex patterns to handle:
   - `git -C /path command`
   - `git --git-dir=/path command`
   - `git -c config.option command`
3. Add test file with command variations
4. Verify all patterns match expected commands
