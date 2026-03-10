# Plan: reduce-skillbuilder-context-via-commit-shas

## Current State

The skill-builder-agent Step 3 (benchmark evaluation loop) passes all eval data through the main agent's context window:
subagent run outputs, grading JSONs from skill-grader-agent, the benchmark JSON passed to BenchmarkAggregator, and the
analysis report from skill-analyzer-agent. Each benchmark pass accumulates 200-500KB+ of context in the main agent.

## Target State

Step 3 is refactored so that:
- Each eval-run subagent writes its output to a session-scoped file and commits it, returning only the commit SHA
- The skill-grader-agent subagent reads output from the committed file (via `git show SHA:path`), grades it, writes
  `grading.json`, commits, returns SHA
- The BenchmarkAggregator is invoked by a subagent that reads from committed grading files, aggregates, commits
  `benchmark.json`, returns SHA
- skill-analyzer-agent reads from the committed `benchmark.json` (via `git show SHA:path`), writes analysis, commits,
  returns SHA
- The main agent only receives commit SHAs and the final compact human-readable analysis report (~1KB); no raw JSON
  ever enters main agent context

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** The internal data-flow protocol for Step 3 changes from inline JSON return to commit-SHA
  handoff. No user-visible behavior changes (benchmark summary table and analysis report are still presented to the
  user). skill-grader-agent and skill-analyzer-agent invocation protocols change (they now accept a SHA instead of
  inline JSON/text). BenchmarkAggregator invocation changes (a subagent reads committed grading files and calls it,
  rather than the main agent passing inline JSON).
- **Mitigation:** All changes are internal to Step 3. The external contract of skill-builder-agent (input: skill draft,
  output: benchmark table + analysis report presented to user) is unchanged. The compact analysis report text (the
  human-readable BENCHMARK ANALYSIS REPORT block, ~1KB) still flows through main agent context since it must be
  displayed to the user. Only large JSON blobs are offloaded.

## Rejected Alternatives

### A: Pass grading JSON via environment variables or temp files without git

- **Risk:** HIGH — temp files lack git-based integrity and are not isolated per concurrent CAT session without extra
  bookkeeping. Multi-instance safety requires per-session isolation, which git commits provide automatically via their
  content-addressed SHAs.
- **Rejected because:** Git commits provide immutable, addressable, session-isolated artifacts with no additional
  state management. Temp files need cleanup logic and are race-prone.

### B: Keep inline JSON but compress with base64 or gzip before injection

- **Risk:** MEDIUM — still injects large blobs into context; just encodes them. Doesn't reduce token count.
- **Rejected because:** The goal is context-window reduction, not encoding. Compression doesn't remove tokens from
  context.

### C: Stream results into a single growing JSON array in one file

- **Risk:** MEDIUM — concurrent parallel runs would need locking to avoid write conflicts.
- **Rejected because:** The commit-per-run approach gives each run an independent file with its own SHA. No locking,
  no write conflicts, clean parallel execution.

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` — Rewrite Step 3 to use commit-SHA handoff protocol
- `plugin/agents/skill-grader-agent/SKILL.md` — Update input protocol: accept a SHA+path instead of inline text;
  write `grading.json` to a session-scoped path and commit, returning SHA
- `plugin/agents/skill-analyzer-agent/SKILL.md` — Update input protocol: accept a SHA+path instead of inline
  benchmark JSON; write analysis to a session-scoped path and commit, returning SHA; return only the compact
  human-readable report as return value (not the full analysis file content)

## Session-Scoped Directory Structure

All eval artifacts are written into a session-scoped directory to support concurrent CAT instances. The directory
structure is:

```
${CLAUDE_PROJECT_DIR}/.cat/eval-artifacts/${CLAUDE_SESSION_ID}/
  run-outputs/
    case-1-with-skill.txt
    case-1-without-skill.txt
    case-2-with-skill.txt
    case-2-without-skill.txt
    ...
  grading/
    case-1-with-skill.json
    case-1-without-skill.json
    case-2-with-skill.json
    case-2-without-skill.json
    ...
  benchmark.json
  analysis.txt
```

**Why session-scoped:** Multiple CAT instances may run skill-builder sessions simultaneously, each in its own worktree.
Using `${CLAUDE_SESSION_ID}` as a path component ensures each instance writes to an isolated directory with no risk of
collision or data corruption.

**Why inside CLAUDE_PROJECT_DIR:** Eval artifacts belong to the project repository. Committing them to git (even
ephemerally) enables SHA-based retrieval. The directory is within the worktree so git operations apply to the correct
repository context.

**Cleanup:** The `eval-artifacts/${CLAUDE_SESSION_ID}/` directory and its git-tracked files are intentionally left
after session completion. They serve as an audit trail and are small (typically <100KB per session). A future cleanup
issue may add TTL-based pruning.

## Git Commit Strategy

### Who commits what

| Actor | What they write | Commit message pattern |
|-------|----------------|------------------------|
| Eval-run subagent | `run-outputs/<case-id>-<config>.txt` | `eval: run case-<id> config=<config> [session: <SESSION_ID>]` |
| skill-grader-agent | `grading/<case-id>-<config>.json` | `eval: grade case-<id> config=<config> [session: <SESSION_ID>]` |
| BenchmarkAggregator subagent | `benchmark.json` | `eval: aggregate benchmark [session: <SESSION_ID>]` |
| skill-analyzer-agent | `analysis.txt` | `eval: analyze benchmark [session: <SESSION_ID>]` |

### What each commit contains

Each commit contains exactly one file. This keeps SHAs narrowly scoped: grading subagents can retrieve a specific run
output by SHA without inadvertently reading other artifacts.

### How subagents reference committed files

Subagents retrieve committed content using `git show <SHA>:<relative-path>`. The SHA passed by the main agent is the
commit SHA (not a blob SHA). The relative path is the path within the repository from the worktree root.

Example:
```bash
git show abc1234:eval-artifacts/session-xyz/run-outputs/case-1-with-skill.txt
```

This command runs inside the worktree where the commit was made. The main agent passes both the SHA and the relative
path to the consuming subagent.

### Commit location (worktree branch)

All eval artifact commits are made on the current issue branch (the worktree's branch). They are ephemeral implementation
artifacts, not permanent history — they will be squashed/rebased away when the issue is merged via `cat:work-merge-agent`.

## Compact Summary Format (Flows Through Main Agent Context)

The only data that flows back through main agent context from the benchmark loop is the human-readable benchmark
summary table and the BENCHMARK ANALYSIS REPORT block produced by skill-analyzer-agent.

Combined, these are approximately 30-60 lines (~1KB), well within acceptable main-agent context usage.

Example of what the main agent receives after the full eval loop:

```
BENCHMARK SUMMARY
=================
Config        | Pass Rate | Mean Duration | StdDev Duration | Mean Tokens | StdDev Tokens
------------- | --------- | ------------- | --------------- | ----------- | -------------
with-skill    |   83%     |    4200 ms    |      800 ms     |    1500     |      200
without-skill |   50%     |    3100 ms    |      300 ms     |    1100     |      150
DELTA         |  +33%     |   +1100 ms    |                 |    +400     |

BENCHMARK ANALYSIS REPORT
=========================
...
```

The main agent does NOT receive:
- Individual run output text (can be 5-50KB per run)
- Grading JSON objects (can be 2-10KB each)
- Benchmark aggregation JSON (can be 1-3KB)
- Analysis file content (the compact report IS the analysis output; the file is a backup)

## Pre-conditions

- [ ] All dependent issues are closed
- [ ] refactor-adversarial-tdd-protocol is closed (establishes the git-commit-as-handoff pattern; this issue applies
  the same pattern to Step 3)

## Sub-Agent Waves

### Wave 1: Update skill-grader-agent to commit-SHA protocol

- Update `plugin/agents/skill-grader-agent/SKILL.md`:
  - Change **Inputs** section: replace "Test case output: The full text output produced by the subagent" with "Run
    output SHA+path: A commit SHA and relative file path pointing to the committed run output file. Read the file
    content using `git show <SHA>:<path>` to obtain the test case output."
  - Add new Step 1 (before existing Step 1): "Read the run output from git using `git show <SHA>:<path>`. Store the
    content as the test case output."
  - Renumber all existing steps (current Step 1 becomes Step 2, Step 2 becomes Step 3, Step 3 becomes Step 4).
  - Change the end of (renumbered) Step 4 (Produce Grading JSON): After producing the grading JSON, write it to
    `${EVAL_ARTIFACTS_DIR}/grading/<test_case_id>.json`. Then commit the file with message
    `eval: grade <test_case_id> [session: ${CLAUDE_SESSION_ID}]`. Return the commit SHA as the sole return value.
  - Update **Verification** section: replace "Output is valid JSON with no surrounding prose" with "Commit SHA
    returned as sole output (no JSON in return value)".
  - Files: `plugin/agents/skill-grader-agent/SKILL.md`

### Wave 2: Update skill-analyzer-agent to commit-SHA protocol

- Update `plugin/agents/skill-analyzer-agent/SKILL.md`:
  - Change **Inputs** section: replace "The invoking agent passes a benchmark JSON object" with "The invoking agent
    passes a benchmark SHA+path: a commit SHA and relative file path pointing to the committed `benchmark.json` file.
    Read the benchmark JSON using `git show <SHA>:<path>`."
  - Add new Step 1 (before existing Step 1): "Read the benchmark JSON from git using `git show <SHA>:<path>`. Parse
    the JSON content as the benchmark object."
  - Renumber all existing steps (Step 1 → Step 2, Step 2 → Step 3, Step 3 → Step 4, Step 4 → Step 5).
  - Change the end of (renumbered) Step 5 (Produce Analysis Report): After producing the analysis report text, write
    it to `${EVAL_ARTIFACTS_DIR}/analysis.txt`. Commit the file with message
    `eval: analyze benchmark [session: ${CLAUDE_SESSION_ID}]`. Return the commit SHA AND the full compact analysis
    report text (the human-readable block) as the return value. The compact report text must flow back to the main
    agent; the commit SHA is for audit trail only.
  - Update **Verification** section: add "Compact analysis report text is returned alongside the commit SHA".
  - Files: `plugin/agents/skill-analyzer-agent/SKILL.md`

### Wave 3: Rewrite first-use.md Step 3 (benchmark evaluation loop)

- Update `plugin/skills/skill-builder-agent/first-use.md` Step 3:

  Replace the current Step 3 content with the following redesigned protocol. The section heading stays
  "### Step 3: Benchmark Evaluation Loop". Replace all body content under that heading with:

  **Generate test cases:** Create 2-3 test cases with assertions. Store the eval set JSON in
  `${EVAL_ARTIFACTS_DIR}/eval-set.json` and commit it with message
  `eval: write test cases [session: ${CLAUDE_SESSION_ID}]`. The SHA is not passed forward (the main agent retains
  the eval set JSON in context for writing grader prompts).

  **Spawn parallel eval-run subagents:** For each test case, spawn two subagents simultaneously — one with the skill
  active (`with-skill`) and one with the skill inactive (`without-skill`). Each eval-run subagent:
  1. Executes the test case prompt in its configured environment.
  2. Writes the full output to `${EVAL_ARTIFACTS_DIR}/run-outputs/<case-id>-<config>.txt`.
  3. Commits the file with message `eval: run <case-id> config=<config> [session: ${CLAUDE_SESSION_ID}]`.
  4. Returns: `{"sha": "<commit-sha>", "path": "eval-artifacts/<SESSION_ID>/run-outputs/<case-id>-<config>.txt",
     "duration_ms": <elapsed>, "total_tokens": <count>}`.

  The main agent collects only the small SHA+metadata JSON from each run subagent. It does NOT read the run output
  files.

  **Spawn parallel grader subagents:** For each completed run, spawn a skill-grader-agent subagent. Pass it:
  - The run output SHA+path (from the eval-run subagent return value)
  - The assertions array (from the eval set JSON, already in main agent context)
  - The test case ID and config name
  - The `${EVAL_ARTIFACTS_DIR}` path and `${CLAUDE_SESSION_ID}`

  All grader subagents for a single benchmark pass are spawned in the same turn (parallel). Each grader subagent
  returns a commit SHA for its `grading/<case-id>-<config>.json` file.

  The main agent collects only the commit SHAs from grader subagents. It does NOT read the grading JSON files.

  **Aggregate via BenchmarkAggregator subagent:** Spawn one subagent to perform aggregation. Pass it:
  - All grading file SHAs+paths (the list of SHA+path pairs from the grader subagents)
  - The `${EVAL_ARTIFACTS_DIR}` path and `${CLAUDE_SESSION_ID}`

  This subagent:
  1. Reads each `grading/<case-id>-<config>.json` via `git show <SHA>:<path>`.
  2. Converts each grading JSON to a BenchmarkAggregator input row:
     `{"config": "<config>", "assertions": [<bool array from assertion_results>], "duration_ms": <N>,
     "total_tokens": <N>}`. (Note: duration_ms and total_tokens come from the eval-run return metadata, which must
     be forwarded to this subagent alongside the grading SHAs.)
  3. Invokes the BenchmarkAggregator Java tool with the assembled input array.
  4. Writes the resulting benchmark JSON to `${EVAL_ARTIFACTS_DIR}/benchmark.json`.
  5. Commits the file with message `eval: aggregate benchmark [session: ${CLAUDE_SESSION_ID}]`.
  6. Returns: `{"sha": "<commit-sha>", "path": "eval-artifacts/<SESSION_ID>/benchmark.json",
     "summary_table": "<formatted benchmark table text>"}`.

  The main agent receives the commit SHA, the path, and the pre-formatted benchmark summary table text. It does NOT
  read the benchmark JSON file.

  **Analyze via skill-analyzer-agent subagent:** Spawn skill-analyzer-agent. Pass it the benchmark SHA+path and the
  `${EVAL_ARTIFACTS_DIR}` path. The subagent returns: the analysis commit SHA and the compact analysis report text.

  The main agent receives the analysis report text (~1KB). It does NOT read the analysis file.

  **Display results to user:** Present the benchmark summary table (from the aggregator subagent return) and the
  analysis report text (from skill-analyzer-agent return). Ask the user:
  1. Are there any assertions to remove or replace based on the pattern analysis?
  2. Would you like to improve the skill and re-run the benchmark?
  3. Are you satisfied with the current skill version?

  **Iterate if needed:** If the user requests improvement, apply targeted changes to the skill and return to the
  benchmark loop. Repeat until the user is satisfied or pass rate shows no improvement.

  - Files: `plugin/skills/skill-builder-agent/first-use.md`

## Error Handling

### If an eval-run subagent fails to commit

The eval-run subagent must return a structured error indicator (e.g., `{"error": "commit failed: <reason>"}` instead
of the normal SHA+metadata JSON). The main agent detects missing `sha` field in the return value, logs the failure,
and either:
- Skips the failed run and proceeds with remaining runs (partial benchmark), OR
- Aborts the benchmark pass and reports the failure to the user.

The main agent must NOT attempt to read the run output directly — it must wait for a successful commit or treat the
run as failed.

### If a grader subagent fails to commit

Same pattern: the grader subagent returns `{"error": "..."}`. The main agent treats the affected test case as
ungraded and excludes it from the BenchmarkAggregator input. If all grading for a config fails, the main agent
reports the failure and asks the user whether to retry.

### If BenchmarkAggregator subagent fails

The aggregator subagent returns `{"error": "..."}`. The main agent reports the failure and asks the user whether to
retry from the grading step (using the existing grading SHAs) or start the benchmark loop over.

### If git show fails (stale SHA)

If a subagent calls `git show <SHA>:<path>` and the SHA is not found (e.g., repo was rebased), the subagent returns
`{"error": "git show failed: SHA not found: <SHA>"}`. The main agent reports this and asks the user to restart the
benchmark loop.

### If EVAL_ARTIFACTS_DIR does not exist

Each subagent that writes a file must create the directory with `mkdir -p` before writing. Since multiple subagents
may race to create the same session directory, `mkdir -p` is idempotent and safe for concurrent use.

## EVAL_ARTIFACTS_DIR Variable Definition

The `${EVAL_ARTIFACTS_DIR}` variable used in the skill text resolves to:

```
<worktree-root>/eval-artifacts/${CLAUDE_SESSION_ID}
```

The main agent computes this path at the start of Step 3 and passes it as a literal string to each subagent. The
`${CLAUDE_SESSION_ID}` is expanded by the main agent before injection — subagents receive the fully-resolved path,
not a variable reference.

Example resolved path:
```
/workspace/.claude/cat/worktrees/my-issue/eval-artifacts/abc123def456/
```

## Post-conditions

- [ ] `plugin/agents/skill-grader-agent/SKILL.md` accepts SHA+path input, commits grading JSON, returns commit SHA
- [ ] `plugin/agents/skill-analyzer-agent/SKILL.md` accepts SHA+path input, commits analysis text, returns SHA +
  compact report text
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 3 passes only SHAs between subagent roles; no raw JSON
  enters main agent context
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] User-visible behavior is unchanged: benchmark summary table and analysis report are still presented to the user
  in the same format
- [ ] E2E: Run a skill-builder benchmark pass and verify the main agent context contains only commit SHAs, small
  metadata JSON (`{"sha": "...", "path": "...", "duration_ms": N, "total_tokens": N}`), the formatted benchmark
  summary table (~10 lines), and the compact analysis report (~20 lines) — no raw grading JSON, benchmark JSON,
  or full subagent run outputs
