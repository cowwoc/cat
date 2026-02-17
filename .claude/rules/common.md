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

### Error Message Content

**MANDATORY:** Error messages must include enough information to reproduce the problem and understand every element of
the message without needing to inspect source code.

**Required elements:**
- **Source location:** Which file (and line number if applicable) triggered the error
- **What failed:** The specific operation or value that caused the problem
- **Context:** All relevant state needed to understand the error elements (e.g., list of valid values, expected format)

**Example — bad:**
```
Error loading skill: Undefined variable ${ARGUMENTS} in skill 'work'.
Not defined in bindings.json and not a built-in variable.
Built-in variables: [CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR]
```
Problem: Which file contains `${ARGUMENTS}`? What does bindings.json contain? Not reproducible without investigation.

**Example — good:**
```
Error invoking skill 'work': SKILL.md:14 references undefined variable ${ARGUMENTS}.
Not a built-in variable or binding.
Built-in variables: [CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR]
bindings.json: {} (empty)
```

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

## Documentation Style

**Line wrapping:** Markdown files should wrap at 120 characters.

**No retrospective commentary.** Do not add documentation or comments that discuss:
- What was changed or implemented
- What was removed or refactored
- Historical context of modifications

**Exception:** Files specifically designed for history tracking (e.g., `CHANGELOG.md`).

**Rationale:** Code and documentation should describe current state and intent, not narrate their own evolution. Git
history provides the authoritative record of changes.

### M-Code References

M-code labels (e.g., `(M088)`, `(M252)`) must not appear in agent-facing documentation (`plugin/skills/`,
`plugin/concepts/`, `plugin/agents/`). These labels consume context tokens without providing value to agents.

Keep M-codes only in:
- `MEMORY.md` — human-readable session reference
- `CLAUDE.md` — project configuration
- `.claude/rules/` — project rules
- `.claude/cat/retrospectives/` — historical record

## Shell Efficiency

**Chain independent commands** with `&&` in a single Bash call instead of separate tool calls.
This reduces round-trips and primes subagents to work efficiently.

```bash
# Good: single call
git branch --show-current && git log --oneline -3 && git diff --stat

# Bad: 3 separate tool calls for independent checks
```

**Worktree directory safety:** You may `cd` into worktrees to work. However, before removing a directory (via `rm`, `git worktree remove`, etc.), ensure your shell is NOT inside the directory being removed. See `/cat:safe-rm`.

## Git Worktree Hygiene (M488)

**Worktrees must stay synchronized with their base branch before squashing.**

When a worktree is created from a base branch (e.g., v2.1), and the base branch advances with new commits, the worktree's tree becomes stale. Squashing commits in a stale worktree can capture old file versions, effectively reverting unrelated changes.

**The git-squash-quick.sh script prevents this by rebasing onto the base branch before squashing.** However, if rebase conflicts occur or the worktree has diverged significantly, explicit awareness helps:

```bash
# Check if worktree is behind its base branch
BASE_BRANCH=$(cat "$(git rev-parse --git-common-dir)/worktrees/$(basename "$PWD")/cat-base" 2>/dev/null)
BEHIND_COUNT=$(git rev-list --count HEAD.."$BASE_BRANCH" 2>/dev/null || echo "0")

if [[ "$BEHIND_COUNT" -gt 0 ]]; then
  echo "INFO: Worktree is $BEHIND_COUNT commits behind $BASE_BRANCH"
  echo "Squash will rebase automatically."
fi
```

**Key points:**
- The squash script handles rebasing automatically (lines 16-55 of git-squash-quick.sh)
- Rebase conflicts cause squash to abort safely with backup branch created
- Manual `git reset --soft` is prohibited (M385) because it captures working directory state

## Testing

- Python: pytest for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests
