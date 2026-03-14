# Common Conventions

Cross-cutting rules that apply to all CAT development work.

## Terminology: CAT Issues vs Claude TaskList

**CRITICAL DISTINCTION:** Two different "task" systems exist. Never conflate them.

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
| Complex business logic | **Java** | Type safety, testability, jlink bundling |
| CLI tools/hooks | Bash | Claude Code plugin integration, Unix tooling |
| Configuration | JSON | Standard, machine-readable |
| Documentation | Markdown | Human-readable, version-controlled |

### Plugin Code (Java)

Java is used for:
- Hook handlers (PreToolUse, PostToolUse, etc.)
- Skill handlers
- Display formatting and output
- Configuration management
- Test suites

**Java Version:** 25+

**Testing Framework:** TestNG with JsonMapper for serialization

### CLI/Hooks (Bash)

Bash scripts are appropriate for:
- Claude Code hook entry points
- Git operations
- Simple file manipulation
- Environment setup
- Complex logic when Java runtime is not yet available (e.g., bootstrap scripts)

Bash scripts should NOT contain:
- Complex business logic (use Java instead)
- State management beyond simple files

### Tool Availability

The plugin's runtime environment provides:

- **Bash** and standard POSIX utilities (`grep`, `sed`, `awk`, `sort`, `find`, `cat`, `head`, `tail`, `cut`, `tr`,
  `wc`, `xargs`, `mktemp`, `date`, etc.)
- **Git**
- **The jlink bundle** (Java tools: `session-analyzer`, `progress-banner`, `verify-audit`, etc.)

Do NOT assume or use tools outside this set. In particular, `jq`, `python`, `python3`, `node`, and `ruby` are **not
available**.

- **JSON parsing:** Use Java (via jlink tools) or Bash pattern matching, not `jq`
- **Complex data processing:** Use jlink-bundled Java tools, not Python scripts
- **Scripting:** Use Bash or Java, not Python

Existing Python scripts are tracked for migration under `migrate-python-to-java`.

## Code Organization

```
project/
├── plugin/                 # CAT plugin source
│   ├── hooks/              # Hook handlers (Java/Bash)
│   ├── skills/             # Skill definitions (Markdown)
│   ├── commands/           # Command definitions (Markdown)
│   └── scripts/            # Utility scripts
├── tests/                  # Test suites
└── docs/                   # Documentation
```

## Multi-Instance Safety

**MANDATORY:** All changes must be safe when multiple Claude instances run concurrently, each in its own isolated worktree.

Instances must NEVER:
- Overwrite each other's files
- Access each other's temporary state
- Share mutable file paths without session/worktree isolation

### Safe Patterns

**Temporary files/directories:**
- ✅ Use `mktemp -d` or `mktemp` for unique per-invocation isolation
- ✅ Store in `/tmp` or per-session directories (e.g., `${CLAUDE_CONFIG_DIR}/projects/.../${CLAUDE_SESSION_ID}/`)
- ❌ Use hardcoded paths like `../repo-clean.git` or `/tmp/shared-work/`
- ❌ Use paths like `${WORKTREE_PARENT}/shared-dir/` (multiple instances share the parent)

**Example: BFG bare clone for history rewriting**
```bash
# WRONG: All instances write to same path
BARE_REPO="../repo-clean.git"
git clone --mirror . "$BARE_REPO"

# CORRECT: Each instance uses unique temp directory
WORK_DIR=$(mktemp -d)
BARE_REPO="${WORK_DIR}/repo-clean.git"
git clone --mirror . "$BARE_REPO"
cd "$BARE_REPO"
# ... do work ...
rm -rf "$WORK_DIR"
```

**Locks and markers:**
- Lock files must include session ID: `${LOCKS_DIR}/${ISSUE_ID}.lock` (not shared)
- Marker files must be per-session: `${SESSION_DIR}/marker-name` (not in worktree/main workspace)
- Use `${CLAUDE_SESSION_ID}` as part of any file path that tracks instance state

**Build artifacts:**
- Worktree builds must NOT write to the main workspace `target/` directory
- Each worktree has its own `${WORKTREE_PATH}/target/` (git-ignored, not shared)
- Main workspace `target/` is read-only from worktrees

**Reading shared state:**
- ✅ Main workspace and shared plugin cache are read-only from worktrees
- ✅ Multiple instances can read the same `.cat/config.json`
- ❌ Do NOT write shared configuration from a worktree (configuration belongs in main workspace only)

### Why This Matters

Worktrees are isolated for a reason: when implementations can run in parallel (multiple users, multiple sessions), shared mutable state causes:
- Race conditions (two instances overwrite the same file)
- Lost work (one instance deletes another's temporary data)
- Mysterious failures (instance A expects a file that instance B deleted)
- Deadlocks (instances waiting for locks on same resources)

The pattern works because:
1. Each instance owns its temporary files (created by `mktemp`)
2. Lock files include session ID (preventing cross-session conflicts)
3. Main workspace is read-only from worktrees (no race conditions)
4. Worktree-local artifacts (build results) don't interfere

## Error Handling

- Java: Use exceptions with meaningful messages; catch specific exceptions
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

**Pattern (Bash):**
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

**Pattern (Java):**
```java
// ❌ WRONG: Silent fallback (returns empty string for invalid format)
if (!SESSION_ID_PATTERN.matcher(value).matches()) {
    log.warn("Invalid session_id format: '{}', treating as empty", value);
    return "";
}

// ✅ CORRECT: Fail-fast with exception
if (!SESSION_ID_PATTERN.matcher(value).matches()) {
    throw new IllegalArgumentException("Invalid session_id format: '" + value +
        "'. Expected UUID format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
}
```

**When fallbacks ARE acceptable:**
- User-facing defaults (e.g., terminal width defaults to 80)
- Non-critical display settings
- Optional enhancements

**When fallbacks are NOT acceptable:**
- Branch names (wrong branch = wrong merge target)
- File paths (wrong path = data loss or corruption)
- Identifiers used to construct file paths (e.g., session IDs used in file path creation)
- Security settings (wrong default = vulnerability)
- Any value that affects data integrity

## Configuration Reads in Worktrees

**MANDATORY:** Agents must read `config.json` from disk **BEFORE** using behavioral configuration values (trust
level, verify level, effort, patience). Branch names and issue paths come from the preparation phase parameters, not
from `config.json`.

**Sources of truth:**

| Value | Source |
|-------|--------|
| `trust`, `verify`, `effort` | `.cat/config.json` field values |
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

## No Backwards Compatibility

**Policy:** Code in terms of the latest design. Do NOT add backwards-compatibility shims, legacy fallbacks, or
dual-format support. When a data structure, file layout, or API changes, add migration logic to `plugin/migrations/`
and write all code against the new design only.

**Rationale:**
- Backward compatibility code adds complexity and maintenance burden
- CAT is a developer tool where users can re-run migrations easily
- Stale data from old formats should be cleaned up, not silently supported forever
- Legacy support obscures the current design and makes future changes harder

**Migration pattern:**
1. Add a migration script to `plugin/migrations/` that converts old data to the new format
2. Update all writers to use the new format
3. Update all readers to expect ONLY the new format
4. Document the change in the issue's PLAN.md

**Idempotency:** Migration scripts MUST be idempotent. Running a migration consecutively must be a no-op on the 2nd+
run. Scripts should check current state before making changes (e.g., skip renaming a file that's already renamed, skip
adding a field that already exists).

**Planning file schema changes:** When an issue modifies the schema of planning files (STATE.md, PLAN.md headings,
field names, section structure), the issue MUST include updating the current version's `plugin/migrations/` script to
transform existing files. The migration is part of the same issue — do not defer it to a separate issue.

**DO NOT:**
- Add "legacy format" branches in readers
- Keep old writers alongside new writers
- Silently fall back to parsing old formats
- Support old file paths or directory structures alongside new ones
- Add compatibility layers that translate between old and new APIs

## MEMORY.md vs Project Conventions

**MEMORY.md is for short-term fixes only** — technical discoveries, workarounds, and session-specific knowledge that
hasn't yet been formalized. When a rule should persist as a project or plugin convention, add it to the appropriate
convention file (see CLAUDE.md § "Convention File Locations"), not MEMORY.md.

| Content Type | Location |
|---|---|
| Short-term workarounds, discoveries | `MEMORY.md` |
| Project development conventions | `.claude/rules/` |
| End-user behavioral rules | `plugin/rules/`, `plugin/` files |

## Documentation Style

**Line wrapping:** Markdown files should wrap at 120 characters.

**No retrospective commentary.** Do not add documentation or comments that discuss:
- What was changed or implemented
- What was removed or refactored
- Historical context of modifications

This applies to all file types, including Java Javadoc, inline comments, and Markdown documentation.

**Example:**
```java
// Bad - describes what was done historically
* <li>{@code {sessionDir}/skills-loaded-*} — legacy flat-file markers (cleaned up for migration)</li>

// Good - describes current behavior
* <li>{@code {sessionDir}/skills-loaded-*} — legacy flat-file markers (deleted when found)</li>
```

**Exception:** Files specifically designed for history tracking (e.g., `CHANGELOG.md`).

**Rationale:** Code and documentation should describe current state and intent, not narrate their own evolution. Git
history provides the authoritative record of changes.

## Pre-existing Problems

**MANDATORY:** When working on an issue, fix pre-existing problems if they fall within the issue's goal, scope, and
post-conditions. Do not dismiss problems as "out of scope" merely because they existed before the current commit.

**Rationale:** The issue's acceptance criteria define what must be true when the issue is closed. If a pre-existing
problem violates those criteria, it must be fixed — regardless of when it was introduced.

**Example:** If an issue's goal is "remove all Python from the project" and pre-existing shell scripts contain inline
`python3` calls, those must be addressed. The fact that they existed before the issue started is irrelevant.

## Recurring Problems After Closed Issues

If a problem recurs after a closed issue was supposed to fix it, the fix was insufficient. Create a new issue to address
the gap — do NOT dismiss it as "already covered" by the closed issue.

**Rationale:** A closed issue means the fix was attempted, not that it succeeded. Recurrence is evidence that the
original fix had an incomplete scope, missed an edge case, or addressed symptoms rather than root cause. The new issue
should reference the closed one and identify what the original fix missed.

## Shell Efficiency

**Chain independent commands** with `&&` in a single Bash call instead of separate tool calls.
This reduces round-trips and primes subagents to work efficiently.

```bash
# Good: single call
git branch --show-current && git log --oneline -3 && git diff --stat

# Bad: 3 separate tool calls for independent checks
```

**Worktree directory safety:** You may `cd` into worktrees to work. However, before removing a directory (via `rm`,
`git worktree remove`, etc.), ensure your shell is NOT inside the directory being removed. See `/cat:safe-rm`.

## Testing

- Java: TestNG for unit tests
- Bash: Bats (Bash Automated Testing System)
- Minimum coverage: 80% for business logic
- All edge cases must have tests

### Test Isolation

Tests must be **self-contained**, **thread-safe**, and must **never impact the production environment**:

1. **No operations against the real repository** — tests must never run git commands against the project's working
   directory, even read-only queries. Use isolated temporary repos. For validation-only tests where execution fails
   before any external operation, this is acceptable since no command actually runs.
2. **No production environment side effects** — tests must not modify files, git state, processes, or configuration
   outside their temporary directories.
3. **Concurrent safety** — multiple test runs, parallel tests, and concurrent Claude instances must not interfere with
   each other or with the host environment. Avoid JVM-global or process-global mutation (e.g., environment variables,
   system properties, stdout/stderr redirection, current working directory).
4. **Deterministic** — test results must not depend on host machine configuration, repository state, or timing. Use
   controlled inputs and injectable dependencies (e.g., `Clock` for time, temp dirs for paths).

**Why:** A leaky test that runs `git reset --soft HEAD~1 && git commit` against the real repo will silently corrupt the
working branch on every build. This is catastrophic when builds happen automatically or in parallel.
