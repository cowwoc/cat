<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# SPRT Runner Agent

## Table of Contents

- [Purpose](#purpose)
- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Functions](#functions)
- [SPRT Parameters](#sprt-parameters)
- [API Boundary](#api-boundary)
- [Early Abort on Failure](#early-abort-on-failure)
- [Subagent Command Allowlist](#subagent-command-allowlist)
- [Test Fixture Policy](#test-fixture-policy)
- [Plugin Cache Isolation](#plugin-cache-isolation)
- [Procedure](#procedure)
- [Output Contract](#output-contract)
- [Investigation Procedure](#investigation-procedure)
- [Verification](#verification)

---

## Purpose

Run the full SPRT loop over every `.md` test case in `test_dir`, producing per-test-case
decisions (ACCEPT/REJECT/INCONCLUSIVE) and an overall result (ACCEPT or REJECT).

---

## Quick Start

To run SPRT on a skill's test suite, invoke the skill with three arguments:

```
cat_agent_id  test_dir  worktree_path  test_model
```

Example invocation for a skill with 3 test cases expected to pass within 10–15 minutes:

```
/cat:sprt-runner-agent  plugin/tests/skills/my-skill/  /path/to/worktree  sonnet
```

Expected progression:
1. Isolation branch is created (30–60 seconds)
2. Batch 1 runs 3 test cases in parallel (2–5 minutes per batch)
3. If any test case has not yet crossed a boundary, Batch 2 runs
4. Most compliant skills reach ACCEPT within 2–3 batches (10–15 minutes total)

If `overall_decision` is REJECT, the Investigation Procedure section provides step-by-step guidance.

---

## Prerequisites

- `worktree_path` points to a clean git worktree on the issue branch
- `test_dir` is a path (absolute or relative to `worktree_path`) containing one or more `*.md` test case files
- `test_model` is the model identifier to use for all trial runs (must match the skill under test)
- `${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner` binary is available
- `${CLAUDE_PLUGIN_ROOT}/client/bin/extract-turns` binary is available (splits multi-turn scenarios into individual turn files)

### Valid Model Names

The `test_model` argument accepts full model IDs or short aliases:

| Short alias | Full model ID |
|-------------|---------------|
| `opus` | `claude-opus-4-5` |
| `sonnet` | `claude-sonnet-4-5` |
| `haiku` | `claude-haiku-4-5` |

The `resolve-model` subcommand of `claude-runner` maps short aliases to full IDs. If an unknown alias is passed,
the command fails with a non-zero exit code and an error message identifying the unknown value.

---

## Functions

### derive_overall(decisions{}) → ACCEPT|REJECT|INCONCLUSIVE

Compute overall result from per-TC decisions:

```
if any TC decision is REJECT   → return REJECT
if all TC decisions are ACCEPT → return ACCEPT
otherwise                      → return INCONCLUSIVE
```

---

## SPRT Parameters

- p0 = 0.95 (pass rate under H₀ — skill is compliant)
- p1 = 0.85 (pass rate under H₁ — skill is non-compliant)
- α = 0.05, β = 0.05
- A = log((1 − β) / α) = log(19) ≈ 2.944 (accept boundary)
- B = log(β / (1 − α)) = log(0.0526) ≈ −2.944 (reject boundary)

These constants are defined and Javadoc-documented in `InstructionTestRunner.java` (constants `SPRT_LOG_PASS`,
`SPRT_LOG_FAIL`, `SPRT_ACCEPT`, `SPRT_REJECT`). The values here are the authoritative reference; the Java
implementation is the authoritative source of truth.

**SPRT decision function** (reference — implemented by the Java tools):
```
If observation k is PASS:
  log_ratio += log(p0 / p1)   # log(0.95 / 0.85) ≈ 0.1112
If observation k is FAIL:
  log_ratio += log((1 − p0) / (1 − p1))  # log(0.05 / 0.15) ≈ −1.0986

After each observation:
  if log_ratio >= A → Accept H₀ (compliant, stop testing this case)
  if log_ratio <= B → Reject H₀ (non-compliant, stop testing this case)
  if B < log_ratio < A → Inconclusive (continue testing)
  if runs_for_this_case >= 50 → Truncate: treat as Reject (INCONCLUSIVE truncation)
```

**INCONCLUSIVE truncation:** Test cases that reach 50 runs without crossing either boundary are forced to
REJECT in the final output. This means the test was inconclusive, not that the skill actually failed
compliance. An INCONCLUSIVE truncation indicates the signal was too weak to decide within the trial limit;
consider increasing the trial budget or revising the test scenario for clearer signal.

---

## API Boundary

The `instruction-test-runner` binary exposes two related subcommands for running SPRT:

| Command | Scope | Use case |
|---------|-------|----------|
| `run-full-sprt` | Complete SPRT workflow (public entry point) | Invoked by `sprt-runner-agent` to run a full test suite. Orchestrates all 8 steps: prepare run, create isolation branch, initialize SPRT state, run batches via `run-sprt-batch`, write results, and cleanup. |
| `run-sprt-batch` | Single SPRT batch (internal workhorse) | Called internally by `run-full-sprt` to process one batch of trials. Creates runner worktrees, launches parallel trials, grades outputs, and updates SPRT state. |

**Error contract for `run-full-sprt`:**
- On success: exits with code 0 and writes the structured results report to stdout.
- On error: exits with code 1. The error message is written to stderr, never to stdout. Stdout contains only the valid results report or nothing.
- Progress messages during execution are written to stderr so they do not pollute the stdout report.

---

## Early Abort on Failure

**CRITICAL:** SPRT testing aborts immediately when any test case reaches REJECT.

**Why:** The overall decision is REJECT if any test case fails (per derive_overall). Continuing to test
inconclusive cases wastes resources when the outcome is already determined.

**Abort behavior:**
- After each batch grading, the runner checks if any test case decision is REJECT
- If so, testing stops immediately
- All remaining INCONCLUSIVE test cases are marked as INCONCLUSIVE in the final report
- The runner proceeds to Step 6 (write test results) and Step 7 (cleanup)

**Output on abort:**
```
=== SPRT Aborted: At least one test case REJECT detected ===
Remaining test cases (N) will be marked INCONCLUSIVE.
  tc1: INCONCLUSIVE (aborted after M runs)
  tc4: INCONCLUSIVE (aborted after K runs)
```

---

## Subagent Command Allowlist

All subagents spawned by this skill operate under a strict command allowlist. Deviations are a constraint
violation and must be treated as prohibition failures.

**Test-run subagents** (no tool restrictions):
- Test-run subagents execute organically with full tool access to test natural behavior
- Filesystem isolation (orphan-branch worktree) ensures assertions are structurally absent

**Grader subagents** (no tool restrictions):
- Graders run via `claude-runner` inside the run worktree, which has assertions structurally absent
- Full tool access is permitted; the run worktree isolation is the primary defense

**Isolation model:** Both test runners and graders execute inside run worktrees branched from an orphan
branch where assertions have been structurally removed (see Step 3). This provides filesystem-level
isolation: assertions do not exist on the subagent's disk and cannot be recovered via git commands.
Both components use `--plugin-source "${RUN_WORKTREE}/plugin/"` so they share exactly the same
plugin version — neither depends on `CLAUDE_PLUGIN_ROOT`.

---

## Test Fixture Policy

Test scenario turn content must create and reference fixture files exclusively within `.cat/work/` inside
the run worktree. Do NOT reference paths under `plugin/tests/`, `plugin/`, or any other committed
directory as mutable test state.

**Why:** Committed paths persist across SPRT runs and concurrent sessions. A test run that writes to a
committed path modifies the isolation branch content and corrupts subsequent trials. `.cat/work/` is
gitignored and scoped to each run worktree — writes there are ephemeral and do not cross trial boundaries.

**Correct — fixture created in `.cat/work`:**
```
Create `.cat/work/index.json` with content `{"status":"open"}`, then update it to set `"status": "closed"`.
```

**Incorrect — fixture in committed path:**
```
The index.json file is at: `plugin/tests/skills/work-execute/.../fixtures/index.json`
```

---

## Plugin Cache Isolation

Both test runners and graders are spawned via `claude-runner` with `--plugin-source "${RUN_WORKTREE}/plugin/"`.
This gives both components an isolated config directory containing exactly the plugin version committed
to the run worktree (branched from the sanitized isolation branch, which captured the full working tree
at creation time). Neither test runners nor graders read from `CLAUDE_PLUGIN_ROOT`.

The isolation branch is created from `git add -A`, which commits the full working tree including the
`plugin/` directory. Each run worktree derived from that branch therefore carries the exact plugin
version that was committed — including any uncommitted working-tree changes captured at branch creation time.

The `prepare-trial` binary returns the jlink binary path (`JLINK_BIN`) for the run worktree as part of
its key=value output. Use `JLINK_BIN` from `prepare-trial` for both test runs and graders — do NOT
construct the path manually or apply a fallback. If `prepare-trial` cannot determine a valid jlink path,
it exits non-zero; fail fast rather than silently continuing with a fallback path.

No manual cache sync or `/reload-plugins` is needed.

---

## Procedure

The SPRT workflow is implemented as a single Java CLI command that orchestrates all 8 steps internally.

**Step 0 (if retrying after client updates):** Kill any previous background SPRT instance and clean up artifacts:

```bash
# CRITICAL: Kill stale monitors FIRST. Monitors do NOT auto-stop when the background process dies.
# Failure to kill monitors before restarting creates duplicate monitors that waste resources.
#
# Find monitor PIDs watching SPRT output files (matches: tail -f --pid=<N> .../sprt-output.log):
ps aux | grep -E "tail.*--pid.*sprt-output" | grep -v grep | awk '{print $2}' | \
  xargs -I{} kill {} 2>/dev/null || true

# If you have SPRT_PID from a previous run, kill it
kill ${SPRT_PID} 2>/dev/null || true

# Remove the output file if it exists
rm -f "${OUTPUT_FILE}"

# Clean up previous run worktree artifacts
rm -rf "${WORKTREE_PATH}/.cat/work/test-runs"
rm -f "${WORKTREE_PATH}/.cat/work/sprt-state.json"

# Remove stale SPRT worktrees and branches
git worktree list | grep -E "$(basename ${WORKTREE_PATH})-(tc|sanitized)" | awk '{print $1}' | \
  xargs -I{} git worktree remove {} --force 2>/dev/null || true
git branch | grep -E "$(basename ${WORKTREE_PATH})-(tc|sanitized)" | \
  xargs -I{} git branch -D {} 2>/dev/null || true
```

**Step 1:** Start the SPRT runner in the background and capture its output path:

```bash
CAT_AGENT_ID="$0"
TEST_DIR="$1"
WORKTREE_PATH="$2"
TEST_MODEL="$3"

# Resolve short model name to full model ID
TEST_MODEL_ID=$("${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" resolve-model "${TEST_MODEL}")
if [[ -z "${TEST_MODEL_ID}" ]]; then
  echo "ERROR: Failed to resolve model '${TEST_MODEL}'" >&2
  exit 1
fi

# Start SPRT runner in background using Bash tool run_in_background parameter
# This ensures proper process lifecycle management (no zombie processes)
OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/sprt-output.log"
mkdir -p "${WORKTREE_PATH}/.cat/work"
echo "OUTPUT_FILE=${OUTPUT_FILE}"
```

```
Bash tool:
  description: "Start SPRT runner"
  run_in_background: true
  command: |
    "${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" run-full-sprt \
      "${WORKTREE_PATH}" "${TEST_DIR}" "${TEST_MODEL_ID}" \
      "${CLAUDE_PROJECT_DIR}" "${CLAUDE_SESSION_ID}" \
      > "${OUTPUT_FILE}" 2>&1
```

The run_in_background parameter ensures the harness manages the process lifecycle correctly, preventing zombie processes
when the runner exits abnormally. You will be notified when the background task completes.

**Step 1b:** After starting the background task, capture the SPRT process PID so the monitor can auto-exit:

```bash
# Wait briefly for the java process to start, then capture its PID
sleep 1
SPRT_PID=$(ps aux | grep "instruction-test-runner.*run-full-sprt" | grep -v grep | awk '{print $2}' | head -1)
if [[ -z "${SPRT_PID}" ]]; then
  echo "ERROR: Could not find SPRT process PID" >&2
  exit 1
fi
echo "SPRT_PID=${SPRT_PID}"
```

**Step 2:** Immediately after capturing the PID, use the Monitor tool to stream progress updates:

```
Monitor tool:
  description: "SPRT test progress"
  timeout_ms: 3600000
  persistent: false
  command: tail -f --pid=<SPRT_PID> <OUTPUT_FILE> 2>/dev/null || true
```

Replace `<OUTPUT_FILE>` with the actual file path from Step 1 and `<SPRT_PID>` with the PID from Step 1b.

The `--pid=<SPRT_PID>` flag causes `tail` to exit automatically when the SPRT process (PID) exits, so the monitor
terminates cleanly without manual intervention.

**While monitoring, watch for three failure signals:**

**Signal 1 — Infrastructure failure (`TC{N}: runner failed` or `TC{N}: grader failed`):**

The run worktree is still alive when this message appears. Act immediately — do NOT wait for the batch
to finish:

1. Kill SPRT to prevent worktree cleanup: `kill ${SPRT_PID}`
2. Wait for the monitor to stop
3. Identify the run worktree:
   ```bash
   RUN_WORKTREE="${HOME}/.cat/worktrees/$(basename ${WORKTREE_PATH})-tc{N}-r{M}"
   ls "${RUN_WORKTREE}/.cat/work/" 2>/dev/null || echo "Run worktree missing"
   ```
4. Find the failing component's session. Both the test runner and grader run with `--cwd "${RUN_WORKTREE}"`,
   so their session JSONLs live under the encoded project dir for that path:
   ```bash
   # Encode the run worktree path to the project dir name Claude uses
   ENCODED=$(echo "${RUN_WORKTREE}" | sed 's|^/||; s|/|--|g; s|\.|_|g' | tr '[:upper:]' '[:lower:]')
   ls "/home/node/.config/claude/projects/${ENCODED}/" 2>/dev/null || echo "No session found"
   ```
5. If a session exists, invoke the `cat:get-history-agent` skill on it:
   - Use `session-analyzer errors <session_id>` to surface tool errors
   - Use `session-analyzer search <session_id> "keyword"` to find relevant events
   - For a runner failure: look for what the agent did and why it exited non-zero
   - For a grader failure: look for whether the agent wrote its grade JSON and what errors occurred
6. If no session exists, the process crashed before its first API call (process-level failure).
   Check for OS-level causes: OOM, process timeout, missing binary, or permission error.
7. Report all findings to the user: what failed, what the session shows (or why it's absent),
   and whether this looks like an infrastructure issue or a skill/test defect.
8. Ask: **"Should I delete the TC{N} Run {M} worktree and continue with the SPRT workflow?"**
9. Only delete and restart SPRT after the user confirms.

**Signal 2 — Assertion failures (TC shows same failure in 2+ runs):**

After each batch completes (when you see `=== Batch N: ...` followed by test case results), check for
any test cases with >= 2 runs showing consistent failures. If found, investigate immediately.

Look for patterns like:
- Same assertions failing each time
- Same agent behavior (e.g., always asks questions, always uses hardcoded paths)

**If clear defect pattern found:**
1. Kill SPRT: `kill ${SPRT_PID}`
2. Wait for monitor to stop
3. Read partial results: `cat ${OUTPUT_FILE}`
4. Proceed immediately to Investigation Procedure (see below)

**Why investigate early:** Don't wait for SPRT to accumulate 3+ runs to statistically decide REJECT.
If 2 runs show identical failures with same root cause, that's definitive evidence of a skill/test defect.
Abort and investigate immediately to save resources.

**Step 3:** After the monitor stops (or after investigation if failures occurred), read the final output:

```bash
cat "${OUTPUT_FILE}"
```

Do NOT remove the output file yet - it's needed for investigation if failures occurred.

The SPRT command performs the complete SPRT workflow:

1. **Prepare run** — Validates test directory, resolves paths, initializes state file
2. **Cleanup prior runs** — Removes orphaned SPRT worktrees and branches
3. **Create isolation branch** — Strips assertions, creates opaque TC files, commits to orphan branch
4. **Initialize SPRT** — Sets up per-TC state tracking with configured thresholds
5. **SPRT loop** — Adaptive batching: creates run worktrees, spawns parallel trials via claude-runner, grades outputs, updates SPRT state, repeats until all TCs decided or truncated at 50 runs
6. **Write results** — Commits test-results.json to test directory, returns overall decision
7. **Cleanup** — Removes all run worktrees, branches, and isolation branch
8. **Report** — Outputs structured results table with per-TC decisions and token usage

The CLI command outputs progress messages to stderr during execution and returns the final structured report to stdout.

## Output Contract

The `run-full-sprt` command outputs a structured report to stdout:

1. **Results table** — Markdown table with columns: Test Case, Original File, Decision, Trials, Tokens
2. **`overall_decision:`** line — one of `ACCEPT` or `REJECT` (never `INCONCLUSIVE` — the SPRT loop
   forces all INCONCLUSIVE TCs to REJECT at the 50-run limit)
3. **`TEST_SHA:`** line — the commit SHA of `test-results.json`

Example output:

```
## SPRT Results

| Test Case | Original File          | Decision      | Trials | Tokens  |
|-----------|------------------------|---------------|--------|---------|
| tc1       | test-case-1.md         | ACCEPT        | 3      | 14,800  |
| tc2       | test-case-2.md         | REJECT        | 5      | 22,100  |
| TOTAL     |                        |               | 8      | 36,900  |

overall_decision: REJECT
TEST_SHA: abc123def456...
```

If `overall_decision` is not `ACCEPT` or `REJECT`, treat as failure and stop.

---

## Investigation Procedure

**When overall_decision is REJECT**, investigate each failed test case before reporting to the user.

### Step 1: Read all grade files for REJECT test cases

```bash
cd "${WORKTREE_PATH}/.cat/work/test-runs/${CAT_AGENT_ID}"

# Find all grade files for failed test cases
for TC_DIR in tc*/ ; do
  TC_ID="${TC_DIR%/}"
  # Read the test case decision from sprt-state.json
  DECISION=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" get-json-field \
    "$(cat ${WORKTREE_PATH}/.cat/work/sprt-state.json)" \
    "sprt_state.${TC_ID}.decision")
  
  if [[ "$DECISION" == "REJECT" ]]; then
    echo "=== Investigating ${TC_ID} ==="
    ls ${TC_ID}_run*_grade.json
  fi
done
```

### Step 2: Analyze failure patterns for each REJECT test case

For each failed test case, examine its grade files to identify:

1. **Which assertions failed most frequently?**
   - Read all `tc*_run*_grade.json` files for the test case
   - Count how many times each assertion failed across all runs
   - The most frequently failed assertions reveal the core issue

2. **What was the agent's behavior?**
   - Did the agent ask clarifying questions instead of demonstrating the pattern?
   - Did the agent use hardcoded paths instead of mktemp?
   - Did the agent explain the rule without actually applying it?
   - Did the agent demonstrate the pattern but miss required elements (e.g., cleanup step)?

3. **Is the failure consistent?**
   - Did the test case fail in every run, or only some?
   - If inconsistent, what changed between passing and failing runs?

### Step 3: Classify each failure

For each REJECT test case, determine the root cause:

| Classification | Description | Next Action |
|----------------|-------------|-------------|
| **Skill defect** | Instruction file doesn't specify required behavior clearly enough | Fix instruction file, re-run SPRT |
| **Test defect** | Test scenario or assertions are wrong/unclear | Fix test file, re-run SPRT |
| **Infrastructure defect** | SPRT harness or grading logic has bugs | Fix test infrastructure, re-run SPRT |

### Step 4: Report investigation results

```
## SPRT Investigation Results

**Overall Decision:** REJECT (early abort after batch N)

**Failed Test Cases:** M/K test cases failed

---

### TC2: cleanup-rm-f.md
**Runs:** 3
**Consistent failure:** All 3 runs
**Failure pattern:** Agent asked clarifying questions instead of demonstrating cleanup pattern
**Failed assertions:**
- Assertion 1: "must use rm -f" (3/3 runs)
- Assertion 2: "must include cleanup step" (3/3 runs)

**Root cause:** Skill defect
**Recommendation:** Update instruction file to explicitly require demonstrating the cleanup pattern with concrete example

---

### TC3: find-java-files.md
**Runs:** 3
**Consistent failure:** All 3 runs
**Failure pattern:** Agent found files but didn't capture output with tee
**Failed assertions:**
- Assertion 3: "must use tee to capture output" (3/3 runs)

**Root cause:** Skill defect
**Recommendation:** Add explicit requirement to use tee pattern when listing files

---

**Next Steps:**
1. Fix skill instruction file to address identified defects
2. Re-run SPRT to verify fixes
```

### Step 5: Cleanup monitor and SPRT process

**MANDATORY:** After investigation is complete (or immediately if overall_decision is ACCEPT), clean up the monitor and SPRT process:

```bash
# Kill the monitor (if still running - may have already stopped naturally)
ps aux | grep "tail.*--pid.*${SPRT_PID}" | grep -v grep | awk '{print $2}' | xargs -r kill 2>/dev/null || true

# Kill the SPRT process (if still running - may have already exited naturally)
kill "${SPRT_PID}" 2>/dev/null || true

# Wait briefly for processes to terminate
sleep 1

# Force kill if still alive
kill -9 "${SPRT_PID}" 2>/dev/null || true

# Remove output file
rm -f "${OUTPUT_FILE}"
```

**Why this matters:** SPRT processes can sometimes hang after completing their work. Explicitly killing them ensures clean termination and prevents resource leaks.

---

## Verification

- [ ] Every `*.md` file in `test_dir` appears in the results table
- [ ] Every per-TC decision is ACCEPT or REJECT (all INCONCLUSIVE cases forced to REJECT at 50 runs)
- [ ] `overall_decision` derived correctly per `derive_overall`
- [ ] `overall_decision` is ACCEPT or REJECT (never INCONCLUSIVE in output)
- [ ] All run worktrees and branches removed after trials
- [ ] Sanitized branch deleted after SPRT completes and caller has finished examining failures
- [ ] SPRT state file at `${WORKTREE_PATH}/.cat/work/sprt-state.json` reflects final state
- [ ] `JLINK_BIN` used from `prepare-trial` output — never manually constructed or overridden
- [ ] **If overall_decision is REJECT:** Investigation procedure completed and results reported
