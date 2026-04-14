---
paths: ["plugin/**", "client/**"]
---
# LLM-to-Java Extraction Policy

**MANDATORY goal:** When adding new features to the plugin or updating existing features, extract as much
logic as possible from LLM skills into Java code. This applies to **all files in the skill's transitive
reference graph**, rooted at `SKILL.md` — including `first-use.md` (loaded by the `SKILL.md` preprocessor
directive), any `.md` file listed in `execution_context`, any referenced concept or rules file, and any
Bash scripts invoked from skill steps.

The aim is to make processes deterministic wherever possible. Only leave in the LLM layer:
- Contextual decision-making that genuinely requires reasoning
- Text output to the terminal

## What belongs in Java

| Logic type | Examples |
|------------|---------|
| File reads and writes | Reading/writing `config.json`; reading turn content from git |
| Data derivation | Mapping questionnaire answers to config values |
| Validation | Checking that a value is within an allowed set |
| Display formatting | Rendering settings boxes, progress banners |
| JSON manipulation | Merging a new value into an existing JSON object |
| Subprocess invocation setup | Constructing paths, prompts, arguments, and output file locations before calling a subprocess (e.g., `claude-runner`). Includes fallback path resolution and deterministic preamble text. |

## What stays in the LLM

| Logic type | Examples |
|------------|---------|
| Presenting menus | `AskUserQuestion` dialogs |
| Navigation between steps | Routing based on user selection |
| Generating novel text | Summaries, explanations, commit messages |
| Contextual reasoning | Choosing an approach based on issue context |

## Pattern

When designing a skill step that reads a file, transforms data, and writes a result:

1. Implement the read-transform-write logic as a Java CLI tool
2. The skill invokes the tool via Bash and passes user input as arguments
3. The tool returns structured output (JSON or plain text) that the skill uses for next-step routing

**Example:** Instead of the LLM reading `config.json`, mentally merging a new value, and writing it back,
a Java CLI tool handles the merge atomically:

```bash
update-config trust=medium caution=high
```

**Why:** LLM-driven file manipulation is error-prone (JSON formatting, dropped keys, hallucinated values)
and wastes tokens on deterministic work. Java handles it correctly every time and is faster.

## Structured Output Contracts

When a skill's output is consumed programmatically by a calling agent, the Java tool must return
structured output so callers do not use `grep | sed` to extract values.

**Wrong — caller parses text output:**
```bash
OVERALL=$(echo "${SKILL_OUTPUT}" | grep -o 'overall_decision:[[:space:]]*[A-Z]*' \
  | sed 's/overall_decision:[[:space:]]*//')
```

### key=value format (scalar outputs, LLM consumer)

When a command returns only scalar values (paths, names, counts, status strings) and the consumer
is an LLM agent (not a Bash script), output `key=value` lines — one per line. The LLM reads the
output and declares the values as variables directly, without any parsing loop.

**Java tool returns:**
```
status=ok
overall_decision=ACCEPT
test_sha=abc123
```

**Skill instruction tells the LLM:**
```bash
# Outputs key=value lines; declare as STATUS, OVERALL_DECISION, TEST_SHA
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" \
  write-test-results "${WORKTREE_PATH}" "${SPRT_STATE_PATH}" "${TEST_DIR}"
```

No while loop, no `get-json-field` call — the LLM is the consumer, so it declares the variables
directly from the command output.

**When to use `key=value`:**
- All output values are scalars (strings, paths, counts, booleans)
- The direct consumer is the LLM agent reading the skill instructions

### JSON format (complex outputs or non-LLM consumers)

Use compact JSON when the output contains arrays, nested objects, or multi-line string values.

**Java tool returns:**
```json
{"output_dir":"/path","worktrees":[{"path":"...","branch":"..."}]}
```

For complex outputs, the LLM extracts individual fields with `get-json-field`:
```bash
OUTPUT_DIR=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" \
  get-json-field "${CREATE_RESULT}" "output_dir")
```

**When to use JSON:**
- Output contains arrays or nested objects (`create-runner-worktrees`, `create-isolation-branch`)
- Output contains multi-line string values (e.g., `prepare-trial` with `turn_content`, `preamble`)

Design the Java tool's return value to include all fields the caller will need.
