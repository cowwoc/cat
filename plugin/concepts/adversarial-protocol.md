<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Adversarial Protocol for Instruction Hardening

This document defines the unified adversarial TDD loop used by skill-builder-agent and tdd-implementation-agent
to harden instructions and test suites through alternating red-team and blue-team review cycles.

## Overview

The adversarial protocol runs red-team and blue-team agents iteratively to find and close gaps in instructions
or test code. The loop continues until convergence (no major findings remain) with no arbitrary round limits.

**Why two separate persistent subagents:**
A single agent playing both roles anchors on its own attack vectors — it knows exactly which loopholes it
invented, so it subconsciously over-fits the defense to those attacks and under-defends against variations.
Separate subagents eliminate this bias. Reusing the same agent across rounds (via `resume`) is more efficient
than spawning fresh agents each round; to counter anchoring risk from reuse, agents are explicitly instructed
to seek new attack vectors each round rather than revisiting prior findings.

## Effort Gate

When the calling agent's configured effort level is `low`, skip the adversarial hardening loop entirely. The loop
is most valuable for medium and high effort work where thoroughness justifies the additional rounds. Low-effort
work relies on the author's initial quality and stakeholder review for validation.

Callers should check effort before entering the loop:

- `effort = low` → skip adversarial hardening, proceed to next workflow step
- `effort = medium` or `effort = high` → run the full adversarial loop

## Convergence Criterion

**The adversarial loop continues as long as the `loopholes` array in findings.json contains CRITICAL or HIGH
severity entries. Once the `loopholes` array contains only LOW/MEDIUM entries (or is empty), the loop terminates.**

This removes arbitrary round limits and enables convergence-based stopping. The red-team signals convergence
via `has_critical_high: false` in its structured JSON return — the main agent never reads findings.json directly.

**Important:** Only entries in the `loopholes` array count toward convergence. Entries in the `disputed` array
(those with `"arbitration_verdict": "upheld"`) are excluded from the CRITICAL/HIGH count, as the arbitration
agent has confirmed they are false premises, not actual loopholes.

**Final-round MEDIUM/LOW cleanup:** When the red-team returns `has_critical_high: false` and the `loopholes`
array still contains MEDIUM or LOW entries, resume the blue-team one final time to patch those remaining
findings before exiting the loop. Skip arbitration and diff-validation for this cleanup pass — MEDIUM/LOW
findings do not warrant the full validation cycle. After the blue-team commits the cleanup patches, the loop
terminates.

## findings.json Schema

The findings file contains two top-level arrays:

```json
{
  "loopholes": [
    {
      "file": "path/to/file",
      "line": 42,
      "severity": "CRITICAL|HIGH|MEDIUM|LOW",
      "type": "missing_assertion|missing_parameter|edge_case|...",
      "description": "What the finding is",
      "recommendation": "How to fix it"
    }
  ],
  "disputed": [
    {
      "file": "path/to/file",
      "line": 42,
      "severity": "CRITICAL|HIGH|MEDIUM|LOW",
      "type": "...",
      "description": "...",
      "recommendation": "...",
      "false_premise": "What the red-team incorrectly claimed",
      "evidence": "Why the premise is false (with specific citations and verifiable facts)",
      "arbitration_verdict": "upheld"
    }
  ]
}
```

### Array Semantics

- **`loopholes`**: Active findings that require patching. Each entry has at minimum `"severity"` and a description.
  Entries in this array are presumed to be valid and must be addressed.

- **`disputed`**: Findings where the blue-team disputed the red-team's claim and the arbitration agent verified
  the dispute. Only entries with `"arbitration_verdict": "upheld"` appear in this array. These findings are NOT
  patched — they represent false premises that have been independently verified.

**Important:** The `disputed` array accumulates across rounds and is never reset. Rejected disputes (where
arbitration found the evidence insufficient) are moved back to `loopholes` for patching.

## Full Protocol Flow

### Round Initialization

At the start of each round, initialize or retrieve:
- `TARGET_FILE_PATH` — the file path (repo-relative) to the content being hardened (e.g.,
  `plugin/skills/foo/SKILL.md`, `src/Parser.java`)
- `COMMIT_SHA` — the git commit hash to use for `git show` commands (optional, defaults to HEAD)
- `PRIOR_DISPUTED_FINDINGS` — entries from the `disputed` array from the previous round (arbitration-upheld
  only), or `[]` if this is round 1
- `PRIOR_REJECTED_DISPUTES` — disputes that arbitration rejected in prior rounds, or `[]` if this is round 1
- `RED_TEAM_TASK_ID` — the stored task_id of the persistent red-team agent (None on round 1)
- `BLUE_TEAM_TASK_ID` — the stored task_id of the persistent blue-team agent (None on round 1)
- `DIFF_VALIDATION_TASK_ID` — the stored task_id of the persistent diff-validation agent (None on round 1)
- `PRE_ROUND_COMMIT` — the current commit hash (`git rev-parse HEAD`) captured before spawning the blue-team
  agent each round. Provides a rollback target if blue-team commits must be reverted and `git revert` encounters
  conflicts.

## Git Operations: Repository-Relative Paths

All `git show <sha>:<path>` commands must use repository-relative paths, NOT absolute filesystem paths.

Examples:
- ✓ CORRECT: `git show c3a45bc:findings.json` (repo-relative)
- ✓ CORRECT: `git show c3a45bc:plugin/skills/foo.md` (repo-relative)
- ✗ WRONG: `git show c3a45bc:/workspace/findings.json` (absolute)
- ✗ WRONG: `git show c3a45bc:${WORKTREE_ROOT}/findings.json` (absolute)

Repository-relative paths work consistently regardless of worktree location or absolute filesystem paths. The git
object store is repository-wide, making repo-relative paths the stable reference.

For direct filesystem operations (cat, mkdir, cp), use absolute paths with `${WORKTREE_ROOT}` prefix. For git object
store access, always use repo-relative paths.

### Step 1: Red-Team Analysis

**Round 1 — Spawn red-team:**

```
Task tool:
  description: "Red-team: find loopholes (round 1)"
  subagent_type: "cat:red-team-agent"
  prompt: |
    ## Target Type
    {skill_instructions|test_code|source_code}

    ## Target File Path
    {TARGET_FILE_PATH}

    ## Commit SHA
    {COMMIT_SHA}

    Read the target content from {TARGET_FILE_PATH} using the Read tool or `git show {COMMIT_SHA}:{TARGET_FILE_PATH}`.

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Round Number
    1

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "has_critical_high": true|false}
    Where has_critical_high is true if the loopholes array contains any CRITICAL or HIGH severity entries.
```

**Round 2+ — Resume red-team:**

```
Task tool (resume):
  task_id: {RED_TEAM_TASK_ID}
  prompt: |
    Round {N}. Resume your red-team analysis.

    ## Target Type
    {skill_instructions|test_code|source_code}

    ## What Changed Since Last Round
    {git diff RED_TEAM_COMMIT_HASH..BLUE_TEAM_COMMIT_HASH -- TARGET_FILE_PATH}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Round Number
    {N}

    ## Prior Disputed Findings (do NOT re-raise these)
    {JSON array from the "disputed" field of findings.json, or "[]" if none}

    First, analyze the diff above to determine whether the blue-team's patches introduced new gaps or
    failed to fully close prior loopholes. Then, re-examine the FULL current instructions (not just the diff)
    for attack vectors not yet explored in previous rounds — the diff focus must not prevent discovery of
    loopholes present in unchanged sections.
    Do NOT re-raise findings that appear in the Prior Disputed Findings list above — those have been
    rejected with evidence and must not be re-submitted.
    Write new findings to {WORKTREE_ROOT}/findings.json and commit as before.

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "has_critical_high": true|false}
    Where has_critical_high is true if the loopholes array contains any CRITICAL or HIGH severity entries.
```

**Commit hash validation:** Before using `RED_TEAM_COMMIT_HASH`, verify it is a valid commit on the current
branch. Run `git merge-base --is-ancestor {RED_TEAM_COMMIT_HASH} HEAD` and confirm exit code 0. If the check
fails, abort with "ERROR: red-team returned invalid or detached commit hash {RED_TEAM_COMMIT_HASH}".

**Freshness check:** Additionally verify the commit advanced by running
`git log --oneline {PREV_COMMIT}..{COMMIT_HASH}` and confirming at least one entry exists. If the log is empty,
the subagent returned a stale commit hash (no new work was committed). Log "ERROR: subagent returned stale commit
hash {COMMIT_HASH} (no commits since {PREV_COMMIT})" and abort.

**Termination check:** Parse the JSON object from the last line of the red-team's response. Extract the
`commit` field as `RED_TEAM_COMMIT_HASH` and the `has_critical_high` field. If `has_critical_high` is `false`:
check for remaining MEDIUM/LOW findings (see "Final-round MEDIUM/LOW cleanup" in § Convergence Criterion).
If MEDIUM/LOW findings remain, resume blue-team for one final cleanup pass, then **STOP THE LOOP**. If no
findings remain, **STOP THE LOOP** immediately. If `has_critical_high` is `true`: proceed to Step 2
(blue-team patching).

**Error handling:**
- If the red-team's last line is not valid JSON or is missing `commit` or `has_critical_high` fields: log
  "ERROR: red-team returned malformed JSON: {last line}" and abort the loop.
- If `commit` is not a valid commit on the current branch (verify with
  `git merge-base --is-ancestor {RED_TEAM_COMMIT_HASH} HEAD`): log "ERROR: red-team returned invalid or
  detached commit hash {RED_TEAM_COMMIT_HASH}" and abort.

### Step 2: Blue-Team Patching with Dispute Mechanism

**Blue-team patch constraints:** The blue-team CANNOT remove capabilities, delete features, or weaken verification
items to close loopholes. Patches must harden the target while preserving its full functionality. If a loophole can
only be closed by removing a capability, the blue-team must document why and flag it for human review rather than
silently deleting the capability.

**Dispute Protocol:** Before patching any finding, the blue-team must verify its premise. If a finding claims
something that is factually incorrect (e.g., claims an env var is unavailable when it is documented as available,
misrepresents an API's behavior, or assumes a file does not exist when it does):
1. Do NOT patch the finding.
2. Move it from the `loopholes` array to the `disputed` array in findings.json.
3. Add two fields:
   - `"false_premise"`: What the red-team claimed (the incorrect assumption).
   - `"evidence"`: Why the premise is false (cite specific documentation, env var names, actual API behavior,
     test results, or file existence).
4. Only patch findings remaining in the `loopholes` array after dispute evaluation.
5. Never patch a finding that has been moved to `disputed`.

**Round 1 — Spawn blue-team:**

```
Task tool:
  description: "Blue-team: close loopholes (round 1)"
  subagent_type: "cat:blue-team-agent"
  prompt: |
    ## Target Type
    {skill_instructions|test_code|source_code}

    ## Target File Path
    {TARGET_FILE_PATH}

    Read the target file at {TARGET_FILE_PATH} to understand the changes needed.

    ## Red-Team Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Target File Path
    {TARGET_FILE_PATH}

    ## Worktree Root
    {WORKTREE_ROOT}

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
    revised skill file. Commit all modified files.

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "has_new_disputes": true|false}
    Where has_new_disputes is true if you moved any findings to the disputed array in this round.
```

**Round 2+ — Resume blue-team:**

```
Task tool (resume):
  task_id: {BLUE_TEAM_TASK_ID}
  prompt: |
    Round {N}. Resume your blue-team patching.

    ## Target Type
    {skill_instructions|test_code|source_code}

    ## Red-Team Commit Hash
    {RED_TEAM_COMMIT_HASH}

    ## Target File Path
    {TARGET_FILE_PATH}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Round Number
    {N}

    ## Prior Rejected Disputes (do NOT re-dispute these)
    {JSON array of disputes that arbitration rejected in prior rounds, including finding_id, reasoning}

    Do NOT re-dispute findings listed above — arbitration has already reviewed and rejected the evidence.
    These findings must be patched, not disputed again.

    Apply the dispute protocol before patching: for each finding in `loopholes`, verify its premise. If the
    premise is false, move the finding to `disputed` with `"false_premise"` and `"evidence"` fields. Only
    patch findings that remain in `loopholes` after dispute evaluation. Write the updated findings.json. If
    any findings were patched, also write the revised skill file. Commit all modified files.

    Note: Disputes are subject to arbitration — findings you move to `disputed` will be independently
    verified before they are accepted. If arbitration rejects a dispute, you will be asked to patch it.

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "has_new_disputes": true|false}
    Where has_new_disputes is true if you moved any findings to the disputed array in this round.
```

**Blue-team return parsing:** Parse the JSON object from the last line of the blue-team's response. Extract
the `commit` field as `BLUE_TEAM_COMMIT_HASH` and the `has_new_disputes` field.

**Error handling:**
- If the blue-team's last line is not valid JSON or is missing `commit` or `has_new_disputes` fields: log
  "ERROR: blue-team returned malformed JSON (round {N}): {last line}" and abort.
- Apply the same commit hash validation as for red-team (verify it is a valid commit on the current branch).

**Freshness check:** Additionally verify the commit advanced by running
`git log --oneline {PREV_COMMIT}..{COMMIT_HASH}` and confirming at least one entry exists. If the log is empty,
the subagent returned a stale commit hash (no new work was committed). Log "ERROR: subagent returned stale commit
hash {COMMIT_HASH} (no commits since {PREV_COMMIT})" and abort.

### Step 3: Arbitration Phase (Dispute Verification)

**When to run:** If the blue-team's `has_new_disputes` field is `true`, spawn a fresh arbitration subagent to
independently verify each dispute before it is accepted.

**Detection:** Check the `has_new_disputes` field from the blue-team's JSON return. If `has_new_disputes` is
`false`: skip arbitration and proceed to Step 4 (diff validation). If `has_new_disputes` is `true`: spawn
the arbitration agent below.

**Arbitration subagent prompt:**

```
Task tool:
  description: "Arbitration: verify blue-team disputes (round {N})"
  subagent_type: "general-purpose"
  prompt: |
    You are an independent arbitration agent. Your role is to verify whether the blue-team's evidence
    actually proves that the red-team's premise is false. You are NOT the red-team or blue-team — do not
    advocate for either side. Evaluate each dispute on the evidence alone.

    ## Blue-Team Commit Hash
    {BLUE_TEAM_COMMIT_HASH}

    ## Worktree Root
    {WORKTREE_ROOT}

    ## Instructions
    1. Read findings.json from the blue-team commit:
       `git show {BLUE_TEAM_COMMIT_HASH}:{WORKTREE_ROOT}/findings.json`
    2. Collect all entries in the `disputed` array that do NOT have `"arbitration_verdict": "upheld"`.
       These are the new disputes to review.
    3. For each disputed finding:
       a. Read the `"false_premise"` field — this is what the red-team claimed.
       b. Read the `"evidence"` field — this is why the blue-team says the premise is false.
       c. Independently verify the evidence. For example:
          - If evidence cites documentation, read that documentation file and confirm the claim matches.
          - If evidence cites an env var being available, check relevant documentation.
          - If evidence cites a test assertion, verify the assertion text matches what is claimed.
       d. Determine verdict:
          - `"upheld"` — the evidence proves the red-team's premise is false; the dispute is valid.
          - `"rejected"` — the evidence does NOT prove the premise false; the finding is legitimate.
    4. Apply verdicts to findings.json:
       - For upheld disputes: add `"arbitration_verdict": "upheld"` to the entry (it stays in `disputed`).
       - For rejected disputes: move the entry back from `disputed` to `loopholes` (removing
         `"false_premise"` and `"evidence"` fields). This finding must be patched by blue-team.
    5. Write the updated findings.json and commit with message:
       `arbitration: process {N} dispute(s) (round {R})`

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "rejected_count": N, "rejected_findings": [...]}
    Where rejected_count is the number of disputes rejected (moved back to loopholes), and
    rejected_findings is the array of rejected finding objects (for passing context to blue-team).
    If all disputes were upheld, return rejected_count: 0 and rejected_findings: [].
```

## Arbitration Agent Scope Constraint

The arbitration agent must ONLY modify `findings.json`. It must NOT modify the target file being hardened or any other
worktree file.

The arbitration prompt must include:

'You may ONLY modify findings.json. Do NOT modify {TARGET_FILE_PATH} or any other file in the worktree. Do NOT use
the Write or Edit tools on any file other than findings.json. Do NOT use Bash to run commands that modify worktree
state (git checkout, git reset, rm, mv, sed -i, etc.). You may use Read and Bash (read-only commands like git show,
cat, grep) solely to gather data and verify claims in disputed findings.'

**Verdict processing:**

1. Parse the JSON object from the last line of the arbitration agent's response. Extract `commit` as
   `ARBITRATION_COMMIT_HASH`, `rejected_count`, and `rejected_findings`.

2. If `rejected_count` is 0 (all disputes upheld): proceed directly to Step 4 (diff validation) using
   `ARBITRATION_COMMIT_HASH` as the effective commit (findings.json is already updated by the arbitration
   agent).

3. If `rejected_count` > 0: **resume the blue-team agent** to patch the rejected findings:

```
Task tool (resume):
  task_id: {BLUE_TEAM_TASK_ID}
  prompt: |
    Round {N} arbitration rejected the following disputes — the findings were moved back to loopholes:
    {rejected_findings array from arbitration JSON return}

    Read the current findings.json at {ARBITRATION_COMMIT_HASH}, patch each finding now in `loopholes`,
    and write the revised skill file. Commit all modified files.

    ## Return Format
    Return ONLY a JSON object on the last line:
    {"commit": "<hash>", "has_new_disputes": true|false}
    Where has_new_disputes is true if you moved any findings to the disputed array in this round.
```

4. After blue-team re-patches, parse the JSON return and update `BLUE_TEAM_COMMIT_HASH` to the new commit.
   All upheld disputes remain in the `disputed` array with `"arbitration_verdict": "upheld"`.

5. Accumulate rejected disputes across rounds in a list `REJECTED_DISPUTES` (from `rejected_findings` +
   arbitration reasoning). Pass this list to the blue-team round 2+ prompt as "Prior Rejected Disputes".

### Step 4: Diff Validation

**Purpose:** Ensure every CRITICAL/HIGH finding (excluding arbitration-upheld disputes) has a corresponding
patch hunk in the diff.

**Round 1 — Spawn diff-validation:**

```
Task tool:
  description: "Diff validation: round 1"
  subagent_type: "cat:diff-validation-agent"
  prompt: |
    ## Target Type
    {skill_instructions|test_code|source_code}

    ## RED_TEAM_COMMIT_HASH
    {RED_TEAM_COMMIT_HASH}

    ## BLUE_TEAM_COMMIT_HASH
    {BLUE_TEAM_COMMIT_HASH}

    ## TARGET_FILE_PATH
    {TARGET_FILE_PATH}

    ## WORKTREE_ROOT
    {WORKTREE_ROOT}

    ## Round Number
    1
```

**Round 2+ — Resume diff-validation:**

```
Task tool (resume):
  task_id: {DIFF_VALIDATION_TASK_ID}
  prompt: |
    ## Target Type
    {skill_instructions|test_code|source_code}

    ## RED_TEAM_COMMIT_HASH
    {RED_TEAM_COMMIT_HASH}

    ## BLUE_TEAM_COMMIT_HASH
    {BLUE_TEAM_COMMIT_HASH}

    ## TARGET_FILE_PATH
    {TARGET_FILE_PATH}

    ## WORKTREE_ROOT
    {WORKTREE_ROOT}

    ## Round Number
    {N}
```

## Diff-Validation Scope Check

In addition to matching findings to hunks (coverage check), perform a reverse scope check: every hunk in the
blue-team diff must map to a specific loophole in findings.json.

If any hunk modifies content unrelated to the listed loopholes (e.g., weakens an unrelated prohibition, restructures
uninvolved sections, or changes unrelated verification items), reject the blue-team commit.

The diff-validation prompt must include:

'After matching each finding to hunks, perform a reverse check: for each hunk in the diff, verify it maps to at least
one active finding. If any hunk modifies content unrelated to any loophole (i.e., it touches lines or sections not
covered by active findings), mark the validation as FAIL with reason "out-of-scope hunk" and include the hunk details
in your report.'

**Failure handling:** The diff-validation agent returns a commit hash on its last line and exits non-zero when
any non-disputed CRITICAL or HIGH finding has no matching patch hunk. If the agent exits non-zero:

1. Read the `diff-validation-{N}.json` report from the returned commit hash to identify which findings had no
   matching hunk (those with `"outcome": "FAIL"`).
2. Run `git revert {BLUE_TEAM_COMMIT_HASH} --no-edit` to undo the commit. If the revert encounters merge
   conflicts, abort with `git revert --abort` and fall back to `git reset --hard {PRE_ROUND_COMMIT}` to restore
   the pre-round state.
3. Resume the blue-team agent with: "Your round {N} patch was reverted. The following findings had no
   corresponding patch hunk: {FAIL findings from diff-validation report}. Rewrite the patch touching only
   the lines required to close each finding. Do not modify any other content. Commit all modified files.
   Return ONLY a JSON object on the last line: {\"commit\": \"<hash>\", \"has_new_disputes\": true|false}"
4. Re-run diff validation (resume `DIFF_VALIDATION_TASK_ID`) with the new blue-team commit. If validation
   still fails, log "ERROR: blue-team introduced insufficient patches in round {N} after retry — aborting loop"
   and stop. Do not retry more than once per round.

## Diff-Validation Failure: Revert and Restore Disputes

When diff-validation rejects the blue-team commit, the orchestrator reverts the blue-team commit to undo both the
patched target file and the updated findings.json.

However, `git revert` undoes the entire findings.json commit, including any findings moved to the `disputed` array.
Legitimately disputed findings would be re-introduced into the `loopholes` array on the next red-team run.

**Correction procedure after revert:**

1. After reverting the blue-team commit, read the reverted commit's findings.json to extract the `disputed` array:
   ```
   git show {BLUE_TEAM_COMMIT_HASH}:findings.json | grep -A999 '"disputed"' > /tmp/disputes.json
   ```

2. Restore the `disputed` array to findings.json on disk.

3. Commit this restoration:
   ```
   git commit -m 'adversarial: restore disputed array after revert'
   ```

This ensures disputes (metadata about the adversarial process) survive the revert and legitimately disputed findings
are not re-introduced into the loopholes list.

### Step 5: Round Advancement

After successful diff validation:

1. No variable update needed. Subagents read from {TARGET_FILE_PATH} on disk in each round. Update
   `COMMIT_SHA` to `BLUE_TEAM_COMMIT_HASH` so the next round's red-team reads the latest patched content.
2. Increment round counter.
3. Return to Step 1 (red-team analysis) to check for remaining loopholes.

**Important:** Arbitration-upheld disputes count as closed for round-advancement purposes. The round counter
increments whether findings were patched, arbitration-upheld, or both. A round where all findings were
arbitration-upheld (none patched) still advances the counter normally.

## Summary Table

When batch processing multiple skill files, display results as:

| Skill | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|------------------|-----------------|-----------------|
| ...   | ...    | ...              | ...             | ...             |

Where:
- **Rounds**: Total rounds completed
- **Loopholes Closed**: CRITICAL/HIGH findings patched in final version
- **Disputes Upheld**: Findings in `disputed` array with `"arbitration_verdict": "upheld"`
- **Patches Applied**: Total patch hunks committed across all rounds
