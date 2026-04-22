---
paths: ["client/**", "plugin/**"]
---
# CAT-Owned Data Structure Conventions

These rules apply to all data structures **owned by CAT**: JSON files, YAML files, key=value output lines,
and any other structured formats written and read by CAT tooling. They do **not** apply to 3rd-party-owned
formats such as Claude Code hook payloads, git output, or external API responses.

## Key Names

Key names use **snake_case** (e.g., `test_case_id`, `log_ratio`, `smoke_runs_done`).

See also: `naming-conventions.md` § JSON Field Names.

## Enum Values

Enum values use **UPPERCASE_SNAKE_CASE** (e.g., `PASS`, `FAIL`, `ACCEPT`, `REJECT`, `INCONCLUSIVE`,
`PRIOR_BOOST`).

**Correct:**
```json
{ "decision": "ACCEPT", "verdict": "PASS" }
```

**Incorrect:**
```json
{ "decision": "accept", "verdict": "pass" }
```

## Case-Sensitive Parsing

Enum values are parsed **case-sensitively**. Readers must not normalize values with `toLowerCase()`,
`toUpperCase()`, or `equalsIgnoreCase()` before comparing. An unrecognized value is an error, not a
silent fallback.

**Correct (Java):**
```java
if (decision.equals("ACCEPT")) { ... }
else if (decision.equals("REJECT")) { ... }
else throw new IllegalArgumentException("Unknown decision: " + decision);
```

**Incorrect (Java):**
```java
if (decision.equalsIgnoreCase("accept")) { ... }
```

## Pretty-Printing

All CAT-owned structured files (JSON, YAML) must be written in **pretty-printed** (indented) form. Compact
single-line output is only acceptable for transient inter-process communication (e.g., a Java tool writing
a result directly to stdout for immediate shell capture). Any file persisted to disk must be indented.

**Correct (JSON on disk):**
```json
{
  "decision": "ACCEPT",
  "runs": 12
}
```

**Incorrect (JSON on disk):**
```json
{"decision":"ACCEPT","runs":12}
```

## Scope Boundary

| Format | Owned by CAT? | Apply these rules? |
|--------|--------------|-------------------|
| SPRT state JSON (`sprt-state.json`) | Yes | Yes |
| Grader output JSON | Yes | Yes |
| Test result JSON (`test-results.json`) | Yes | Yes |
| key=value output lines from Java CLI tools | Yes | Yes |
| CAT YAML frontmatter fields (e.g., `category:`, `effort:`) | Yes | Yes |
| Claude Code hook payloads (`tool_name`, `role`, `type`) | No — Claude Code | No |
| Git porcelain output | No — git | No |
| External API response fields | No — 3rd party | No |
