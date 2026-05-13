# Edit Application

Use `apply_patch` for manual file edits.

Plan independent edits together, but apply manual patches serially. Do not call `apply_patch` in parallel with other
tools, and do not use shell write tricks for ordinary file edits.

Requirements:
- Read each existing file before editing it.
- Group related independent edits into a single patch when practical.
- Apply separate patches serially when edits are easier to review or when later edits depend on earlier results.
- Retry only failed edits; do not re-apply successful edits.

This is guidance only for Codex file editing. It does not bypass project rules, hooks, or worktree restrictions.
