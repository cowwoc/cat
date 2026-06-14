<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# SPRT Runner

## Table of Contents

- [Purpose](#purpose)
- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Functions](#functions)
- [SPRT Parameters](#sprt-parameters)
- [API Boundary](#api-boundary)
- [Early Abort on Failure](#early-abort-on-failure)
- [Agent Command Allowlist](#agent-command-allowlist)
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

**Critical worktree rule:** run SPRT from the issue worktree context, not `/workspace`.
Use the issue worktree root as `worktree_path` and choose `test_dir` inside that same worktree.
Do not pass `/workspace` as `worktree_path`.

---

## Quick Start

To run SPRT on a skill's test suite, invoke the skill with five arguments:

```
test_dir  worktree_path  test_model  test_effort  expected_instruction_sha
```

Example invocation for a skill with 3 test cases expected to pass within 10–15 minutes:

```
/cat:sprt-runner  client/plugin/tests/skills/common/my-skill/  /path/to/worktree  sonnet  medium  abc1234
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
- `worktree_path` MUST NOT be `/workspace`; it must be the issue worktree root for the issue under test
- `test_model` is the model identifier to use for all trial runs. When no instruction frontmatter overrides it,
  use the active engine's default test runner model (`gpt-5.4-mini` for Codex).
- `${CAT_PLUGIN_ROOT}/client/bin/sprt-runner` binary is available
- `${CAT_PLUGIN_ROOT}/client/bin/extract-turns` binary is available (splits multi-turn scenarios into individual turn files)

This SPRT implementation runs on the active CAT engine. Trial and grader process launches are executed through
`cat:spawn-engine` semantics inside `sprt-runner` for the active engine.

### Valid Model Names

The `test_model` argument accepts the model IDs supported by the active engine. Engine-specific aliases may be accepted by the underlying runner; unknown aliases fail fast with an error identifying the unknown value.

---

## Functions

### derive_overall(decisions{}) → ACCEPT|REJECT|INCONCLUSIVE

Compute overall result from per-test-case decisions:

```
if any test case decision is REJECT   → return REJECT
if all test case decisions are ACCEPT → return ACCEPT
otherwise                             → return INCONCLUSIVE
```

---

## SPRT Parameters

- p0 = 0.95 (pass rate under H₀ — skill is compliant)
- p1 = 0.85 (pass rate under H₁ — skill is non-compliant)
- α = 0.05, β = 0.05
- A = log((1 − β) / α) = log(19) ≈ 2.944 (accept boundary)
- B = log(β / (1 − α)) = log(0.0526) ≈ −2.944 (reject boundary)

These constants are defined and Javadoc-documented in `SprtRunner.java` (constants `SPRT_LOG_PASS`,
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

The `sprt-runner` binary exposes one public SPRT entry point for skills and agents:

| Command | Scope | Use case |
|---------|-------|----------|
| `run-sprt` | Complete SPRT workflow | Invoked by `sprt-runner-agent` to run the test suite in the selected test directory. Orchestrates prepare, isolation, batching, grading, state updates, results, and cleanup. |
| `run-status` | Whole-run status snapshot and event deltas | Canonical monitoring surface for in-flight SPRT runs. Reads `.cat/work/sprt-run-status.json` and `.cat/work/sprt-run-events.jsonl`, optionally waiting for new events before returning. |

`run-sprt` usage:

```bash
sprt-runner run-sprt \
<worktree_path> <test_dir> <test_model> <effort>
```

Parameter types:
- `worktree_path`: path to git worktree under test
- `test_dir`: directory containing `*.md` test cases
- `test_model`: model alias/id (e.g., `haiku`, `sonnet`, `opus`)
- `effort`: required test-runner effort level (`low|medium|high|xhigh|max`)

**Error contract for `run-sprt`:**
- On success: exits with code 0 and writes the structured results report to stdout.
- On error: exits with code 1. The error message is written to stderr, never to stdout. Stdout contains only the valid results report or nothing.
- Progress messages during execution are written to stderr so they do not pollute the stdout report.

`run-status` usage:

```bash
sprt-runner run-status \
<worktree_path> [--since-seq N] [--wait-seconds N] [--json|--summary]
```

Parameter types:
- `worktree_path`: worktree under test
- `--since-seq N`: optional lower bound for returned event sequence numbers
- `--wait-seconds N`: optional bounded wait for new events before returning
- `--json|--summary`: JSON is the default; `--summary` is a compact human-readable view

**Contract for `run-status`:**
- Use this as the only normal monitoring interface for an active SPRT run.
- `status` is telemetry only. Formal pass/fail results still come from `test-results.json`.
- The JSON response contains:
  - `status`: whole-run snapshot (`status`, `phase`, `batch`, `undecided_count`, `decided_count`, `cumulative_failures`, `last_event_seq`, `test_results_path`, `overall_decision`, `error`, etc.)
  - `events`: append-only event deltas with `seq`, `timestamp`, `type`, `phase`, and `message`
- Repeated polling with `--since-seq <last_event_seq>` returns only newly appended events.
- `--wait-seconds` performs bounded long-polling and is preferred over tight empty polling loops.

---

## Early Abort on Failure

SPRT testing aborts after the current batch when the total failure count across all test cases reaches 2+ within the first 5 batches. This aggregate threshold provides fast feedback without being overly sensitive to a single early failure in one test case.

## Test Prioritization on Re-Run

When SPRT is re-run after fixing failures, previously-failed tests execute first.

**Why:** Fast feedback on whether fixes resolved the issues. No need to wait for all tests
to complete before seeing if known failures are fixed.

**Implementation:** The SPRT state file (`sprt-state.json`) tracks which test cases triggered
early failure detection. On subsequent runs, these test IDs are sorted to the front of the
execution queue.

---

## Agent Command Allowlist

All agents spawned by this skill operate under a strict command allowlist. Deviations are a constraint
violation and must be treated as prohibition failures.

**Test-run agents** (no tool restrictions):
- Test-run agents execute organically with full tool access to test natural behavior
- Filesystem isolation (orphan-branch worktree) ensures assertions are structurally absent

**Grader agents** (no tool restrictions):
- Graders run through the active engine inside the run worktree, which has assertions structurally absent
- Full tool access is permitted; the run worktree isolation is the primary defense

**Isolation model:** Both test runners and graders execute inside run worktrees branched from an orphan
branch where assertions have been structurally removed (see Step 3). This provides filesystem-level
isolation: assertions do not exist on the agent's disk and cannot be recovered via git commands.
Both components share exactly the same plugin version from the run worktree; neither depends on `CAT_PLUGIN_ROOT`.

---

## Test Fixture Policy

Test scenario turn content must create and reference fixture files exclusively within `.cat/work/` inside
the run worktree. Do NOT reference paths under `client/plugin/tests/`, `client/plugin/`, or any other committed
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
The index.json file is at: `client/plugin/tests/skills/common/work-execute/.../fixtures/index.json`
```

---

## Plugin Cache Isolation

Both test runners and graders are spawned through `cat:spawn-engine` semantics on the active engine with the run
worktree as their plugin source.
This gives both components an isolated config directory containing exactly the plugin version committed
to the run worktree (branched from the isolation branch, which captured the full working tree
at creation time). Neither test runners nor graders read from `CAT_PLUGIN_ROOT`.

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

The SPRT workflow is executed via `sprt-runner run-sprt` with effort immediately after the model id.

**Step 0 (if retrying after client updates):** Stop any still-running background SPRT task in the harness, then clean up stale artifacts:

```bash
# Remove prior status and log artifacts
rm -f "${WORKTREE_PATH}/.cat/work/sprt-run-status.json"
rm -f "${WORKTREE_PATH}/.cat/work/sprt-run-events.jsonl"
rm -f "${RESULT_FILE}"
rm -f "${RUN_LOG_FILE}"

# Clean up previous run worktree artifacts
rm -rf "${WORKTREE_PATH}/.cat/work/test-runs"
rm -f "${WORKTREE_PATH}/.cat/work/sprt-state.json"

# Remove stale SPRT worktrees and branches
git worktree list | grep -E "$(basename ${WORKTREE_PATH})-(tc|isolation)" | awk '{print $1}' | \
  xargs -I{} git worktree remove {} --force 2>/dev/null || true
git branch | grep -E "$(basename ${WORKTREE_PATH})-(tc|isolation)" | \
  xargs -I{} git branch -D {} 2>/dev/null || true
```

**Step 1:** Start the SPRT runner in the background and define the status/log artifact paths:

```bash
TEST_DIR="$0"
WORKTREE_PATH="$1"
TEST_MODEL="$2"
TEST_EFFORT="$3"
EXPECTED_INSTRUCTION_SHA="$4"
TEST_RUN_ID="$(basename "${TEST_DIR}")-$(date +%Y%m%d%H%M%S)"

# Use the model id or alias accepted by the active engine.
TEST_MODEL_ID="${TEST_MODEL}"
SPRT_RUNTIME="${CAT_ENGINE:-}"
if [[ -z "${SPRT_RUNTIME}" ]]; then
  echo "ERROR: CAT_ENGINE must be set to the active engine (claude or codex)" >&2
  exit 1
fi

# Start SPRT runner in background using Bash tool run_in_background parameter.
# The harness owns the process lifecycle; monitor it through run-status, not via ps/tail.
RESULT_FILE="${WORKTREE_PATH}/.cat/work/sprt-results.json"
RUN_LOG_FILE="${WORKTREE_PATH}/.cat/work/sprt-runner.stderr.log"
STATUS_FILE="${WORKTREE_PATH}/.cat/work/sprt-run-status.json"
EVENTS_FILE="${WORKTREE_PATH}/.cat/work/sprt-run-events.jsonl"
mkdir -p "${WORKTREE_PATH}/.cat/work"
echo "RESULT_FILE=${RESULT_FILE}"
echo "RUN_LOG_FILE=${RUN_LOG_FILE}"
echo "STATUS_FILE=${STATUS_FILE}"
echo "EVENTS_FILE=${EVENTS_FILE}"
```

```
Bash tool:
  description: "Start SPRT runner"
  run_in_background: true
  command: |
    "${WORKTREE_PATH}/client/distribution/target/jlink/${SPRT_RUNTIME}/bin/sprt-runner" run-sprt \
      "${WORKTREE_PATH}" "${TEST_DIR}" "${TEST_MODEL_ID}" \
      "${TEST_EFFORT}" \
      > "${RESULT_FILE}" 2> "${RUN_LOG_FILE}"
```

The `run_in_background` parameter ensures the harness manages the process lifecycle correctly. You will be
notified when the background task completes.

**Step 1b:** Initialize incremental status polling state:

```bash
LAST_EVENT_SEQ=0
WAIT_SECONDS=60
```

**Step 2:** Monitor the run exclusively through `run-status`:

Run the following command, then repeat it until the returned `status.status` becomes terminal
(`COMPLETED`, `FAILED`, `ABORTED`, `UNKNOWN`, or `STALE`):

```bash
"${WORKTREE_PATH}/client/distribution/target/jlink/${SPRT_RUNTIME}/bin/sprt-runner" run-status \
  "${WORKTREE_PATH}" \
  --since-seq "${LAST_EVENT_SEQ}" \
  --wait-seconds "${WAIT_SECONDS}" \
  --json
```

After each response:
- Read `status.last_event_seq` and assign it back to `LAST_EVENT_SEQ`.
- Summarize only the newly returned `events`; do not reread the whole run from disk.
- Use the snapshot fields (`phase`, `batch`, `undecided_count`, `decided_count`, `cumulative_failures`,
  `overall_decision`, `error`) as the source of truth for your progress summary.
- If you need a compact human-readable view, rerun the same command with `--summary`.
- If one poll returns no new events and the snapshot is still `RUNNING`, increase `WAIT_SECONDS` from `60` to `120`,
  then to `300` on later empty polls. Reset `WAIT_SECONDS=60` after any response that includes new events.

The runner automatically checks for failures after each of the first 5 batches and stops early if
2+ total failures are detected across all test cases.

**Progress response format:** Each time `run-status` returns one or more new events, respond with a one-line progress
summary in this format:

```
**Batch B** | <latest event message> | <N> TCs remaining | <status>/<phase>
```

Populate:
- `B`: `status.batch` when present, otherwise `-`
- `<latest event message>`: the final message in the returned `events` array, or the snapshot phase transition
- `<N> TCs remaining`: `status.undecided_count` when present
- `<status>/<phase>`: the returned snapshot status and phase

When a poll returns no new events and the snapshot is still `RUNNING`, do not spam the user. Simply continue
long-polling with the current `WAIT_SECONDS` value and only report again after new events arrive or the snapshot
becomes terminal.

**While monitoring, watch for three failure signals from `status` or newly returned `events`:**

**Signal 1 — Infrastructure failure (`tc{N}: runner failed` or `tc{N}: grader failed`):**

The run worktree is still alive when this message appears. Act immediately — do NOT wait for the batch
to finish:

1. Stop the background SPRT task in the harness to prevent worktree cleanup.
2. Re-run `run-status` once without `--wait-seconds` to capture the latest snapshot.
3. Identify the run worktree:
   ```bash
   RUN_WORKTREE="${WORKTREE_PATH}/.cat/work/worktrees/$(basename ${WORKTREE_PATH})-tc{N}-r{M}"
   ls "${RUN_WORKTREE}/.cat/work/" 2>/dev/null || echo "Run worktree missing"
   ```
4. Find the failing component's session or process output. Engine sessions, when available, are scoped to
   the run worktree config under `${RUN_WORKTREE}/.cat/config`; otherwise inspect the captured runner and grader
   stdout files in `${RUN_WORKTREE}/.cat/work/`.
5. If a session exists, invoke the `cat:get-history` skill on it:
   - Use `session-analyzer --engine ${SPRT_RUNTIME} errors <session_id>` to surface tool errors
   - Use `session-analyzer --engine ${SPRT_RUNTIME} search <session_id> "keyword"` to find relevant events
   - For a runner failure: look for what the agent did and why it exited non-zero
   - For a grader failure: look for whether the agent wrote its grade JSON and what errors occurred
6. If no session exists, the process crashed before its first API call (process-level failure).
   Check for OS-level causes: OOM, process timeout, missing binary, or permission error.
7. Report all findings to the user: what failed, what the session shows (or why it's absent),
   and whether this looks like an infrastructure issue or a skill/test defect.
8. Ask: **"Should I delete the tc{N} Run {M} worktree and continue with the SPRT workflow?"**
9. Only delete and restart SPRT after the user confirms.

**Signal 2 — Assertion failures (test case shows same failure in 2+ runs):**

After each `batch_summary` event, inspect the current `RESULT_FILE` if you need per-test-case detail, and
check for any test cases with >= 2 runs showing consistent failures. If found, investigate immediately.

Look for patterns like:
- Same assertions failing each time
- Same agent behavior (e.g., always asks questions, always uses hardcoded paths)

**If clear defect pattern found:**
1. Stop the background SPRT task in the harness.
2. Re-run `run-status` once without `--wait-seconds` to capture the latest snapshot.
3. Read partial results: `cat "${RESULT_FILE}" 2>/dev/null || true`
4. Proceed immediately to Investigation Procedure (see below)

**Why investigate early:** Don't wait for SPRT to accumulate 3+ runs to statistically decide REJECT.
If 2 runs show identical failures with same root cause, that's definitive evidence of a skill/test defect.
Abort and investigate immediately to save resources.

**Signal 3 — Infrastructure errors (JSON parsing, schema validation, Java exceptions):**

When `run-status` or the captured stderr log shows errors like:
- "Pipeline for tc{N} failed"
- "Grade file missing assertion_results or assertions field"
- Java stack traces (IOException, IllegalArgumentException, etc.)
- "{"status":"ERROR","message":"..."}"

**These indicate infrastructure failures that require investigation:**

1. **Do NOT wait for SPRT to complete** — stop the background SPRT task in the harness immediately.
2. Re-run `run-status` once without `--wait-seconds` to capture the latest snapshot.
3. Read the output: `cat "${RUN_LOG_FILE}" 2>/dev/null || true`
4. Identify the failing test case and run number from the error message
5. Check if grade files and run artifacts still exist:
   ```bash
   TEST_RUNS_DIR="${WORKTREE_PATH}/.cat/work/test-runs/${TEST_RUN_ID}"
   ls -la "${TEST_RUNS_DIR}"/tc*_run*_grade.json
   ls -la "${TEST_RUNS_DIR}"/tc*_run*.json
   ```
6. **CRITICAL:** If SPRT was killed before cleanup, artifacts are preserved. If SPRT completed, artifacts are gone.
7. Investigate the root cause using available artifacts (grade files, transcripts, prompts)
8. Report findings and ask user whether to proceed with investigation or fix and re-run

**Step 3:** Once `run-status` reaches a terminal state (or after investigation if failures occurred), read the final output:

```bash
cat "${RESULT_FILE}"
```

**CRITICAL CHECK:** Before doing anything else, inspect the terminal snapshot and stderr log for infrastructure errors:

```bash
FINAL_STATUS_JSON=$("${WORKTREE_PATH}/client/distribution/target/jlink/${SPRT_RUNTIME}/bin/sprt-runner" run-status \
  "${WORKTREE_PATH}" --json)
if grep -q "Pipeline for.*failed\|Grade file missing\|IOException\|ERROR" "${RUN_LOG_FILE}" || \
   printf '%s' "${FINAL_STATUS_JSON}" | grep -q '"status":"FAILED"\|"status":"UNKNOWN"\|"phase":"ERROR"'; then
  echo "⚠️  INFRASTRUCTURE ERROR DETECTED"
  echo "Artifacts may have been cleaned up already if SPRT completed."
  echo "Check if test-runs directory still exists:"
  ls -la "${WORKTREE_PATH}/.cat/work/test-runs/${TEST_RUN_ID}/" 2>/dev/null || echo "Already cleaned up"
  echo ""
  echo "Next: Investigate the error using Investigation Procedure below."
  echo "Do NOT proceed with normal workflow until investigation is complete."
fi
```

Do NOT remove the result or stderr log files yet. They are needed for investigation if failures occurred.

The SPRT command performs the complete SPRT workflow:

1. **Prepare run** — Validates test directory, resolves paths, initializes state file
2. **Cleanup prior runs** — Removes orphaned SPRT worktrees and branches, then clears any existing
   `test-results.json` aggregate entry for the current `[model_id, effort]` tuple before starting
   the new round
3. **Create isolation branch** — Strips assertions, creates opaque test case files, commits to orphan branch
4. **Initialize SPRT** — Sets up per-test-case state tracking with configured thresholds
5. **SPRT loop** — Adaptive batching: creates run worktrees, spawns parallel trials through the active engine, grades outputs, updates SPRT state, repeats until all test cases decided or truncated at 50 runs
6. **Write results** — Commits test-results.json to test directory, returns overall decision
7. **Cleanup** — Removes all run worktrees, branches, and isolation branch
8. **Report** — Outputs structured results table with per-test-case decisions and token usage

The CLI command writes the final structured report to stdout, stderr diagnostics to the stderr log you captured
in Step 1, and whole-run telemetry to the status snapshot/event files consumed by `run-status`.

## Output Contract

The `run-sprt` command outputs a structured report to stdout:

1. **Results table** — Markdown table with columns: Test Case, Original File, Decision, Trials, Tokens
2. **`overall_decision:`** line — one of `ACCEPT` or `REJECT` (never `INCONCLUSIVE` — the SPRT loop
   forces all INCONCLUSIVE test cases to REJECT at the 50-run limit)
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
cd "${WORKTREE_PATH}/.cat/work/test-runs/${TEST_RUN_ID}"

# Find all grade files for failed test cases
for TC_DIR in tc*/ ; do
  TC_ID="${TC_DIR%/}"
  # Read the test case decision from sprt-state.json
  DECISION=$("${CAT_PLUGIN_ROOT}/client/bin/sprt-runner" get-json-field \
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

### Step 5: Cleanup temporary telemetry and captured output

**MANDATORY:** After investigation is complete (or immediately if `overall_decision` is ACCEPT), remove any temporary logs you no longer need:

```bash
rm -f "${RESULT_FILE}"
rm -f "${RUN_LOG_FILE}"
```

**Why this matters:** The harness owns the background process lifecycle. Your responsibility is to avoid stale
status/log artifacts from confusing the next run.

---

## Verification

- [ ] Every `*.md` file in `test_dir` appears in the results table
- [ ] Every per-test-case decision is ACCEPT or REJECT (all INCONCLUSIVE cases forced to REJECT at 50 runs)
- [ ] `overall_decision` derived correctly per `derive_overall`
- [ ] `overall_decision` is ACCEPT or REJECT (never INCONCLUSIVE in output)
- [ ] **If overall_decision is ACCEPT:** All run worktrees and branches removed after trials
- [ ] **If infrastructure errors occurred:** Run worktrees and grade files preserved until investigation complete
- [ ] Sanitized branch deleted ONLY after investigation complete (if failures occurred) or after SPRT completes (if ACCEPT)
- [ ] SPRT state file at `${WORKTREE_PATH}/.cat/work/sprt-state.json` reflects final state
- [ ] `run-status` snapshot at `${WORKTREE_PATH}/.cat/work/sprt-run-status.json` reached a terminal lifecycle state
- [ ] `run-status` event log at `${WORKTREE_PATH}/.cat/work/sprt-run-events.jsonl` recorded monotonic `seq` values
- [ ] `JLINK_BIN` used from `prepare-trial` output — never manually constructed or overridden
- [ ] **If overall_decision is REJECT:** Investigation procedure completed and results reported
