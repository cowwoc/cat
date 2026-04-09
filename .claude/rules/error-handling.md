# Error Handling

- Java: Use exceptions with meaningful messages; catch specific exceptions
- Bash: Use `set -euo pipefail` and trap handlers
- Always provide meaningful error messages
- Log errors with context (what failed, why, how to fix)

## Error Message Content

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

## Fail-Fast Principle

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
  echo "Solution: Run /cat:work-agent to create worktree properly." >&2
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
