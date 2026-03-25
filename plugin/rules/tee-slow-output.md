---
mainAgent: true
subAgents: [all]
---
## Tee Slow Process Output

**MANDATORY:** When running a slow command (build, test suite, long-running script), capture its output with `tee` so
you can re-filter the output later without re-running the command.

**Rationale:** Slow commands can take minutes to complete. If you need to search the output for a different pattern or
review a different section, re-running the command wastes significant time. Capturing the output to a log file lets you
`grep`, `head`, `tail`, or otherwise re-filter the results instantly.

**Pattern:**

```bash
# 1. Create a temporary log file
LOG_FILE=$(mktemp /tmp/build-output-XXXXXX.log)

# 2. Run the slow command with tee to capture output
mvn -f client/pom.xml test 2>&1 | tee "$LOG_FILE"

# 3. Later, re-filter without re-running the command
grep -i "error" "$LOG_FILE"
grep "FAILED" "$LOG_FILE"
tail -50 "$LOG_FILE"
```

**When NOT to tee:**
- **Fast commands** (< 2 seconds) — the overhead of managing a log file outweighs the benefit
- **Trivially small output** — if the full output fits in context, there is nothing to re-filter
- **Commands in `run_in_background`** — background task output is already captured and retrievable

**Cleanup:** Delete the log file when you no longer need it. Do not leave temporary log files behind after the task is
complete.

```bash
rm -f "$LOG_FILE"
```
