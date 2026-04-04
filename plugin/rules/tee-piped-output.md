---
mainAgent: true
---
## Tee Piped Process Output

**MANDATORY:** When running a Bash command that contains a pipe (`|`), insert `tee` to capture the full output of the
first command in the pipeline to a temporary log file. This allows re-filtering the output later without re-running the
command.

**Rationale:** Piped commands filter output before you see it. If you later need a different filter (a different `grep`
pattern, a different line range), you must re-run the original command. Capturing the pre-pipe output to a log file
lets you `grep`, `head`, `tail`, or otherwise re-filter the results instantly without re-execution.

**Pattern:**

```bash
# 1. Create a temporary log file
LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)

# 2. Capture full output before the pipe
some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"

# 3. Later, re-filter without re-running the command
grep -i "error" "$LOG_FILE"
tail -50 "$LOG_FILE"
```

**Cleanup:** Delete the log file when you no longer need it. Do not leave temporary log files behind after the task is
complete.

```bash
rm -f "$LOG_FILE"
```

---

## Exception: Batch-Only Output Discarding

**SCOPE:** This exception applies ONLY to one-way batch jobs where output is permanently discarded and never re-used.

**When the exception APPLIES:**
- Output will be **permanently discarded** (never re-read or re-filtered)
- The job is **one-way processing** (no user feedback needed)
- The skill instructions **explicitly state output is final**

**When the exception DOES NOT APPLY:**
- Output will be **analyzed by test assertions** (SPRT, skill tests)
- Output will be **re-piped to other tools** (passed through pipeline stages)
- Output will be **captured for later review** (logged for inspection)
- Output will be **used as input to other processing steps** (downstream consumption)

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
