# State

- **Status:** in-progress
- **Progress:** 0%
- **Dependencies:** []
- **Blocks:** []
- **Last Updated:** 2026-02-11

## Decomposed Into
- 2.1-port-lock-and-worktree
- 2.1-port-standalone-scripts
- 2.1-port-issue-discovery
- 2.1-port-work-prepare

## Parallel Execution Plan

### Wave 1 (Concurrent)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| port-lock-and-worktree | 35K | None |
| port-standalone-scripts | 35K | None |

### Wave 2 (After Wave 1)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| port-issue-discovery | 45K | port-lock-and-worktree |

### Wave 3 (After Wave 2)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| port-work-prepare | 45K | port-lock-and-worktree, port-issue-discovery |

**Total sub-issues:** 4
**Max concurrent subagents:** 2 (in Wave 1)
