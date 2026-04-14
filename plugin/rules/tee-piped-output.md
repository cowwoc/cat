---
mainAgent: true
---
## Tee Piped Process Output

**MANDATORY:** When running a Bash command that contains a pipe (`|`), insert `tee` to capture the full output of the
first command in the pipeline to a temporary log file. This allows re-filtering the output later without re-running the
command.

**You MUST demonstrate the complete pattern in your response** — provide the exact `LOG_FILE=$(mktemp)` variable
assignment and the `tee "$LOG_FILE"` command. Do not merely advise the user to save output without showing the
specific commands.

**Rationale:** Piped commands filter output before you see it. If you later need a different filter (a different `grep`
pattern, a different line range), you must re-run the original command. Capturing the pre-pipe output to a log file
lets you `grep`, `head`, `tail`, or otherwise re-filter the results instantly without re-execution.

**Do NOT ask clarifying questions instead of providing the required commands.** When a task requires cleanup (e.g.,
deleting a temporary file), provide the exact `rm -f "$LOG_FILE"` command directly. Do not ask the user for permission
or seek clarification about what to delete.

**Pattern:**

```bash
# 1. Create a temporary log file (MANDATORY: use mktemp, never hardcoded paths)
LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)

# 2. Capture full output before the pipe (MANDATORY: include 2>&1 for stderr)
some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"

# 3. Later, re-filter without re-running the command
grep -i "error" "$LOG_FILE"
tail -50 "$LOG_FILE"
```

**ALL temporary log files MUST use `mktemp`** — never use hardcoded paths like `/tmp/build.log` or
`/tmp/java_files_full.txt`. Hardcoded paths create collisions when multiple instances run concurrently.

**The `LOG_FILE=$(mktemp)` variable assignment is MANDATORY** — do not use inline literal paths in the `tee` command.
The variable assignment allows cleanup and re-filtering steps to reference the correct file.

**Stderr capture via `2>&1` is MANDATORY** — without it, error messages are lost when the command fails. The directive
"capture the full output" means both stdout AND stderr.

**Cleanup:** Delete the log file when you no longer need it. Do not leave temporary log files behind after the task is
complete.

```bash
rm -f "$LOG_FILE"
```

---

## Same-File Input/Output is FORBIDDEN

**FORBIDDEN:** Never use `tee` or shell redirection to write to a file that the same pipeline command reads from.
Both `tee DEST` and `> DEST` open the destination for writing **before the command executes**, truncating it to
zero bytes. When `DEST` is also the source, the command reads an empty file and corrupts the content.

```bash
# FORBIDDEN — truncates STATE_FILE before cmd reads it
cmd "$STATE_FILE" | tee "$STATE_FILE"

# FORBIDDEN — redirect truncates STATE_FILE before cmd reads it
cmd "$STATE_FILE" > "$STATE_FILE"
```

**MANDATORY pattern — temp-file swap:**

```bash
cmd "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE"
```

This applies to any tool that reads a file as input and produces updated output: `update-sprt`,
`jq`, `sed -i` equivalents, and similar update-in-place patterns.

---

## Exception: Batch-Only Output Discarding

**SCOPE:** This exception applies ONLY to one-way batch jobs where output is permanently discarded and never re-used.

**When the exception APPLIES:**
- Output will be **permanently discarded** (never re-read or re-filtered)
- The job is **one-way processing** (no user feedback needed)
- The skill instructions **explicitly state output is final**

**When the exception DOES NOT APPLY:**
- Output will be **analyzed by test assertions** (SPRT, skill tests, any test harness)
- Output will be **consumed by test infrastructure** (graders, validators, assertion engines)
- Output will be **re-piped to other tools** (passed through pipeline stages)
- Output will be **captured for later review** (logged for inspection)
- Output will be **used as input to other processing steps** (downstream consumption)

The exception does NOT apply when output is "discarded to a file" that is then consumed by test infrastructure. From
the consumer's perspective (test harness, grader, validator), the output is NOT permanently discarded — it is used for
verification.

**Output Contract is ALWAYS Binding**

Procedural optimization (skipping tee) does NOT grant autonomy over:
- The skill's interaction model (must be direct, not conversational)
- Output format (must match the documented contract)
- User dialogue (no clarifying questions, no permission-seeking)
- Autonomous decision-making (no overriding stated requirements)

**CORRECT example:**
```
Batch job captures logs to a file that will never be re-read.
The skill instructions say "final output goes to /var/log/run.log".
Skipping tee is a procedural optimization here.
```

**INCORRECT example:**
```
SPRT test harness captures output to a file for assertion analysis.
Agent reasons: "Output is discarded (to file), so I may skip tee."
Agent extends this to: "I may ask clarifying questions instead of direct output."
❌ WRONG — output contract is binding regardless of procedure choice.
```
