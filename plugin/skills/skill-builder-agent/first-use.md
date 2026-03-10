<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Builder

## Purpose

Design or update skills and commands by reasoning backward from the goal to required preconditions,
then converting to forward-execution steps. This skill delegates the design phase to a Task subagent
which reads detailed methodology and conventions from separate files.

---

## When to Use

- Creating a new skill or command
- Updating an existing skill or command that has unclear or failing steps
- Any procedure where the goal is clear but the path is not

**Note:** Both `skills/` and `commands/` are agent-facing prompt files that define behavior.
Use skill-builder for BOTH types.

---

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally (design subagent, red-team, and blue-team).
It CANNOT be invoked by a subagent.

---

## Document Structure: XML vs Markdown

Skills and commands can use either XML-based structure or pure markdown sections.
Choose based on the features needed.

### Use XML Structure When

XML tags (`<objective>`, `<process>`, `<step>`, `<execution_context>`) are required when:

| Feature | XML Syntax | Purpose |
|---------|------------|---------|
| **File references** | `[description](${CLAUDE_PLUGIN_ROOT}/path/file.md)` inside | Reference files for on-demand loading via Read tool |
|                     | `<execution_context>` |  |
| **Named step routing** | `<step name="validate">` with "Continue to step: | Branch between steps based on |
|                         | create" | conditions |
| **Conditional loading** | `<conditional_context>` | Load files only when specific scenarios occur |
| **Complex workflows** | Multiple `<step>` blocks with routing | Multi-phase processes with 10+ steps |

**Example** (command with file references and routing):
```xml
<execution_context>
[CAT work concepts](${CLAUDE_PLUGIN_ROOT}/concepts/work.md)
[merge-subagent skill](${CLAUDE_PLUGIN_ROOT}/skills/merge-subagent/SKILL.md)
</execution_context>

<process>
<step name="validate">
If validation fails, continue to step: error_handler
Otherwise, continue to step: execute
</step>

<step name="execute">
...
</step>
</process>
```

### Use Pure Markdown When

Standard markdown sections (`## Purpose`, `## Procedure`, `## Verification`) are preferred when:

- No file reference expansion needed
- Linear workflow (steps execute in order)
- Simple single-purpose command or skill
- No conditional branching between steps

**Example** (simple skill):
```markdown
## Purpose

Display skill output help content.

---

## Procedure

Output the template content exactly as provided in context.

---

## Verification

- [ ] Content output verbatim
- [ ] No modifications made
```

### Decision Checklist

Before creating a new skill/command, answer:

1. Does it need to load external files? → **XML** (use `<execution_context>`)
2. Does it have conditional step routing? → **XML** (use `<step name="...">`)
3. Does it need conditional file loading? → **XML** (use `<conditional_context>`)
4. Is it a simple linear procedure? → **Markdown** (use `## Purpose/Procedure/Verification`)

**Default**: Use pure markdown unless you need XML-specific features.

---

## Procedure

### Step 1: Collect Existing Skill Content (if updating)

If the caller provides an existing skill path, read the `SKILL.md` and `first-use.md` files from that path
to understand the current state of the skill. Store this as `EXISTING_SKILL_CONTENT` for the design subagent.

If creating a new skill, note that no existing content is available.

### Step 2: Delegate Design Phase to Task Subagent

Invoke the Task tool to delegate the design phase (backward chaining, methodology, conventions) to a
general-purpose subagent. The subagent will read the design methodology and conventions from separate files
and return a complete skill draft.

```
Task tool:
  description: "Design skill: [skill name]"
  subagent_type: "general-purpose"
  prompt: |
    You are a skill design agent. Design or update a CAT skill following the methodology below.

    ## Inputs
    Goal: {GOAL}
    Existing skill content (if updating): {EXISTING_SKILL_CONTENT or "N/A — creating new skill"}

    ## Design Methodology
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/design-methodology.md

    ## Skill Writing Conventions
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/skill-conventions.md

    ## Return Format
    Return the complete designed skill as a markdown code block (the full SKILL.md or first-use.md content).
    Do NOT spawn subagents. Do NOT invoke the Task tool. Do NOT use the Bash, Write, Edit, or any other
    tool that modifies files or executes external commands. The ONLY permitted tools are Read (to read
    design methodology and convention files) and returning your response. Nothing else.
```

The design subagent should only read files and return SKILL_DRAFT. If the response includes Task tool
invocations, evidence of subagent spawning, or use of Bash/Write/Edit/any file-modifying tool, treat as
constraint violation and reject the draft.

The subagent will return the designed skill draft as `SKILL_DRAFT`. Validate that:
- The response is non-empty
- The response is a valid markdown code block
- The content contains Purpose, Procedure, and Verification sections

If the response is empty, not a markdown code block, or missing required sections, reject the draft and
re-invoke the design subagent with clarifying instructions.

### Step 3: Benchmark Evaluation Loop

After receiving the skill draft from the design subagent, run the benchmark evaluation loop to measure
the skill's impact quantitatively.

At the start of Step 3, compute `EVAL_ARTIFACTS_DIR` as `<worktree-root>/eval-artifacts/${CLAUDE_SESSION_ID}`
(expanding `${CLAUDE_SESSION_ID}` to its actual value). Pass this resolved path as a literal string to all
subagents — do NOT pass variable references.

**Context isolation:** `EVAL_ARTIFACTS_DIR` includes the session ID in its path, ensuring that concurrent
eval-run subagents from different sessions write to separate directories and never collide. Each eval-run
subagent receives `EVAL_ARTIFACTS_DIR` and `CLAUDE_SESSION_ID` as pre-resolved literal strings, so no
subagent ever expands these variables independently. Subagents must not derive their own session ID — they
must use the value passed by the main agent.

**Generate test cases:** Create 2-3 test cases with assertions. Store the eval set JSON in
`${EVAL_ARTIFACTS_DIR}/eval-set.json` and commit it with message
`eval: write test cases [session: ${CLAUDE_SESSION_ID}]`. The SHA is not passed forward (the main agent
retains the eval set JSON in context for writing grader prompts).

**Spawn parallel eval-run subagents:** For each test case, spawn two subagents simultaneously — one with
the skill active (`with-skill`) and one with the skill inactive (`without-skill`). Each eval-run subagent:
1. Executes the test case prompt in its configured environment.
2. Creates the output directory with `mkdir -p ${EVAL_ARTIFACTS_DIR}/run-outputs`.
3. Writes the full output to `${EVAL_ARTIFACTS_DIR}/run-outputs/<case-id>-<config>.txt`.
4. Commits the file with message `eval: run <case-id> config=<config> [session: ${CLAUDE_SESSION_ID}]`.
5. Returns: `{"sha": "<commit-sha>", "path": "eval-artifacts/<SESSION_ID>/run-outputs/<case-id>-<config>.txt",
   "duration_ms": <elapsed>, "total_tokens": <count>}`.
   On failure, returns `{"error": "<reason>"}`.

The main agent collects only the small SHA+metadata JSON from each run subagent. It does NOT read the run
output files. If a run subagent returns `{"error": "..."}` (missing `sha` field), log the failure and
either skip the affected run (partial benchmark) or abort and report the failure to the user.

**Spawn parallel grader subagents:** For each completed run, spawn a skill-grader-agent subagent. Pass it:
- The run output SHA+path (from the eval-run subagent return value)
- The assertions array (from the eval set JSON, already in main agent context)
- The test case ID and config name
- The `${EVAL_ARTIFACTS_DIR}` path and `${CLAUDE_SESSION_ID}` (both already resolved to literal strings)

**Grader subagent return contract:** Each grader subagent returns only a commit SHA (a bare hex string with
no JSON wrapper). The grading file path is deterministic: `eval-artifacts/<SESSION_ID>/grading/<case-id>-<config>.json`.
The main agent reconstructs the full SHA+path pair for the aggregator by combining the returned SHA with
this naming convention — it does not ask the grader to return the path.

**Concurrent commit safety:** All grader subagents for a single benchmark pass are spawned in the same turn
(parallel), but they each commit to the same worktree. Git serializes commits internally, so parallel
subagents may briefly contend for the ref lock. If a grader subagent's `git commit` fails with "cannot lock
ref" or similar, it must retry up to 3 times with a short delay before returning `{"error": "commit
failed: <reason>"}`. The main agent does not need to handle this retry — it is the grader's responsibility.

Each grader subagent returns a commit SHA for its `grading/<case-id>-<config>.json` file, or
`{"error": "..."}` on failure. If the `git show` command used to read the run output fails (e.g., SHA not
found, path missing), the grader returns `{"error": "git show failed: <reason>"}` and stops — it does not
produce partial grading. The main agent collects only the commit SHAs from grader subagents. It does NOT
read the grading JSON files. If a grader subagent returns an error, exclude that test case from the
aggregation input; if all grading for a config fails, report the failure and ask the user whether to retry.

**Aggregate via BenchmarkAggregator subagent:** Spawn one subagent to perform aggregation. Pass it:
- All grading file SHAs+paths (the main agent reconstructs each path as
  `eval-artifacts/<SESSION_ID>/grading/<case-id>-<config>.json` and pairs it with the SHA returned by the
  grader subagent)
- The eval-run return metadata for each run (duration_ms, total_tokens), forwarded alongside grading SHAs
- The `${EVAL_ARTIFACTS_DIR}` path and `${CLAUDE_SESSION_ID}`

This subagent:
1. Reads each `grading/<case-id>-<config>.json` via `git show <SHA>:<path>`.
2. Converts each grading JSON to a BenchmarkAggregator input row:
   `{"config": "<config>", "assertions": [<bool array from assertion_results>], "duration_ms": <N>,
   "total_tokens": <N>}`.
3. Invokes the BenchmarkAggregator Java tool with the assembled input array.
4. Creates the output directory with `mkdir -p ${EVAL_ARTIFACTS_DIR}`.
5. Writes the resulting benchmark JSON to `${EVAL_ARTIFACTS_DIR}/benchmark.json`.
6. Commits the file with message `eval: aggregate benchmark [session: ${CLAUDE_SESSION_ID}]`.
7. Returns: `{"sha": "<commit-sha>", "path": "eval-artifacts/<SESSION_ID>/benchmark.json",
   "summary_table": "<formatted benchmark table text>"}`, or `{"error": "<reason>"}` on failure.

The main agent receives the commit SHA, the path, and the pre-formatted benchmark summary table text. It
does NOT read the benchmark JSON file. If the aggregator subagent returns an error, report the failure and
ask the user whether to retry from the grading step (using the existing grading SHAs) or restart the loop.

**Analyze via skill-analyzer-agent subagent:** Spawn skill-analyzer-agent. Pass it the benchmark SHA+path
and the `${EVAL_ARTIFACTS_DIR}` path. The subagent returns the analysis commit SHA and the compact analysis
report text. The main agent receives only the compact analysis report text (~1KB). It does NOT read the
analysis file.

**Display results to user:** Present the benchmark summary table (from the aggregator subagent return) and
the analysis report text (from skill-analyzer-agent return). Ask the user:
1. Are there any assertions to remove or replace based on the pattern analysis?
2. Would you like to improve the skill and re-run the benchmark?
3. Are you satisfied with the current skill version?

**Iterate if needed:** If the user requests improvement, apply targeted changes to the skill and return
to the benchmark loop. Cap at 5 benchmark iterations total. Stop iterating if the improvement between
consecutive rounds is less than 5 percentage points — report "Benchmark plateau reached: pass rate
improvement below 5% threshold" and present the best result to the user. If the iteration cap is reached,
stop and report "Benchmark iteration cap reached (5 rounds) — presenting best result."

### Step 4: Adversarial TDD Loop

After the benchmark phase converges, harden the instructions using alternating red-team and blue-team
subagents. Run until no major loopholes remain or 10 iterations complete.

**Why two separate persistent subagents:**
A single agent playing both roles anchors on its own attack vectors — it knows exactly which loopholes it
invented, so it subconsciously over-fits the defense to those attacks and under-defends against variations.
Separate subagents eliminate this bias. Reusing the same agent across rounds (via `resume`) is more efficient
than spawning fresh agents each round; to counter anchoring risk from reuse, agents are explicitly instructed
to seek new attack vectors each round rather than revisiting prior findings.

**Termination criteria:** No major loopholes = the `loopholes` array in red-team's findings file contains no
CRITICAL or HIGH severity issues. Only arbitration-upheld disputes (findings where the arbitration agent confirms
the blue-team's evidence) are excluded from the CRITICAL/HIGH count. Findings pending arbitration or rejected by
arbitration remain in scope. Check by reading the findings file from the red-team's returned commit hash.
LOW/MEDIUM concerns may remain if they require disproportionate instruction complexity to close.

**findings.json schema:** The findings file contains two top-level arrays:

- `"loopholes"` — active findings to be patched. Each entry has at minimum `"severity"` (one of CRITICAL, HIGH,
  MEDIUM, LOW) and a description field.
- `"disputed"` — findings where the arbitration agent has upheld the blue-team's dispute. Each entry has:
  - `"false_premise"`: what the red-team claimed (the incorrect assumption)
  - `"evidence"`: why the premise is false (verifiable facts, documentation references, or test results)
  - `"arbitration_verdict"`: `"upheld"` (set by the arbitration agent after confirming the evidence)

Only arbitration-upheld findings appear in the `"disputed"` array. Findings rejected by arbitration are moved
back to `"loopholes"` for patching. The `"disputed"` array accumulates across rounds and is never reset.

For a complete traced walkthrough of the dispute mechanism including the arbitration step, see
`plugin/skills/skill-builder-agent/e2e-dispute-trace.md`.

**Termination verification:** Read findings.json using `git show {RED_TEAM_COMMIT_HASH}:findings.json`. Scan only
the `loopholes` array for entries with `"severity": "CRITICAL"` or `"severity": "HIGH"`. Do not count `disputed`
entries — red-team is instructed not to re-raise previously disputed findings, so the `loopholes` array already
reflects only unresolved findings. If no CRITICAL or HIGH entries exist in `loopholes`, STOP — instructions are
sufficiently hardened. If the round counter reaches 10 without convergence, stop and report
"Loop cap reached at 10 rounds — {N} CRITICAL/HIGH loopholes remain unresolved."

**Round 1 — spawn persistent agents:**

Spawn the red-team agent using the Task tool and store its `task_id` as `RED_TEAM_TASK_ID`:

```
Task tool:
  description: "Red-team: find loopholes in instructions (round 1)"
  subagent_type: "cat:red-team-agent"
  prompt: |
    ## Instructions to Attack
    {CURRENT_INSTRUCTIONS}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Round Number
    1
```

After red-team completes, read `RED_TEAM_COMMIT_HASH` from the last line of its response.

**Commit hash validation:** Before using `RED_TEAM_COMMIT_HASH`, verify it is a valid commit on the current
branch: run `git branch --contains {RED_TEAM_COMMIT_HASH}` and confirm the current branch is listed. Also
verify it is newer than the previous round's commit (if any) by running `git log --oneline {PREV_COMMIT}..{RED_TEAM_COMMIT_HASH}`
and confirming at least one entry exists. If either check fails, abort with "ERROR: red-team returned invalid
or stale commit hash {RED_TEAM_COMMIT_HASH}".

Read `findings.json` at that commit and scan the `loopholes` array for any entry with `"severity": "CRITICAL"`
or `"severity": "HIGH"`.

If no CRITICAL or HIGH entries exist: STOP loop — instructions are sufficiently hardened.

Spawn the blue-team agent using the Task tool and store its `task_id` as `BLUE_TEAM_TASK_ID`. The blue-team
MUST start only after red-team completes (it requires the findings file from red-team's commit).

```
Task tool:
  description: "Blue-team: close loopholes in instructions (round 1)"
  subagent_type: "cat:blue-team-agent"
  prompt: |
    ## Current Instructions
    {CURRENT_INSTRUCTIONS}

    ## Red-Team Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Skill File Path
    {SKILL_FILE_PATH}

    ## Round Number
    1

    ## Dispute Protocol
    Before patching any finding, verify its premise. If a finding claims something that is factually incorrect
    (e.g., claims an env var is unavailable when it is documented as available, misrepresents an API's behavior,
    or assumes a file does not exist when it does), do NOT patch it. Instead, move it from the `loopholes` array
    to the `disputed` array in findings.json with two fields:
    - `"false_premise"`: what the red-team claimed (the incorrect assumption)
    - `"evidence"`: why the premise is false (cite specific documentation, env var names, actual API behavior)
    Only patch findings remaining in the `loopholes` array after dispute evaluation. Never patch a finding that
    has been moved to `disputed`. Write the updated findings.json. If any findings were patched, also write the
    revised skill file. Commit all modified files and return only the commit hash.
```

**Error handling:**
- If red-team does not return a commit hash on its last line: log an error and abort the loop.
- If `git show {RED_TEAM_COMMIT_HASH}:findings.json` fails or returns empty: log "ERROR: findings.json not readable from commit {RED_TEAM_COMMIT_HASH}" and abort.
- If findings.json is malformed (not valid JSON or missing `loopholes` array): log the error and abort. Do not default to empty.
- Apply the same commit-hash validation to blue-team: abort if no valid commit hash is returned.

After blue-team completes, read `BLUE_TEAM_COMMIT_HASH` from the last line of its response.

**Arbitration phase:** If blue-team moved any findings to `disputed`, spawn a separate arbitration agent to
independently verify each dispute before it is accepted. The arbitration agent is NOT the red-team or blue-team
agent — it is a fresh general-purpose subagent with no prior context.

Read `findings.json` at `BLUE_TEAM_COMMIT_HASH` and collect all entries in the `"disputed"` array that do not
yet have `"arbitration_verdict": "upheld"`. If there are no such entries, skip arbitration and proceed to diff
validation.

Spawn one arbitration subagent per batch of disputed findings (all disputes from this round may be reviewed in
a single subagent call):

```
Task tool:
  description: "Arbitration: verify blue-team disputes (round {N})"
  subagent_type: "general-purpose"
  prompt: |
    You are an independent arbitration agent. Your role is to verify whether the blue-team's evidence
    actually proves that the red-team's premise is false. You are NOT the red-team or blue-team — do not
    advocate for either side. Evaluate each dispute on the evidence alone.

    ## Disputed Findings to Review
    {JSON array of disputed entries from findings.json at BLUE_TEAM_COMMIT_HASH}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Instructions
    For each disputed finding:
    1. Read the `"false_premise"` field — this is what the red-team claimed.
    2. Read the `"evidence"` field — this is why the blue-team says the premise is false.
    3. Independently verify the evidence. For example:
       - If evidence cites documentation, read that documentation file and confirm the claim matches.
       - If evidence cites an env var being available, check plugin/concepts/skill-loading.md or equivalent.
       - If evidence cites a test assertion, verify the assertion text matches what is claimed.
    4. Return a JSON verdict for each finding:
       - `"upheld"` — the evidence proves the red-team's premise is false; the dispute is valid.
       - `"rejected"` — the evidence does NOT prove the premise false; the finding is legitimate.

    ## Return Format
    Return ONLY a JSON array of verdicts on the last line of your response, with no trailing text:
    [{"finding_id": "...", "verdict": "upheld|rejected", "reasoning": "..."}]

    Do NOT modify any files. Do NOT commit anything. Return only the JSON array.
```

Parse the JSON verdict array from the last line of the arbitration agent's response.

For each verdict:
- If `"verdict": "upheld"`: add `"arbitration_verdict": "upheld"` to that entry in `findings.json` (it remains
  in the `disputed` array and will NOT be patched).
- If `"verdict": "rejected"`: move the entry back from `disputed` to `loopholes` (removing any
  `"false_premise"` and `"evidence"` fields added by blue-team). This finding must be patched by blue-team.

If any disputes were rejected by arbitration, write the updated `findings.json`, commit it with message
`arbitration: reject {N} dispute(s) (round {R})`, and **resume the blue-team agent** with the following prompt:

```
Task tool (resume):
  task_id: {BLUE_TEAM_TASK_ID}
  prompt: |
    Round {N} arbitration rejected the following disputes — the findings were moved back to loopholes:
    {list of rejected finding IDs and reasoning}

    Read the current findings.json at {ARBITRATION_COMMIT_HASH}, patch each finding now in `loopholes`,
    and write the revised skill file. Commit all modified files and return only the commit hash.
```

After blue-team re-patches, update `BLUE_TEAM_COMMIT_HASH` to the new commit. All upheld disputes remain in
the `disputed` array with `"arbitration_verdict": "upheld"`.

Accumulate rejected disputes across rounds in a list `REJECTED_DISPUTES` (finding_id + arbitration reasoning).
Pass this list to the blue-team round 2+ prompt as "Prior Rejected Disputes".

If all disputes were upheld (no rejections), no additional commit is needed — proceed directly to diff
validation with the existing `BLUE_TEAM_COMMIT_HASH`.

**Diff validation:** Delegate diff validation to the persistent diff-validation subagent. On round 1, spawn
it and store its `task_id` as `DIFF_VALIDATION_TASK_ID`. On round 2+, resume it using `task_id`.

Round 1 — spawn:

```
Task tool:
  description: "Diff validation: round 1"
  subagent_type: "cat:diff-validation-agent"
  prompt: |
    ## Diff Command
    git diff {PREV_BLUE_COMMIT_OR_ROUND1_START}..{BLUE_TEAM_COMMIT_HASH} -- {SKILL_FILE_PATH}

    ## Findings Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Skill File Path
    {SKILL_FILE_PATH}

    ## Round Number
    1
```

Round 2+ — resume:

```
Task tool (resume):
  task_id: {DIFF_VALIDATION_TASK_ID}
  prompt: |
    ## Diff Command
    git diff {PREV_BLUE_COMMIT_OR_ROUND1_START}..{BLUE_TEAM_COMMIT_HASH} -- {SKILL_FILE_PATH}

    ## Findings Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Skill File Path
    {SKILL_FILE_PATH}

    ## Round Number
    {N}
```

Parse the JSON result from the diff-validation agent's last line. If `"status": "INVALID"`:

1. Run `git revert {BLUE_TEAM_COMMIT_HASH} --no-edit` to undo the commit.
2. Resume the blue-team agent with: "Your round {N} patch was reverted. The following hunks had no
   correspondence to any listed loophole: {out_of_scope_hunks from validation result}. Rewrite the patch
   touching only the lines required to close each loophole. Do not modify any other content. Commit and
   return the new hash."
3. Re-run diff validation (resume DIFF_VALIDATION_TASK_ID) with the new blue-team commit. If the result
   is still INVALID, log "ERROR: blue-team introduced out-of-scope changes in round {N} after retry —
   aborting loop" and stop. Do not retry more than once per round.

Read the skill file at `BLUE_TEAM_COMMIT_HASH` to update `CURRENT_INSTRUCTIONS`.
Increment round counter. If round < 10: continue to next iteration.

> **Note:** Arbitration-upheld disputes count as closed for round-advancement purposes. The round counter
> increments whether findings were patched, arbitration-upheld, or both. A round where all findings were
> arbitration-upheld (none patched) still advances the counter normally.

**Round 2+ — resume persistent agents:**

In subsequent rounds, `resume` the existing agents by passing `task_id`:

```
Task tool (resume):
  task_id: {RED_TEAM_TASK_ID}
  prompt: |
    Round {N}. Resume your red-team analysis.

    ## What Changed Since Last Round
    {git diff RED_TEAM_COMMIT_HASH..BLUE_TEAM_COMMIT_HASH -- SKILL_FILE_PATH}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Round Number
    {N}

    ## Prior Disputed Findings (do NOT re-raise these)
    {JSON array from the "disputed" field of the previous round's findings.json, or "[]" if none}

    First, analyze the diff above to determine whether the blue-team's patches introduced new gaps or
    failed to fully close prior loopholes. Then, re-examine the FULL current instructions (not just the diff)
    for attack vectors not yet explored in previous rounds — the diff focus must not prevent discovery of
    loopholes present in unchanged sections.
    Do NOT re-raise findings that appear in the Prior Disputed Findings list above — those have been
    rejected with evidence and must not be re-submitted.
    Write new findings to {WORKTREE_ROOT}/findings.json and commit as before, returning only the commit hash.
```

```
Task tool (resume):
  task_id: {BLUE_TEAM_TASK_ID}
  prompt: |
    Round {N}. Resume your blue-team patching.

    ## Red-Team Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Skill File Path
    {SKILL_FILE_PATH}

    ## Round Number
    {N}

    ## Prior Rejected Disputes (do NOT re-dispute these)
    {JSON array of disputes that arbitration rejected in prior rounds, including finding_id, reasoning}

    Do NOT re-dispute findings listed above — arbitration has already reviewed and rejected the evidence.
    These findings must be patched, not disputed again.

    Apply the dispute protocol before patching: for each finding in `loopholes`, verify its premise. If the
    premise is false, move the finding to `disputed` with `"false_premise"` and `"evidence"` fields. Only
    patch findings that remain in `loopholes` after dispute evaluation. Write the updated findings.json. If
    any findings were patched, also write the revised skill file. Commit all modified files and return only
    the commit hash.

    Note: Disputes are subject to arbitration — findings you move to `disputed` will be independently
    verified before they are accepted. If arbitration rejects a dispute, you will be asked to patch it.
```

After the blue-team returns `BLUE_TEAM_COMMIT_HASH` for round 2+, apply the same arbitration phase as in
round 1: spawn an arbitration subagent for any new disputes (entries in `disputed` without
`"arbitration_verdict": "upheld"`), then proceed to diff validation.

### Step 5: In-Place Hardening Mode (Optional)

**BLOCKING — Do NOT implement this loop manually.** Reading this section does not authorize direct
execution of the hardening algorithm. You are NOT the hardening engine — you are the orchestrator.

The ONLY valid execution path is:
- Spawn red-team and blue-team subagents using the **Task tool** as defined in Step 4
- Let the subagents read CURRENT_INSTRUCTIONS, execute the loop, and commit changes

**Prohibited paths (will be treated as a protocol violation):**
- Manually performing the red-team analysis yourself (without a Task tool subagent)
- Delegating to `cat:work-execute` — this is an implementation subagent, not a hardening subagent
- Delegating to any non-Task-tool path
- Announcing "executing skill-builder in-place hardening mode" and then doing it yourself

If you are reading this and thinking "I should now run the loop", stop — you are primed incorrectly.
Return to Step 4 and spawn Task tool subagents.

In-place hardening mode runs the adversarial TDD loop against a skill file in a worktree in a single session,
producing one commit per round as the loop progresses.

**Primary workflow — single skill file:**

In-place hardening mode activates when the caller passes a single skill file path inside the current worktree.

1. Read the file content as `CURRENT_INSTRUCTIONS`. Store the file path as `SKILL_FILE_PATH`. Determine
   the worktree root by running `git rev-parse --show-toplevel` from within the worktree; store as
   `WORKTREE_ROOT`. Pass `WORKTREE_ROOT` to all red-team and blue-team subagent prompts so they use
   absolute paths (e.g., `{WORKTREE_ROOT}/findings.json`) rather than relative paths.
2. Run the full RED→BLUE loop (up to 10 rounds) as defined in Step 4. Each round produces two commits:
   one from the red-team (findings.json) and one from the blue-team (patched skill file). The loop
   continues until convergence (no CRITICAL/HIGH entries in the `loopholes` array) or the round cap is reached.
3. No additional write step is needed — the blue-team commits the hardened content directly each round.

**Secondary workflow — directory / batch mode:**

If the caller passes a directory path (or `--batch <dir>`) instead of a single file, enumerate all `SKILL.md`
and `first-use.md` files under the directory recursively. Apply the single-skill workflow to each file.

By default, process files **sequentially** (safe for all worktrees). Parallel processing is allowed when each
skill file is independent (no shared skill-to-skill dependencies). In parallel mode, each subagent runs the
full RED→BLUE loop for its own file, committing per-round — never touching other skill files. Parallel
subagents must not commit shared files (e.g., index files or aggregated docs) to avoid merge conflicts; those
are updated once after all parallel subagents complete.

Skip files that are not valid skill files (missing Purpose or Procedure sections). If a skill file fails
validation after blue-team patching, log the failure and continue to the next skill.

After all skill files are processed (or user types `abort`), display a batch summary table:

| Skill | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|-----------------|-----------------|-----------------|
| ...   | ...    | ...             | ...             | ...             |

---

## Output Format

**Final skill output includes:**

1. **SKILL.md or first-use.md file** — The complete designed, benchmarked, and hardened skill content
2. **Frontmatter** — YAML with name, description (trigger-oriented), and optional argument-hint
3. **Purpose section** — The goal statement from the backward chaining methodology
4. **Procedure section** — Forward-execution steps calling extracted functions
5. **Verification section** — How to confirm the skill works correctly
6. **Optional sections** — Prerequisites, Functions, Examples (as needed based on skill complexity)

**For complex skills** (XML-based with routing and conditional loading), include:
- `<execution_context>` section with file references
- `<process>` section with named steps and routing logic
- Conditional `<step>` blocks that branch based on conditions

**Frontmatter guidelines:**

```yaml
---
name: skill-name
description: "[WHEN to use] - [what it does briefly]"
user-invocable: true/false (only if non-default)
argument-hint: "<args>" (if skill references $ARGUMENTS or $N)
---
```

The description is used for intent routing — include trigger conditions and user synonyms, but exclude
implementation details (trust levels, internal architecture, etc.).

---

## Related Concepts

- **subagent-context-minimization**: When to delegate to subagents and how to pass references instead of
  content — `plugin/concepts/subagent-context-minimization.md`

## Verification

- [ ] Design subagent returned a complete skill draft
- [ ] Benchmark phase ran with 2+ test cases
- [ ] Benchmark results show meaningful signal (non-zero pass rate differential)
- [ ] Step 3 eval-run subagents commit run output files and return SHA+metadata JSON (not raw text)
- [ ] Step 3 grader subagents receive SHA+path input, commit grading JSON, return SHA only (path is reconstructed by
  main agent using naming convention `eval-artifacts/<SESSION_ID>/grading/<case-id>-<config>.json`)
- [ ] Step 3 grader subagents retry `git commit` up to 3 times on ref-lock errors before returning an error
- [ ] Step 3 grader subagents return `{"error": "git show failed: ..."}` if run output cannot be read; main agent
  excludes that test case from aggregation
- [ ] Step 3 EVAL_ARTIFACTS_DIR and CLAUDE_SESSION_ID are passed as resolved literal strings to all subagents — no
  subagent expands these variables independently
- [ ] Step 3 BenchmarkAggregator subagent reads grading files via git show, commits benchmark.json, returns SHA+summary_table
- [ ] Step 3 skill-analyzer-agent receives benchmark SHA+path, returns analysis commit SHA + compact report text
- [ ] Step 3 main agent context contains only SHAs, small metadata JSON, benchmark summary table, and analysis report — no raw JSON blobs
- [ ] Adversarial TDD loop completed (either converged or 10 iterations reached)
- [ ] Final skill document includes purpose, procedure, and verification sections
- [ ] Frontmatter description is trigger-oriented and contains no implementation details
- [ ] No Functions or Prerequisites sections prime manual construction (per priming prevention checklist)
- [ ] Step 4 uses commit-based handoff: red-team commits findings.json, blue-team commits patched skill file
- [ ] Step 4 reuses one red-team and one blue-team agent across rounds via task_id resume
- [ ] Step 4 blue-team uses `subagent_type: "cat:blue-team-agent"` (not `general-purpose` + `model: "opus"`)
- [ ] Step 4 diff validation uses `cat:diff-validation-agent` Task, not inline orchestrator review
- [ ] Step 4 DIFF_VALIDATION_TASK_ID is stored and reused via resume in round 2+
- [ ] Step 4 round 2+ prompts include git diff of previous round's changes for delta-focused analysis
- [ ] Step 4 blue-team prompt includes dispute protocol: verify each finding's premise before patching
- [ ] Step 4 blue-team moves false-premise findings to `disputed` array with `false_premise` and `evidence` fields
- [ ] Step 4 blue-team only patches findings remaining in `loopholes` after dispute evaluation
- [ ] Step 4 arbitration agent spawned after blue-team if any disputes were raised
- [ ] Step 4 arbitration agent is a fresh general-purpose subagent (not red-team or blue-team)
- [ ] Step 4 arbitration agent returns JSON verdict array: `[{"finding_id": "...", "verdict": "upheld|rejected", "reasoning": "..."}]`
- [ ] Step 4 upheld disputes receive `"arbitration_verdict": "upheld"` and remain in `disputed` array
- [ ] Step 4 rejected disputes are moved back to `loopholes` and blue-team is resumed to patch them
- [ ] Step 4 round 2+ red-team prompt includes prior disputed findings list (only arbitration-upheld entries) so red-team does not re-raise them
- [ ] Step 4 termination check scans only the `loopholes` array (not `disputed`) for CRITICAL/HIGH entries
- [ ] Step 4 `disputed` array contains only arbitration-upheld disputes; rejected disputes are in `loopholes`
- [ ] Step 4 blue-team round 2+ prompt includes prior rejected disputes so blue-team cannot re-dispute findings arbitration already rejected
- [ ] findings.json `disputed` array accumulates across rounds and is never reset
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows Loopholes Closed, Disputes Upheld, and Patches Applied columns
