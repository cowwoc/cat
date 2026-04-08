---
paths: ["*.sh"]
---
## Shell Efficiency

**Chain independent commands** with `&&` in a single Bash call instead of separate tool calls.
This reduces round-trips and primes subagents to work efficiently.

```bash
# Good: single call
git branch --show-current && git log --oneline -3 && git diff --stat

# Bad: 3 separate tool calls for independent checks
```

**Worktree directory safety:** You may `cd` into worktrees to work. However, before removing a directory (via `rm`,
`git worktree remove`, etc.), ensure your shell is NOT inside the directory being removed. See `/cat:safe-rm-agent`.
