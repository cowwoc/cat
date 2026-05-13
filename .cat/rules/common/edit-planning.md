# Edit Planning

When 2 or more file edits are independent, plan them as one edit set before modifying files.

Two edits are independent if neither file's content depends on the other edit result. Multiple operations on the same
file are not independent.

Requirements:
- Determine the full set of independent edit targets before applying changes.
- Read each existing file before editing it.
- Apply edits using the active runtime's file-editing mechanism.
- Retry only failed edits; do not re-apply successful edits.

This is guidance only for edit planning. It does not bypass project rules, hooks, or worktree restrictions.
