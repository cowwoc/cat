---
mainAgent: true
subAgents: [all]
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

**When NOT to tee:**
- **Commands in `run_in_background`** -- background task output is already captured and retrievable

**Cleanup:** Delete the log file when you no longer need it. Do not leave temporary log files behind after the task is
complete.

```bash
rm -f "$LOG_FILE"
```
