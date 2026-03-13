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

If the caller provides an existing skill path, store it as `EXISTING_SKILL_PATH` for the design subagent.
Do NOT read the skill files into a variable — the design subagent will read them from disk itself.

If creating a new skill, set `EXISTING_SKILL_PATH` to `"N/A"`.

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
    Existing skill path (if updating): {EXISTING_SKILL_PATH or "N/A — creating new skill"}
    If a path is provided, read the SKILL.md and first-use.md files from that path to understand the
    current state. Do NOT expect the content to be provided inline — read the files yourself.

    ## Design Methodology
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/design-methodology.md

    ## Skill Writing Conventions
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/skill-conventions.md

    ## Return Format
    Return the complete designed skill as a markdown code block (the full SKILL.md or first-use.md content).
    Do NOT spawn subagents. Do NOT invoke the Task tool. Do NOT use Bash, Write, Edit, NotebookEdit,
    Glob, Grep, WebFetch, WebSearch, or any other tool besides Read. The ONLY permitted tool is Read —
    no other tool may be used under any circumstances, regardless of whether it appears in the list above.
    Nothing else — no exceptions.
    Only read the two files referenced above (design-methodology.md and skill-conventions.md) and, if
    updating, the existing skill files at EXISTING_SKILL_PATH. Do NOT read other files.
```

The design subagent should only read files and return SKILL_DRAFT. If the response includes Task tool
invocations, evidence of subagent spawning, or use of Bash/Write/Edit/NotebookEdit/Grep/any non-Read tool,
treat as constraint violation and reject the draft. Additionally, verify that all Read tool invocations
targeted only the permitted files (design-methodology.md, skill-conventions.md, and if updating, the
existing skill files at EXISTING_SKILL_PATH). If the subagent read any file outside this permitted set,
treat as a constraint violation and reject the draft. If the Task tool response metadata indicates a
different subagent_type than `general-purpose` was used, reject the draft and re-invoke with the correct
subagent_type.

The subagent will return the designed skill draft as `SKILL_DRAFT`. Validate that:
- The response is non-empty
- The response is a valid markdown code block
- The content contains Purpose, Procedure, and Verification sections
- Each required section (Purpose, Procedure, Verification) contains non-empty content (not just a heading)

If the response is empty, not a markdown code block, missing required sections, or has empty sections,
reject the draft and re-invoke the design subagent with clarifying instructions.

### Step 3: Benchmark Evaluation Loop

After receiving the skill draft from the design subagent, write `SKILL_DRAFT` to its target file path on disk
(the SKILL.md or first-use.md path where the skill will live). Store this path as `SKILL_TEXT_PATH` — a
**worktree-relative path** (e.g., `plugin/skills/my-skill/first-use.md`), not an absolute filesystem path.
Commit the file with message `benchmark: write skill draft [session: ${CLAUDE_SESSION_ID}]` and store the
commit SHA as `SKILL_DRAFT_SHA`. The skill text is now on disk and committed, so subagents can read it via
`git show <SHA>:<SKILL_TEXT_PATH>` or `cat <SKILL_TEXT_PATH>`.

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/cat-config.json`. If `effort = low`, skip
the benchmark evaluation loop (Steps 3a–3g), adversarial hardening (Step 4), and compression phase (Step 6)
entirely. Proceed directly to ## Output Format with a single-run sanity check: spawn one benchmark-run
subagent with the skill active on a simple test scenario, verify it produces non-empty output, and report
the result to the user.

At the start of Step 3, compute `BENCHMARK_ARTIFACTS_DIR` as
`<worktree-root>/benchmark-artifacts/${CLAUDE_SESSION_ID}` (expanding `${CLAUDE_SESSION_ID}` to its actual
value). Pass this resolved path as a literal string to all subagents — do NOT pass variable references.

**Context isolation:** `BENCHMARK_ARTIFACTS_DIR` includes the session ID in its path, ensuring that concurrent
benchmark-run subagents from different sessions write to separate directories and never collide. Each
benchmark-run subagent receives `BENCHMARK_ARTIFACTS_DIR` and `CLAUDE_SESSION_ID` as pre-resolved literal
strings, so no subagent ever expands these variables independently. Subagents must not derive their own
session ID — they must use the value passed by the main agent.

#### Step 3a: Auto-Generate Test Cases

Extract semantic units from the skill file using the Nine-Category Extraction Algorithm embedded in
`${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/validation-protocol.md` (Section 1). Perform this
extraction inline (do NOT spawn a subagent for extraction). Read the validation-protocol.md file to apply
the algorithm.

For each extracted unit, classify it as behaviorally testable or not:
- **Testable (generate a test case):** REQUIREMENT, PROHIBITION, CONDITIONAL, SEQUENCE, DEPENDENCY,
  EXCLUSION, CONSEQUENCE
- **Not testable (skip):** REFERENCE, CONJUNCTION

For each testable unit, generate a test case using this template:
1. Extract the constraint from the semantic unit's `original` text
2. Design a scenario that exercises the constraint:
   - For REQUIREMENT: scenario where the requirement should be applied
   - For PROHIBITION: scenario where the forbidden action is tempting but must be avoided
   - For CONDITIONAL: two scenarios — one triggering the condition, one not
   - For SEQUENCE: scenario requiring multiple ordered steps
   - For DEPENDENCY: scenario with dependency present, scenario with dependency absent
   - For EXCLUSION: scenario attempting both mutually exclusive options
   - For CONSEQUENCE: scenario triggering the cause, assert the effect occurs
3. Generate assertions using the hybrid type system:
   - **Deterministic** (preferred): regex match, string containment, structural check — graded inline
   - **Semantic** (fallback): when the expected behavior requires understanding or judgment
   - Each test case must have at least one assertion
   - Maximize deterministic-to-semantic ratio; use semantic only when behavior is genuinely subjective

**Assertion type decision heuristic:**
- Use deterministic when: output format (table, list, JSON), presence/absence of specific strings,
  file operations, ordering constraints
- Use semantic when: correctness of explanations, appropriateness of approach, quality beyond syntax
- When uncertain, prefer deterministic; only fall back to semantic if the property is genuinely subjective

**Test case JSON schema** (stored in `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json`):
```json
{
  "test_cases": [
    {
      "test_case_id": "TC1",
      "semantic_unit_id": "unit_5",
      "category": "REQUIREMENT",
      "prompt": "Scenario text that exercises the behavior...",
      "assertions": [
        {
          "assertion_id": "TC1_det_1",
          "type": "deterministic",
          "method": "regex",
          "description": "output must contain table with 4 columns",
          "pattern": "\\|[^|]+\\|[^|]+\\|[^|]+\\|[^|]+\\|",
          "expected": true
        },
        {
          "assertion_id": "TC1_sem_1",
          "type": "semantic",
          "description": "explanation correctly identifies the root cause",
          "instruction": "Check if the output explanation correctly identifies...",
          "expected": true
        }
      ]
    }
  ]
}
```

Deterministic assertion methods:
- `regex`: `pattern` field contains regex; pass if output matches (or doesn't match when `expected: false`)
- `string_match`: `pattern` field contains literal string; pass if output contains it
- `structural`: `pattern` field contains a structural check description (e.g., "JSON with key 'status'")

After generating test cases, present them to the user for approval. The user may add, remove, or modify
test cases. Auto-generated cases that appear non-discriminating are flagged for the user's review.

Store the approved test cases in `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and commit with message
`benchmark: generate test cases [session: ${CLAUDE_SESSION_ID}]`. Store the commit SHA as
`BENCHMARK_SET_SHA`. Do NOT retain the test case JSON in context — subagents read assertions from the
committed file via `git show {BENCHMARK_SET_SHA}:benchmark-artifacts/<SESSION_ID>/test-cases.json` or
`cat {BENCHMARK_ARTIFACTS_DIR}/test-cases.json`.

#### Step 3b: SPRT Benchmark

Run the SPRT-based benchmark to measure the skill's compliance quantitatively.

**SPRT parameters:**
- p0 = 0.95 (pass rate under H₀ — skill is compliant)
- p1 = 0.85 (pass rate under H₁ — skill is non-compliant)
- α = 0.05, β = 0.05
- A = log((1 − β) / α) = log(19) ≈ 2.944 (accept boundary)
- B = log(β / (1 − α)) = log(0.0526) ≈ −2.944 (reject boundary)

**SPRT decision function** (applied after each run for each test case independently):
```
If observation k is PASS:
  log_ratio += log(p0 / p1)   # log(0.95 / 0.85) ≈ 0.1112
If observation k is FAIL:
  log_ratio += log((1 − p0) / (1 − p1))  # log(0.05 / 0.15) ≈ −1.0986

After each observation:
  if log_ratio >= A → Accept H₀ (compliant, stop testing this case)
  if log_ratio <= B → Reject H₀ (non-compliant, stop all cases, proceed to hardening)
  if B < log_ratio < A → Inconclusive (continue testing)
```

**Mixed assertion aggregation:** A single benchmark run passes if and only if ALL assertions pass
(deterministic and semantic). One failed assertion fails the entire run. SPRT receives one pass/fail per run.

**Pipelining control flow:**

1. Main agent spawns up to 4 benchmark-run subagents in parallel (Haiku model, fresh non-resumed)
2. As each subagent completes, main agent immediately:
   a. Grades deterministic assertions from the subagent's return value (inline, no subagent overhead)
   b. If semantic assertions exist, spawns a Haiku grader subagent (counts against the 4-agent limit)
   c. Once all assertions for the run are graded, updates the SPRT log_ratio for that test case
   d. Checks boundaries: if Accept or Reject, stops spawning new subagents for that test case
3. If any test case rejects, all remaining test cases stop immediately
4. Freed agent slots are used for test cases still inconclusive
5. Loop terminates when all test cases have accepted or any test case has rejected

**Spawn parallel benchmark-run subagents:** Each subagent runs with the skill active at `SKILL_TEXT_PATH`.
Each benchmark-run subagent:
1. Executes the test case prompt in its configured environment (skill present and active)
2. Grades all deterministic assertions inline before returning (regex, string_match, structural checks)
3. Returns per-assertion pass/fail with `null` for semantic assertions (pending grading)
4. Writes the full output to a temp file: `/tmp/benchmark-runs/<case-id>_run_<N>.txt` (NOT committed)
5. Returns: `{"run_id": "<TC_id>_run_<N>", "test_case_id": "<TC_id>", "assertion_results":
   [{"assertion_id": "...", "passed": true|false|null}], "semantic_pending": ["<assertion_id>"],
   "output_path": "/tmp/benchmark-runs/<case-id>_run_<N>.txt"}`
   On failure, returns `{"error": "<reason>"}`.

Pass each subagent only scalar references (test case ID, run index, `BENCHMARK_ARTIFACTS_DIR`,
`CLAUDE_SESSION_ID`, model: `claude-haiku`) — do NOT embed test case content or assertion arrays inline in
the prompt. The benchmark-run subagent reads assertions from
`cat {BENCHMARK_ARTIFACTS_DIR}/test-cases.json`.

The benchmark-run subagent prompt must include the prohibition: "Do NOT read benchmark-artifacts (other than
test-cases.json to read the prompt and assertions), grading files, run-output files from other subagents,
or any file under benchmark-artifacts/ other than test-cases.json, via any mechanism (Read tool, Bash,
Grep, or otherwise). This prohibition is absolute — it applies regardless of the file's content or purpose,
including peer subagent output files. Do NOT run any git command that could reveal skill content or commit
history — this includes git log, git show, git diff, git rev-list, git shortlog, git format-patch, and any
other command that outputs committed content or commit messages."

**Spawn Haiku grader subagents** for semantic assertions: When a benchmark-run subagent returns with
`semantic_pending` entries, spawn one Haiku grader subagent per pending semantic assertion (in parallel,
each gets its own subagent). Each grader subagent:
- Receives: the assertion object (type, description, instruction, expected), the output file path
  (grader reads the file itself via the Read tool)
- Is prohibited: "Do NOT read the skill file, test-cases.json, or any file other than the specified
  output file at {output_path}."
- Returns: `{"assertion_id": "<id>", "passed": true|false}`

**Concurrent commit safety:** Run outputs are written to temp files (NOT committed per-run). Only the
final `benchmark.json` is committed after SPRT completes. This eliminates git lock contention. If any
subagent needs to commit (grading summaries), retry up to 3 times with exponential backoff and jitter:
first retry after 1–2 seconds (randomized), second after 2–4 seconds, third after 4–8 seconds. If all
retries fail, return `{"error": "commit failed: <reason>"}`.

**After SPRT completes:** Write results to `${BENCHMARK_ARTIFACTS_DIR}/benchmark.json` with per-test-case
SPRT log_ratios, pass/fail counts, and final decision (Accept/Reject). Commit with message
`benchmark: SPRT result [session: ${CLAUDE_SESSION_ID}]`. Store SHA as `BENCHMARK_SHA`.

Display SPRT results to the user: per-test-case decision, log_ratio, and pass/fail counts.

#### Step 3c: Analyze via skill-analyzer-agent

**Analyze via skill-analyzer-agent subagent:** Spawn skill-analyzer-agent. Pass it the benchmark
SHA+path (from the SPRT result) and `SKILL_TEXT_PATH` (worktree-relative). The `skill_text_path` must be
a **worktree-relative path** (e.g., `plugin/skills/my-skill/first-use.md`) — never an absolute path.

```
Task tool:
  description: "Analyze skill against benchmark"
  subagent_type: "cat:skill-analyzer-agent"
  prompt: |
    ## Benchmark
    SHA: {BENCHMARK_SHA}
    Path: {BENCHMARK_PATH}

    ## Skill Text
    skill_text_path: {SKILL_TEXT_PATH}

    ## Worktree Root
    WORKTREE_ROOT: {WORKTREE_ROOT}

    ## Benchmark Artifacts
    BENCHMARK_ARTIFACTS_DIR: {BENCHMARK_ARTIFACTS_DIR}
    CLAUDE_SESSION_ID: {CLAUDE_SESSION_ID}

    Read the skill text using: cat {WORKTREE_ROOT}/{SKILL_TEXT_PATH}
    (SKILL_TEXT_PATH is worktree-relative; prepend WORKTREE_ROOT for the absolute path.)

    RESTRICTION: This is a read-only analysis task. Do NOT modify the skill file, benchmark
    artifacts, findings.json, or any other file in the worktree. Do NOT use the Write or
    Edit tools. Do NOT use Bash to run commands that modify files (rm, mv, sed -i, tee, etc.).
    You may only use Read and Bash (read-only commands like git show, cat, grep) to gather data.
```

The subagent returns the analysis commit SHA and the compact analysis report text. The main agent receives
only the compact analysis report text (~1KB). It does NOT read the analysis file.

**Display results to user:** Present the SPRT benchmark summary and the analysis report text. Ask the user:
1. Are there any test cases to remove or replace based on the pattern analysis?
2. Would you like to improve the skill and re-run the benchmark?
3. Are you satisfied with the current skill version?

**Iterate if needed:** If the user requests improvement, apply targeted changes to the skill file at
`SKILL_TEXT_PATH`, commit the updated file, and update `SKILL_DRAFT_SHA` before returning to Step 3b. Cap
at 5 benchmark iterations total. Track the best-performing iteration by storing `BEST_SCORE` and `BEST_SHA`
(the commit SHA of the skill file at that iteration). `BEST_SCORE` is defined as the fraction of test cases
that reached SPRT Accept. After each iteration, compare the current BEST_SCORE and update if higher. Stop
iterating if the absolute improvement between consecutive rounds is less than 5 percentage points — restore
the best skill version by running `git checkout {BEST_SHA} -- {SKILL_TEXT_PATH}` and committing with
message `benchmark: restore best iteration [session: ${CLAUDE_SESSION_ID}]`, then report "Benchmark plateau
reached." If the iteration cap is reached, apply the same rollback to `BEST_SHA` if the final iteration is
not the best, then stop and report "Benchmark iteration cap reached (5 rounds) — presenting best result."

### Step 4: Adversarial TDD Loop

After the benchmark phase converges, harden the instructions using alternating red-team and blue-team
subagents. Run until convergence (no CRITICAL/HIGH loopholes remain).

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/cat-config.json`. If `effort = low`, skip
adversarial hardening entirely and proceed to Step 5.

**Protocol:** Follow [plugin/concepts/adversarial-protocol.md](${CLAUDE_PLUGIN_ROOT}/concepts/adversarial-protocol.md)
for the complete adversarial loop including:
- Red-team → blue-team → arbitration → diff-validation flow
- Structured JSON returns from each subagent (commit hash + control flow metadata)
- Dispute mechanism, arbitration, and convergence criterion
- Round advancement and error handling

**findings.json schema:** The red-team and blue-team agents define the authoritative findings.json schema
in their own agent definitions (fields: `name`, `severity`, `attack`, `evidence`). The generic schema
shown in adversarial-protocol.md (fields: `file`, `line`, `type`, `description`, `recommendation`) is
illustrative — the agent-specific schemas take precedence. The blue-team and diff-validation agents must
process findings using the fields the red-team agent actually writes, not the protocol's generic schema.

**Skill-builder-specific configuration:**

| Parameter | Value |
|-----------|-------|
| `target_type` | `skill_instructions` |
| `TARGET_FILE_PATH` | `{SKILL_FILE_PATH}` (the skill's SKILL.md or first-use.md being hardened) |
| `CURRENT_CONTENT` | Pass `TARGET_FILE_PATH` — subagents read the file from disk themselves. Do NOT embed file content inline in subagent prompts. |

> **Invocation variants — target_type:** The default `skill_instructions` can be replaced to match
> the content being hardened:
>
> | `target_type`       | Content being hardened          | `TARGET_FILE_PATH` points to |
> |---------------------|---------------------------------|------------------------------|
> | `skill_instructions`| Skill or agent Markdown file    | SKILL.md or agent .md file   |
> | `test_code`         | Test source file                | *Test.java or *Test.sh       |
> | `source_code`       | Implementation source file      | *.java, *.sh, etc.           |

After hardening converges, present the hardening changes to the user for review before proceeding to
compression.

### Step 5: In-Place Hardening Mode (Optional)

**BLOCKING — Do NOT implement this loop manually.** Reading this section does not authorize direct
execution of the hardening algorithm. You are NOT the hardening engine — you are the orchestrator.

The ONLY valid execution path is:
- Spawn red-team and blue-team subagents using the **Task tool** as defined in Step 4
- Let the subagents read the target file from `SKILL_FILE_PATH` on disk, execute the loop, and commit changes

**Prohibited paths (will be treated as a protocol violation):**
- Manually performing any part of the hardening loop yourself — including red-team analysis, blue-team
  patching, arbitration, or diff validation — without a Task tool subagent
- Delegating to `cat:work-execute` — this is an implementation subagent, not a hardening subagent
- Delegating to any non-Task-tool path
- Announcing "executing skill-builder in-place hardening mode" and then doing it yourself

If you are reading this and thinking "I should now run the loop", stop — you are primed incorrectly.
Return to Step 4 and spawn Task tool subagents.

In-place hardening mode runs the adversarial TDD loop against a skill file in a worktree in a single session,
producing one commit per round as the loop progresses.

**Primary workflow — single skill file:**

In-place hardening mode activates when the caller passes a single skill file path inside the current worktree.
This mode is intended for hardening existing, already-functional skills — it applies adversarial instruction
review only and does NOT run the benchmark evaluation loop (Steps 1-3). Before entering in-place mode,
the orchestrator must verify that a prior benchmark exists for this skill by checking whether
`benchmark-artifacts/*/benchmark.json` contains an entry whose skill path matches the target file (search
via `git log --all --oneline -- 'benchmark-artifacts/*/benchmark.json'` to find benchmark commits, then
verify at least one exists). If no prior benchmark is found, the orchestrator must abort in-place mode and
fall back to the full workflow (Steps 1-4) with the message: "No prior benchmark found for this skill —
running full workflow including benchmark evaluation."

1. Store the file path as `SKILL_FILE_PATH`. Do NOT read the file into `CURRENT_INSTRUCTIONS` and relay
   it inline to subagents — subagents read the file from `SKILL_FILE_PATH` themselves. Determine
   the worktree root by running `git rev-parse --show-toplevel` from within the worktree; store as
   `WORKTREE_ROOT`. Pass `WORKTREE_ROOT` to all red-team and blue-team subagent prompts so they can
   construct absolute paths for **direct filesystem operations** (e.g., `cat {WORKTREE_ROOT}/findings.json`,
   `mkdir -p {WORKTREE_ROOT}/...`). For `git show` commands, subagents must use repo-relative paths
   (e.g., `git show <sha>:findings.json`) as specified in the shared adversarial protocol.
2. Run the full RED→BLUE loop as defined in Step 4 and the shared adversarial protocol. Each round
   produces commits from red-team (findings.json) and blue-team (patched skill file). The loop
   continues until convergence (red-team returns `has_critical_high: false`).
3. No additional write step is needed — the blue-team commits the hardened content directly each round.

**Secondary workflow — directory / batch mode:**

If the caller passes a directory path (or `--batch <dir>`) instead of a single file, enumerate all `SKILL.md`
and `first-use.md` files under the directory recursively. Apply the single-skill workflow to each file.

By default, process files **sequentially** (safe for all worktrees). Between sequential skills, delete the
previous skill's `findings.json` (or `findings-<skill-name>.json` if using per-skill paths) before starting
the next skill to prevent stale disputes from contaminating subsequent red-team analysis. Parallel processing
is allowed when each skill file is independent (no shared skill-to-skill dependencies). In parallel mode,
each subagent runs the full RED→BLUE loop for its own file, committing per-round — never touching other
skill files. Each parallel subagent must use a skill-specific findings path
(`{WORKTREE_ROOT}/findings-<skill-name>.json`) instead of the shared `{WORKTREE_ROOT}/findings.json` to
avoid overwrite collisions between concurrent red-team agents. Derive `<skill-name>` as
`<directory-name>-<file-stem>` (e.g., `work-agent-first-use` for
`plugin/skills/work-agent/first-use.md`, `git-commit-agent-SKILL` for
`plugin/skills/git-commit-agent/SKILL.md`). This compound key avoids collisions when a single skill
directory contains both SKILL.md and first-use.md. Pass the skill-specific findings path to the
red-team and blue-team subagent prompts via a `FINDINGS_PATH` parameter that overrides the default
`{WORKTREE_ROOT}/findings.json`.
Parallel subagents must not commit shared files (e.g., index files or aggregated docs) to avoid merge
conflicts; those are updated once after all parallel subagents complete. The concurrent commit safety
retry protocol (exponential backoff with jitter, up to 3 retries) from Step 3 also applies to all
red-team and blue-team commits in batch parallel mode. Each parallel subagent must retry on ref-lock
contention using the same backoff schedule: first retry after 1-2 seconds (randomized), second after
2-4 seconds, third after 4-8 seconds.

Skip files that are not valid skill files (missing Purpose or Procedure sections). If a skill file fails
validation after blue-team patching, log the failure and continue to the next skill.

After all skill files are processed (or user types `abort`), display a batch summary table:

| Skill | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|-----------------|-----------------|-----------------|
| ...   | ...    | ...             | ...             | ...             |

### Step 6: Compression Phase

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/cat-config.json`. If `effort = low`, skip
this entire step. The compression phase runs only when `effort = medium` or `high`.

After hardening achieves compliance (Step 4 converges and SPRT re-benchmark accepts), compress the skill
file to minimize token cost while preserving behavioral compliance.

**Hardening + benchmarking + compression are always run together** — never one without the others when
effort > low.

**Sequential phases, never interleaved:**
1. Harden until compliant (only add text) — Step 4
2. Compress to minimize size (only remove text) — Step 6
3. Re-benchmark to verify compression preserved compliance — Step 6 SPRT re-benchmark
4. If compliance dropped, mark load-bearing text as protected and retry compression (up to 3 times)

#### Step 6a: Post-Hardening SPRT Re-Benchmark

Before compressing, run a full SPRT benchmark on the hardened skill to confirm compliance. Use the same
test cases from `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and identical SPRT parameters as Step 3b.
Commit benchmark results with message `benchmark: post-hardening SPRT [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `POST_HARDENING_SHA`. If any test case rejects, return to hardening (Step 4) to address the
failures before proceeding to compression.

#### Step 6b: Compress

Invoke a general-purpose subagent to compress the skill file:

```
Task tool:
  description: "Compress skill: [skill name]"
  subagent_type: "general-purpose"
  prompt: |
    Compress the skill file at {SKILL_TEXT_PATH} following the compression protocol below.

    ## Compression Protocol
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/compression-protocol.md

    {IF_RETRY: ## Protected Sections
    The following sections are load-bearing and must NOT be removed, rephrased, or merged:
    Read constraint file: {PROTECTED_SECTIONS_PATH}
    All text listed there is mandatory preservation — treat it as decision-affecting requirements.}

    ## Output
    Write the compressed file to: {BENCHMARK_ARTIFACTS_DIR}/compressed-{skill-filename}.md

    RESTRICTION: Do NOT read test-cases.json, benchmark.json, or any benchmark artifact other than
    protected-sections.txt (if provided). Do NOT read or modify the original skill file at {SKILL_TEXT_PATH}.
    Do NOT modify any file other than the output path above.
```

Commit the compressed file with message `benchmark: compress skill [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `COMPRESSED_SHA`.

#### Step 6c: Semantic Pre-Check (Fast Gate)

Before running the full SPRT re-benchmark, run a semantic pre-check using the comparison algorithm from
`${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/validation-protocol.md` (Section 2):

1. Extract semantic units from the original hardened skill (using Section 1 extraction algorithm)
2. Extract semantic units from the compressed skill
3. Compare units using the Section 2 comparison algorithm
4. If any LOST unit has severity HIGH (PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION) → skip SPRT and
   retry compression immediately (mark the lost unit's text as protected)
5. If semantically EQUIVALENT or only MEDIUM/LOW losses → proceed to SPRT re-benchmark

#### Step 6d: Post-Compression SPRT Re-Benchmark

Run SPRT re-benchmark on the compressed version using identical test cases and parameters as Step 6a.

**Acceptance criterion:** ALL test cases must reach SPRT Accept (log_ratio ≥ A).

**On rejection:** Compression broke compliance. Identify protected text:
1. Run `git diff {POST_HARDENING_SHA}..{COMPRESSED_SHA} -- {SKILL_TEXT_PATH}` to find removed/changed hunks
2. Identify which test case(s) triggered SPRT rejection and which assertions failed
3. Cross-reference failed assertions with their source semantic units (`semantic_unit_id` in test-cases.json)
4. Cross-reference source semantic units with diff hunks via the `location` field in the extracted unit
5. Mark the corresponding hunks as protected in `${BENCHMARK_ARTIFACTS_DIR}/protected-sections.txt`:

```
## Protected Section 1
Reason: Compression removed this text and SPRT compliance dropped from ACCEPT to REJECT
Original location: lines 45-52
Text:
> [verbatim text that was removed]
```

Commit protected-sections.txt with message
`benchmark: mark protected sections [session: ${CLAUDE_SESSION_ID}]`.

**Retry strategy (cap at 3 attempts):**
- Retry 1: Compress with protected sections from the first failure
- Retry 2: If still failing, diff again to find additional load-bearing text, add to protected sections
- Retry 3: Final attempt with all accumulated protected sections
- After 3 failures: Accept the post-hardening (uncompressed) version as final; report "Compression could
  not preserve compliance after 3 retries — accepting uncompressed version."

**On acceptance:** Copy compressed file to `SKILL_TEXT_PATH` (overwrite original), commit with message
`benchmark: accept final [session: ${CLAUDE_SESSION_ID}]`. Commit
post-compression benchmark.json with message
`benchmark: post-compression SPRT [session: ${CLAUDE_SESSION_ID}]`.

Present the final size reduction and compliance metrics to the user.

---

## Output Format

**Final skill output includes:**

1. **SKILL.md or first-use.md file** — The complete designed, benchmarked, hardened, and compressed skill
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
- **skill-analyzer-agent**: Detects delegation opportunities and content relay anti-patterns in skill
  procedures — `plugin/agents/skill-analyzer-agent/SKILL.md`

## Verification

- [ ] Design subagent returned a complete skill draft
- [ ] Benchmark phase ran with auto-generated test cases (one per testable semantic unit)
- [ ] Test cases include both deterministic and semantic assertion types where appropriate
- [ ] Skill-builder maximizes deterministic-to-semantic assertion ratio when generating test cases
- [ ] Benchmark commit message prefix uses `benchmark:`
- [ ] Benchmark artifacts directory is `benchmark-artifacts/`
- [ ] Variables use `BENCHMARK_ARTIFACTS_DIR` and `BENCHMARK_SET_SHA`
- [ ] SPRT decision logic uses p0=0.95, p1=0.85, α=0.05, β=0.05 with boundaries A≈2.944, B≈-2.944
- [ ] Each test case runs its own independent SPRT; rejection of any case stops all remaining cases
- [ ] SPRT check runs after each individual agent completion (pipelined), not batched per wave
- [ ] Eval-run subagents use Haiku model, fresh (non-resumed) per run
- [ ] Eval-run subagents grade deterministic assertions inline before returning
- [ ] Semantic assertions use separate Haiku grader subagents
- [ ] Run outputs written to temp files (not committed per-run); only benchmark.json is committed
- [ ] Eval-run subagent return format includes per-assertion pass/fail with null for semantic assertions
- [ ] BENCHMARK_ARTIFACTS_DIR and CLAUDE_SESSION_ID passed as resolved literal strings to all subagents
- [ ] Benchmark results show meaningful signal (SPRT log_ratios indicate compliance level)
- [ ] Re-benchmark after hardening uses identical SPRT parameters and same test cases
- [ ] Effort gate: hardening + benchmarking + compression phase skipped entirely when effort = low
- [ ] Hardening and compression are never interleaved — harden first, then compress
- [ ] Compression subagent does not read test-cases.json, benchmark.json, or other benchmark artifacts
- [ ] Post-compression SPRT acceptance criteria identical to post-hardening benchmark
- [ ] Compression retries capped at 3 attempts; uncompressed version accepted after 3 failures
- [ ] Semantic pre-check gates compression before full SPRT re-benchmark
- [ ] Protected text identification cross-references SPRT failure data with diff hunks
- [ ] Step 2 design subagent tool prohibition explicitly lists Grep alongside Bash, Write, Edit, Glob, WebFetch, WebSearch
- [ ] Step 2 draft validation checks that each required section contains non-empty content (not just headings)
- [ ] Step 4 follows shared adversarial protocol from plugin/concepts/adversarial-protocol.md
- [ ] Shared protocol performs final-round MEDIUM/LOW cleanup pass (blue-team only, no arbitration/diff-validation) before loop exit
- [ ] Step 4 uses target_type: skill_instructions
- [ ] Step 4 main agent never reads findings.json directly — uses structured JSON returns from subagents
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows Loopholes Closed, Disputes Upheld, and Patches Applied columns
- [ ] Step 2 design subagent tool prohibition explicitly lists NotebookEdit alongside other prohibited tools
- [ ] Step 5 prohibited paths cover all hardening loop phases (red-team, blue-team, arbitration, diff validation)
- [ ] Step 4 arbitration agent scope restriction prohibits modifying any file other than findings.json
- [ ] Step 5 sequential batch mode requires deleting findings.json between skills to prevent contamination
- [ ] Step 2 design subagent validation verifies Read tool was used only on permitted files
- [ ] Step 4 blue-team patch constraints explicitly protect verification checklist items from removal or weakening
- [ ] Step 4 does not embed file content inline in subagent prompts — subagents read from TARGET_FILE_PATH
- [ ] Step 5 in-place mode verifies prior benchmark existence before skipping Steps 1-3 (checking benchmark-artifacts/*/benchmark.json)
- [ ] Step 5 batch mode uses per-skill findings paths (`findings-<skill-name>.json`) to avoid collisions
- [ ] Step 5 batch mode passes `FINDINGS_PATH` parameter to override default findings.json path in subagent prompts
- [ ] Step 5 distinguishes filesystem operations (use WORKTREE_ROOT prefix) from git show (use repo-relative paths)
- [ ] Step 3c skill-analyzer-agent report includes Delegation Opportunities and Content Relay Anti-Patterns sections
- [ ] Step 2 design subagent Read scope is restricted to methodology, conventions, and existing skill files only
- [ ] Step 5 batch mode skill-name derivation is defined as `<directory-name>-<file-stem>` to avoid collisions
- [ ] Step 5 batch parallel mode extends the concurrent commit retry protocol to red-team and blue-team commits
- [ ] Step 3b benchmark plateau tracks BEST_SCORE and BEST_SHA, rolls back to best iteration on plateau or cap
- [ ] Step 4 arbitration agent prompt includes explicit tool restrictions (Write/Edit limited to findings.json, no state-modifying Bash)
- [ ] Semantic extraction uses embedded Nine-Category algorithm from validation-protocol.md (not subagent invocation)
- [ ] Extracted units include id, category, original, normalized, quote, and location fields
- [ ] Non-testable units (REFERENCE, CONJUNCTION) are skipped when generating test cases
- [ ] Auto-generated test cases are presented to the user for approval before benchmarking
- [ ] User can add, remove, or modify auto-generated test cases
