---
mainAgent: true
subAgents: ["cat:work-implement"]
paths: [".cat/config.json", "*.sh"]
---
# Configuration Reads in Worktrees

**MANDATORY:** Agents must read `config.json` from disk **BEFORE** using behavioral configuration values (trust
level, caution level, curiosity, perfection). Branch names and issue paths come from the preparation phase parameters, not
from `config.json`.

**Sources of truth:**

| Value | Source |
|-------|--------|
| `trust`, `caution`, `curiosity`, `perfection` | `.cat/config.json` field values |
| `target_branch`, `issue_id` | Parameters from `work-prepare` phase output |
| Current branch | `git branch --show-current` |
| Worktree path | Parameters from `work-prepare` phase output |

**Rule:** No configuration value may be used without being explicitly read from its authoritative source. Do not use
assumed, hardcoded, or stale values.

**Pattern (CORRECT):**
```bash
# 1. Read behavioral config using the effective config tool (returns JSON with defaults applied)
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
  echo "ERROR: Failed to read effective config" >&2
  exit 1
fi
TRUST=$(echo "$CONFIG" | grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# 2. Use target_branch from parameter (not from config.json)
if [[ -z "${TARGET_BRANCH:-}" ]]; then
  echo "ERROR: target_branch parameter is missing" >&2
  exit 1
fi
git rebase "$TARGET_BRANCH"
```

**Pattern (WRONG):**
```bash
# WRONG: jq is not available in the plugin runtime environment
TRUST=$(jq -r '.trust' .cat/config.json)

# WRONG: Manually parsing config.json (fragile, no defaults for missing entries)
TRUST=$(grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' .cat/config.json \
  | sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# WRONG: Hardcoded value not from any authoritative source
TARGET_BRANCH="main"
git rebase "$TARGET_BRANCH"
```

**Why this matters:**
- Stale in-memory values cause merges to wrong branches and file operations in wrong directories
- Reading config on-demand catches wrong-worktree contexts before data corruption occurs
- `jq` is not available; use `grep`/`sed` for JSON field extraction

See `plugin/concepts/worktree-isolation.md` for detailed verification checklist and worktree context requirements.
