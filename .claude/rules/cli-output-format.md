---
paths: ["client/**", "plugin/**"]
---
# CLI Output Format Convention

## Rule: Format Is Determined by the Ultimate Consumer

When designing the return value of a CLI tool (a Java binary invoked from a skill), choose the output
format based on **who ultimately consumes the returned values**:

| Ultimate consumer | Output format |
|-------------------|---------------|
| **Another CLI tool** | JSON |
| **The agent (LLM)** | key=value |

**Why:** The agent cannot reliably parse structured data formats without error-prone bash pattern matching.
key=value output is directly consumable via `while IFS='=' read -r key value; do declare "$key=$value"; done`.
JSON is the right choice when another Java tool will parse it — the Java tool can do this safely and precisely.

## Design Smell: Agent Consuming Nested Data

If the agent is the ultimate consumer of nested or complex data (arrays, maps, multi-field objects), this is
usually a **design problem**. Two remedies:

1. **Chain through a CLI tool:** Add a CLI subcommand that accepts the complex JSON as input, performs the
   lookup or extraction, and returns a scalar result as plain text. The agent calls this subcommand instead
   of parsing JSON directly.

2. **Simplify the return value:** Restructure the output so only scalar values are returned to the agent
   (using key=value), and the complex data is consumed exclusively by downstream CLI tools.

**Wrong — agent directly parses complex JSON:**
```bash
# Agent writes multi-step grep/sed to extract a field from a nested structure
RUN_WORKTREES["${tc_id}"]=$(echo "${WORKTREES_RESULT}" | grep -o "{[^}]*\"tc_id\":\"${tc_id}\"[^}]*}" \
  | grep -o '"runner_worktree":"[^"]*"' \
  | sed 's/"runner_worktree":"\([^"]*\)"/\1/')
```

**Right — agent calls a CLI tool that returns a scalar:**
```bash
# Agent delegates extraction to a Java subcommand; receives a plain scalar string
RUN_WORKTREES["${tc_id}"]=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" \
  get-worktree-field "${WORKTREES_RESULT}" "${tc_id}" "runner_worktree")
```

## Scalar Return Values

When a CLI tool returns a single scalar value (not key=value, not JSON), print the value directly to stdout
with no prefix or wrapping. The caller captures it via `$()`.

```bash
# Returns the string value directly — no "name=..." prefix
ORIGINAL_STEM=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" \
  get-tc-name "${ISOLATION_RESULT}" "${tc_id}")
```
