# Common Conventions

Cross-cutting rules that apply to all CAT development work.

## Terminology: CAT Issues vs Claude TaskList

**CRITICAL DISTINCTION (M400):** Two different "task" systems exist. Never conflate them.

| System | Tool/Location | Purpose | Example |
|--------|---------------|---------|---------|
| **CAT Issues** | `/cat:status`, STATE.md files | Project work items across sessions | `v2.1-compress-skills-md` |
| **Claude TaskList** | `TaskList`, `TaskCreate` tools | Within-session work tracking | "Fix the login bug" |

**When reporting status:**
- CAT issues shown in `/cat:status` are **project issues** (persistent across sessions)
- Claude TaskList items are **session tasks** (exist only within current conversation)
- An empty TaskList does NOT mean "no work in progress" - CAT issues may be active
- A CAT issue "in progress" does NOT mean TaskList will have items

## Language Requirements

| Component Type | Language | Rationale |
|----------------|----------|-----------|
| Plugin logic | **Python** | Rich ecosystem, testability, Claude Code integration |
| CLI tools/hooks | Bash | Claude Code plugin integration, Unix tooling |
| Configuration | JSON | Standard, machine-readable |
| Documentation | Markdown | Human-readable, version-controlled |

### Plugin Code (Python)

Python is used for:
- Hook handlers (PreToolUse, PostToolUse, etc.)
- Skill handlers
- Display formatting and output
- Configuration management
- Test suites

**Python Version:** 3.10+

**Testing Framework:** pytest (with unittest compatibility)

### CLI/Hooks (Bash)

Bash scripts are appropriate for:
- Claude Code hook entry points
- Git operations
- Simple file manipulation
- Environment setup

Bash scripts should NOT contain:
- Complex business logic
- State management beyond simple files

## Code Organization

```
project/
├── plugin/                 # CAT plugin source
│   ├── hooks/              # Hook handlers (Python/Bash)
│   ├── skills/             # Skill definitions (Markdown)
│   ├── commands/           # Command definitions (Markdown)
│   └── scripts/            # Utility scripts
├── tests/                  # Test suites
└── docs/                   # Documentation
```

## Error Handling

- Python: Use exceptions with meaningful messages; catch specific exceptions
- Bash: Use `set -euo pipefail` and trap handlers
- Always provide meaningful error messages
- Log errors with context (what failed, why, how to fix)

### Fail-Fast Principle

**MANDATORY:** Prefer failing immediately with a clear error over using fallback values.

**Why:**
- Silent fallbacks mask configuration errors and can cause catastrophic failures
- Example: Falling back to "main" when the base branch should be "v1.10" causes merges to wrong branch
- Fail-fast errors are easier to debug than mysterious wrong behavior

**Pattern:**
```bash
# ❌ WRONG: Silent fallback
BASE_BRANCH=$(cat "$CONFIG_FILE" 2>/dev/null || echo "main")

# ✅ CORRECT: Fail-fast with clear error
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: Config file not found: $CONFIG_FILE" >&2
  echo "Solution: Run /cat:work to create worktree properly." >&2
  exit 1
fi
BASE_BRANCH=$(cat "$CONFIG_FILE")
```

**When fallbacks ARE acceptable:**
- User-facing defaults (e.g., terminal width defaults to 80)
- Non-critical display settings
- Optional enhancements

**When fallbacks are NOT acceptable:**
- Branch names (wrong branch = wrong merge target)
- File paths (wrong path = data loss or corruption)
- Security settings (wrong default = vulnerability)
- Any value that affects data integrity

## Data Migrations

**Policy:** We do NOT maintain backward compatibility when a migration script updates the data structure.

**Rationale:**
- Backward compatibility code adds complexity and maintenance burden
- CAT is a developer tool where users can re-run migrations easily
- Stale data from old formats should be cleaned up, not silently supported forever

**Migration pattern:**
1. Update all writers to use the new format
2. Update all readers to expect ONLY the new format
3. Provide a migration script to convert existing data
4. Document the change in the issue's PLAN.md

**DO NOT:**
- Add "legacy format" branches in readers
- Keep old writers alongside new writers
- Silently fall back to parsing old formats

## Testing

- Python: pytest for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests
