---
mainAgent: true
subAgents: []
---
## Issue Lock Checking
**CRITICAL**: Lock status is managed by the `issue-lock` CLI tool. NEVER probe the filesystem.

**Correct approach** — when asked "Is issue X locked?":
```bash
issue-lock check <issue-id>
```

**Example**:
```bash
issue-lock check 2.1-add-regex-to-session-analyzer
```

**NEVER** use filesystem commands to find locks:
- `ls /workspace/.claude/cat/locks/` — the locks directory does not exist as a browsable path
- `find ... -name "*.lock"` — lock files are not stored in a filesystem-discoverable location

There is no user-accessible lock directory. `issue-lock check` is the first and only step when
querying lock status.
