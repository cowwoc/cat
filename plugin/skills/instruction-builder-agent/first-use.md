<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Instruction Builder

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
Use instruction-builder for BOTH types.

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
    Glob, Grep, WebFetch, WebSearch, TaskOutput, ToolSearch, Skill, or any other tool besides Read.
    Do NOT invoke any skill (e.g., cat:grep-and-read-agent, cat:batch-read-agent, or any other
    cat: skill). The ONLY permitted tool is Read — no other tool may be used under any circumstances,
    regardless of whether it appears in the list above. Nothing else — no exceptions.
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
subagent_type. Note: these checks are best-effort — they detect tool usage only when evidence appears in
the return value. The Task tool does not provide a tool-usage audit log, so undetectable violations remain
an inherent limitation of instruction-based isolation.

The subagent will return the designed skill draft as `SKILL_DRAFT`. Validate that:
- The response is non-empty
- The response is a valid markdown code block
- The content contains Purpose, Procedure, and Verification sections
- Each required section (Purpose, Procedure, Verification) contains non-empty content (not just a heading)

If the response is empty, not a markdown code block, missing required sections, or has empty sections,
reject the draft and re-invoke the design subagent with clarifying instructions.

### Step 3: Compact-Output Pass

Before writing the draft to disk, review `SKILL_DRAFT` for output-token waste. Apply each compaction rule
below inline (no subagent needed). **Correctness takes priority over compactness** — both semantic correctness
(meaning/parsing) and visual correctness (user-facing output alignment and readability) override any size
reduction.

**Correctness exemptions — do NOT apply compaction when:**
- Inside YAML frontmatter (whitespace is syntax)
- Inside Makefile targets (tabs are required, spaces are wrong)
- Inside fenced code blocks where indentation is part of the example
- In any context where changing whitespace would change meaning or break parsing
- In display tables, boxes, or formatted reports where spacing is part of the visual design

**Compaction rules (apply only outside exempted contexts):**

1. **Condense verbose section headings** — if a heading repeats context already established by the skill's
   purpose or a parent heading, shorten or remove the redundant portion.
2. **Shorten examples** — trim examples to the minimum needed to illustrate the point; remove lines that
   duplicate the explanatory text above them.
3. **Remove unused output sections** — if a section produces content never referenced downstream (e.g., a
   table always populated with a single static row, or a section whose output is never acted on), remove it.
4. **Deduplicate repeated guidance** — if the same rule or constraint appears in two or more steps verbatim
   or near-verbatim, keep it in the most authoritative location and replace the others with a brief reference.
5. **Omit receiver-irrelevant output** — review what each output section communicates to its receiver (user,
   subagent, or calling skill). Remove content the receiver cannot act on or does not need: internal reasoning
   steps that informed a decision but weren't requested, full context that the receiver already has, verbose
   status updates that duplicate information the receiver already knows.

After applying compaction rules, store the result as `SKILL_DRAFT` (overwrite with the compacted version).
If no changes were made, proceed without noting it — this pass is silent unless changes were significant
(>10% size reduction), in which case note "Compact-output pass reduced draft by ~{N}%."

### Step 4: Benchmark Evaluation Loop

After receiving the skill draft from the design subagent, write `SKILL_DRAFT` to its target file path on disk
(the SKILL.md or first-use.md path where the skill will live). Store this path as `SKILL_TEXT_PATH` — a
**worktree-relative path** (e.g., `plugin/skills/my-skill/first-use.md`), not an absolute filesystem path.
Commit the file with message `benchmark: write skill draft [session: ${CLAUDE_SESSION_ID}]` and store the
commit SHA as `SKILL_DRAFT_SHA`. The skill text is now on disk and committed, so subagents can read it via
`git show <SHA>:<SKILL_TEXT_PATH>` or `cat <SKILL_TEXT_PATH>`.

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/config.json`. If `effort = low`, skip
the benchmark evaluation loop (Steps 4.1–4.4), adversarial hardening (Step 5), and compression phase (Step 7)
entirely. Proceed directly to ## Output Format with a single-run sanity check: spawn one benchmark-run
subagent with the skill active on a scenario that exercises the skill's primary purpose (i.e., a prompt
that triggers the skill's main workflow, not an empty or no-op input). Verify the output contains at least
one substantive result from the skill's procedure (e.g., a generated step, a produced artifact, or a
decision — not merely an echo of the prompt or a generic acknowledgment). If the sanity check fails
(no substantive result), do NOT proceed to Output Format — report the failure to the user and return to
Step 2 to redesign the skill draft. Report the result to the user.

At the start of Step 4, compute `BENCHMARK_ARTIFACTS_DIR` as the `benchmark/` subdirectory adjacent to the
skill file being improved:
```bash
# SKILL_TEXT_PATH is worktree-relative (e.g., plugin/skills/foo/SKILL.md)
SKILL_ABS_PATH="${CLAUDE_PROJECT_DIR}/${SKILL_TEXT_PATH}"
BENCHMARK_ARTIFACTS_DIR="$(dirname "$SKILL_ABS_PATH")/benchmark"
```
Pass this resolved path as a literal string to all subagents — do NOT pass variable references.

**Model selection:** Read the target skill's `model:` frontmatter field to determine which model to use for
benchmark-run subagents. Run:
```bash
BENCHMARK_MODEL=$("${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner" extract-model \
  "<absolute-path-to-SKILL_TEXT_PATH>")
```
The script prints the model name (e.g., `sonnet`, `haiku`) or falls back to `haiku` when the field is absent.
Store the result as `BENCHMARK_MODEL` and pass it as a resolved literal string to all benchmark-run and grader
subagents. Do NOT hardcode `haiku` — always use the value from `extract-model`.

**Artifact location:** `BENCHMARK_ARTIFACTS_DIR` is the stable `benchmark/` directory adjacent to the skill
file. Artifacts written here can be committed alongside the skill and compared across sessions to detect
regressions or improvements. Each benchmark-run subagent receives `BENCHMARK_ARTIFACTS_DIR`, `CLAUDE_SESSION_ID`,
and `BENCHMARK_MODEL` as pre-resolved literal strings, so no subagent ever expands these variables
independently. Subagents must not derive their own session ID — they must use the value passed by the main agent.

**Single-session assumption:** This workflow assumes only one session benchmarks a given skill at a time.
Two concurrent sessions targeting the same skill would both write to the same `benchmark.json` file, causing
overwrites and corrupted results. No file locking is implemented. If concurrent benchmarking of the same skill
is needed in the future, add a lock file under `BENCHMARK_ARTIFACTS_DIR` keyed by session ID before proceeding.

#### Step 4.1: Auto-Generate Test Cases

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

**`semantic_unit_id` naming convention:** Use domain-specific names (e.g., `unit_step44_guard`,
`unit_step44_reject`) rather than sequential IDs (e.g., `unit_1`, `unit_2`) to make each unit's intent
self-describing.

Deterministic assertion methods:
- `regex`: `pattern` field contains regex; pass if output matches (or doesn't match when `expected: false`)
- `string_match`: `pattern` field contains literal string; pass if output contains it
- `structural`: `pattern` field contains a structural check description (e.g., "JSON with key 'status'")

After generating test cases, present them to the user for approval. The user may add, remove, or modify
test cases. Auto-generated cases that appear non-discriminating are flagged for the user's review.

Store the approved test cases in `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and commit with message
`benchmark: generate test cases [session: ${CLAUDE_SESSION_ID}]`. Store the commit SHA as
`BENCHMARK_SET_SHA`. Do NOT retain the test case JSON in context — subagents read assertions from the
committed file via `cat {BENCHMARK_ARTIFACTS_DIR}/test-cases.json`.

#### Step 4.2: Incremental Test Case Selection (Re-benchmarking only)

When re-benchmarking an existing skill after an edit (rather than benchmarking a brand-new draft), use
`${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner` to identify which test cases need to re-run and which can carry forward from the
prior benchmark.

**Workflow:**

1. Run change detection to identify modified line ranges:
   ```bash
   "${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner" detect-changes \
     <SKILL_DRAFT_SHA> <SKILL_TEXT_PATH> "${BENCHMARK_ARTIFACTS_DIR}/test-cases.json"
   ```
   Output fields:
   - `skill_changed`: false → skip re-benchmark entirely (carry all results forward)
   - `frontmatter_changed`: true → all test cases must re-run (model/config may have changed)
   - `body_changed` + `changed_ranges`: the line ranges in the updated skill body that changed
   - `rerun_test_case_ids`: populated when frontmatter changed (all TCs) or no body-only change
   - `semantic_units_path_hint`: guidance for the next step when body-only changes occurred

2. When `body_changed=true` and `frontmatter_changed=false`, extract semantic units from the updated
   skill body:
   ```bash
   "${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner" extract-units \
     <SKILL_TEXT_PATH>
   ```
   This outputs the skill body with original file line numbers prepended (tab-separated). Apply the
   Nine-Category Extraction Algorithm from `validation-protocol.md` Section 1 inline to extract units
   and their `location` fields (e.g., `"line 42"` or `"lines 42-45"`).

3. Identify which extracted semantic units have locations overlapping the `changed_ranges` from step 1.
   These are the "changed units". Collect their `id` values.

4. Map changed unit IDs to affected test cases:
   ```bash
   "${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner" map-units \
     "${BENCHMARK_ARTIFACTS_DIR}/test-cases.json" '["unit_1", "unit_5"]'
   ```
   Output: `rerun_test_case_ids` (must re-run SPRT) and `carryforward_test_case_ids` (results preserved).

5. For `carryforward_test_case_ids`, copy the SPRT log_ratio and decision from the prior
   `benchmark.json` into the new benchmark result. Only `rerun_test_case_ids` go through full SPRT.

**Semantic unit location matching:** A unit with `location: "line 42"` overlaps changed range
`{"start": 40, "end": 45}` if 40 ≤ 42 ≤ 45. For range locations `"lines 42-45"`, overlap exists
if the unit range and the changed range share any common line number.

**Carry-forward results:** Preserved results from prior passing test cases are treated as if SPRT
already Accepted those cases. Do NOT re-run them. Only report that they were carried forward in the
final benchmark summary.

#### Step 4.3: SPRT Benchmark

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
  if runs_for_this_case >= 50 → Truncate: treat as Reject (non-compliant, stop all cases)
```

**SPRT run cap per test case:** Each test case is limited to 50 SPRT runs within a single benchmark
execution. If a test case reaches 50 runs without crossing either the Accept or Reject boundary, treat it
as a Reject decision — a skill that requires more than 50 trials to demonstrate compliance is effectively
non-compliant. This cap prevents unbounded token and time consumption when the true pass rate falls within
the indifference zone between p0 and p1.

**Mixed assertion aggregation:** A single benchmark run passes if and only if ALL assertions pass
(deterministic and semantic). One failed assertion fails the entire run. SPRT receives one pass/fail per run.

**SPRT independence requirement:** Each benchmark run (TC + run number) MUST spawn a completely fresh
subagent with no prior conversation context. Do NOT execute multiple runs inside the same subagent
context — context from prior runs contaminates later runs and invalidates SPRT's independence assumption.
SPRT requires each trial to be an independent Bernoulli draw from the same underlying distribution; when
run N sees runs 1…N-1 in its conversation history, trial N is conditioned on prior trials (batch
contamination), which produces systematically biased pass rates and spurious Accept/Reject decisions.

**Pre-spawn gate — fresh-subagent parameters:** Before spawning each benchmark-run subagent, verify the
spawn parameters. STOP — do NOT spawn the subagent if spawn parameters include `resume: true`, `resume: false`,
or a `conversation_id` field (regardless of whether `resume` is also present). Block if ANY of these fields
appears — each condition independently triggers a block. The `resume` field must be entirely absent (not set to
`false`), and `conversation_id` must be entirely absent. This check is MANDATORY and must pass before any
subagent is spawned.

**Batch contamination symptom signals:** If you observe the following pattern, treat it as a corroborating
signal for batch contamination (these are supplementary signals, not the primary detection; the primary
check is the pre-spawn gate above):
- The fraction of PASS results observed so far increases monotonically across sequential run indices
  (e.g., runs 1-3 all pass, runs 1-5 all pass) rather than exhibiting variance consistent with an i.i.d.
  process — this suggests each subagent is building on previous results.
- A subagent's response references "the previous run" or "earlier output" when no such prior context
  should exist.
- Two subagents for different run indices return identical `output_path` files or identical content, which
  is only possible if they shared state.
If these symptoms appear but spawn parameters were correct (no `resume: true` / `conversation_id`),
escalate to the user rather than silently discarding — do NOT treat as a routine retry case.

**Pipelining control flow:**

1. Main agent spawns up to 4 benchmark-run subagents in parallel, each a fresh non-resumed `BENCHMARK_MODEL`
   subagent assigned exactly one run (one TC + one run index). Each subagent executes that single run and
   terminates — it is never reused for another run. Reserve at minimum 2 of the 4 slots for benchmark-run
   subagents at all times. At most 2 grader subagents may occupy slots simultaneously. If 2 grader slots
   are already occupied, queue additional grading work until a grader slot frees.
2. As each subagent completes, main agent immediately performs the Result Inspection Checklist (see below)
   before any other processing. Once the checklist passes:
   a. Independently verifies deterministic assertions by reading the output file and re-running each
      deterministic check (regex, string_match, structural) against the actual output — do not trust the
      subagent's self-reported results without verification
   b. If semantic assertions exist, spawns a `BENCHMARK_MODEL` grader subagent (counts against the 4-agent limit)
   c. Once all assertions for the run are graded, updates the SPRT log_ratio for that test case
   d. Checks boundaries: if Accept or Reject, stops spawning new subagents for that test case
3. If any test case rejects, all remaining test cases stop immediately. Once early-reject is triggered,
   freeze SPRT state — do not update log-ratio values from in-flight results, even if they return before
   you begin hardening. Log-ratio updates are only valid pre-reject.
4. Freed agent slots are used for test cases still inconclusive. Every new subagent assigned to a freed
   slot must be a completely fresh spawn — it does NOT inherit any state from the prior subagent in that slot.
5. Loop terminates when all test cases have accepted or any test case has rejected.

**Pipelining edge case scenarios:**
- **Early-accept:** TC1 reaches `log_ratio >= A` after run 3. Stop spawning subagents for TC1 immediately.
  If a TC1 subagent is already in-flight (spawned but not yet returned), wait for it to return (to collect
  timing/token data), then discard its result for SPRT purposes. Free its slot for TC2 or TC3.
- **Early-reject:** TC2 reaches `log_ratio <= B` after run 5. Stop spawning ALL new subagents across ALL
  test cases. Freeze SPRT state immediately — do not update log-ratio values from any in-flight results.
  Wait for in-flight subagents to return (to avoid orphaned processes), discard their results, and proceed
  to hardening. Do NOT spawn any additional benchmark-run or grader subagents.

**Spawn parallel benchmark-run subagents:** Each subagent runs with the skill active at `SKILL_TEXT_PATH`.
Each benchmark-run subagent executes exactly one run (one TC + one run index) and then terminates. Never
assign more than one run to a single subagent.
Each benchmark-run subagent:
1. Executes the test case prompt in its configured environment (skill present and active)
2. Evaluates deterministic assertions inline and reports results before returning (regex, string_match,
   structural checks)
3. Returns per-assertion pass/fail with `null` for semantic assertions (pending grading). The main agent
   independently verifies deterministic results by reading the output file at `output_path` and re-running
   each deterministic assertion against the actual output. If the main agent's result disagrees with the
   subagent's self-reported result, the main agent's result takes precedence.
4. Records `duration_ms` (elapsed wall-clock time in milliseconds from subagent start to return) and
   `total_tokens` (sum of input and output tokens consumed by the subagent invocation, including any
   tool-use overhead)
5. Writes the full output to a temp file:
   `/tmp/benchmark-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt` (NOT committed). Create the directory
   with `mkdir -p /tmp/benchmark-runs/<CLAUDE_SESSION_ID>` before writing. Here `<CLAUDE_SESSION_ID>` is the
   literal session ID string received as a parameter from the main agent at spawn time — do NOT expand an
   environment variable; use the literal value passed to you.
6. Returns: `{"run_id": "<TC_id>_run_<N>", "test_case_id": "<TC_id>", "assertion_results":
   [{"assertion_id": "...", "passed": true|false|null}], "semantic_pending": ["<assertion_id>"],
   "output_path": "/tmp/benchmark-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt",
   "duration_ms": <integer>, "total_tokens": <integer>}`
   On failure, returns `{"error": "<reason>"}`.

Pass each subagent only scalar references (test case ID, run index, `BENCHMARK_ARTIFACTS_DIR`,
`CLAUDE_SESSION_ID`, model: `BENCHMARK_MODEL`) — do NOT embed test case content or assertion arrays inline in
the prompt. The benchmark-run subagent reads assertions from
`cat {BENCHMARK_ARTIFACTS_DIR}/test-cases.json`.

The benchmark-run subagent prompt must include the prohibition: "Do NOT read any benchmark artifact files
(other than test-cases.json to read the prompt and assertions), grading files, or run-output files from
other subagents. The ONLY permitted read from {BENCHMARK_ARTIFACTS_DIR} is test-cases.json. Do NOT read
any other file under {BENCHMARK_ARTIFACTS_DIR} via any mechanism (Read tool, Bash, Grep, or otherwise).
This prohibition is absolute — it applies regardless of the file's content or purpose,
including peer subagent output files. Do NOT run any git command that could reveal skill content or commit
history — this includes git log, git show, git diff, git rev-list, git shortlog, git format-patch, and any
other command that outputs committed content or commit messages.
Bash commands are restricted to the following allowlist: cat, head, tail, wc, grep, mkdir.
This allowlist applies to both external commands and shell built-ins — do NOT use shell built-ins
(echo, printf, read, source, eval, set, export) or process substitution (<(...), >(...)) outside
this allowlist. Do NOT use the Write tool, Edit tool, NotebookEdit tool, TaskOutput tool, or
Skill tool. Do NOT invoke any skill (e.g., cat:grep-and-read-agent, cat:batch-read-agent, or any
other cat: skill) — the Skill tool is prohibited entirely.
The ONLY files you may read (via cat, head, tail, grep, or the Read tool) are:
(1) `{BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and (2) your own output file at the path above.
Do NOT use cat, head, tail, grep, or the Read tool on any other path — including peer subagent output files
under `/tmp/benchmark-runs/`, other files under `{BENCHMARK_ARTIFACTS_DIR}` (e.g., benchmark.json), worktree files
(e.g., findings.json), or any path not listed in (1)-(2).
The ONLY file you may write is your output file at `/tmp/benchmark-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt`
(use `mkdir -p /tmp/benchmark-runs/<CLAUDE_SESSION_ID>` to create the parent directory — mkdir may ONLY be used
with this exact path pattern; do NOT use mkdir to create any other directory).
Do NOT use shell redirection operators (>, >>) to write to any
other path. Do NOT use any Bash command not on this allowlist. Do NOT use commands that could discover or read
peer subagent output files (e.g., find, ls, grep -r, grep -rl, or glob patterns on `/tmp/benchmark-runs/`).
Do NOT use grep with recursive flags (-r, -R, --include, -l combined with directory paths) as this provides
directory discovery equivalent to find or ls. Do NOT use the Glob tool."

**Note on instruction-based isolation:** These checks verify evidence in the return value only — they
cannot confirm the subagent did not access prohibited data and simply omitted the evidence. This is an
inherent limitation of instruction-based isolation.

**Post-spawn freshness verification:** The Task tool guarantees that each invocation without `resume` or
`conversation_id` creates a completely new conversation context with no access to prior subagent state.
This is a runtime guarantee of the tool, not merely an instruction-based assumption. However, verify
freshness post-hoc for each benchmark-run subagent by checking the following in the return value:
- The subagent's response does not reference run indices, test case IDs, or output paths belonging to
  other subagents (cross-run leakage).
- The subagent's `output_path` file content does not contain results or text from a different run
  (shared-state leakage).
- The subagent does not mention "previous run", "earlier attempt", "last time", "as seen before",
  "prior result", "building on", "same approach as run", "consistent with earlier", or any other
  phrasing that implies awareness of or reference to prior benchmark runs. Apply semantic judgment —
  any language that implies knowledge of other runs counts, not just the exact phrases listed here.
If any of these checks fail, treat it as evidence that fresh-spawn isolation was violated. Stop the
entire benchmark and return `{"error": "post-spawn freshness verification failed for <run_id>:
<specific_violation_description>"}`. Do NOT retry — a freshness violation means the isolation model
is broken and all results are suspect.

**Result Inspection Checklist** — MANDATORY after each benchmark-run subagent returns, in this order,
BEFORE updating SPRT state. Do not update log-ratio values until all checks pass.

**Check 1 — Structural contamination check (primary):** Verify the returned `run_id` and `test_case_id`
exactly match the expected values for this slot, and that the return object contains no cross-run references
(no fields referencing other `run_id` values):
- Confirm the return object contains exactly one `run_id` string (not an array, not absent).
- Confirm the return object contains exactly one `test_case_id` string.
- Confirm `run_id` matches the pattern `<expected_TC_id>_run_<expected_N>` (the exact TC and run index
  assigned to this subagent).
- Confirm `test_case_id` matches `<expected_TC_id>`.
- Confirm no field in the return object references a `run_id` value other than the expected one.
- Confirm `output_path` matches the exact pattern `/tmp/benchmark-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt`
  where `<CLAUDE_SESSION_ID>`, `<case-id>`, and `<N>` match the values assigned to this subagent. Reject any
  `output_path` that does not match this pattern (e.g., paths pointing to skill files, benchmark artifacts, or
  locations outside `/tmp/benchmark-runs/`).
If any of these checks fail, discard the result and treat it as a constraint violation:
return `{"error": "single-run constraint violated: subagent <run_id> returned unexpected run_id or
test_case_id: <actual_return>"}`. Do NOT feed a violating result into SPRT.
If structural contamination is detected in 3 or more consecutive spawns for the same slot, stop the entire
benchmark and return `{"error": "batch contamination: fresh subagent spawn failed 3 consecutive times
for <run_id>"}`.

**Check 2 — Prohibition verification:** Inspect the return value for evidence of prohibited behavior:
- If the return value references file paths under `{BENCHMARK_ARTIFACTS_DIR}/` other than `test-cases.json`
  (e.g., in an `output_path` or any explanation field), reject the run.
- If the return value contains content that could only come from a peer subagent's output file (e.g., it
  quotes or references run output from a different `run_id`), reject the run.
- If the return value contains git history data (commit SHAs, commit messages, author lines), reject the
  run.
On rejection, discard the result and return:
`{"error": "prohibition violated by benchmark-run subagent <run_id>: <specific_violation_description>"}`.
Stop the entire benchmark — prohibition violations indicate the isolation model is broken and all results
are suspect.

**Check 3 — Symptom signals (corroborating, not primary):** After all structural checks pass, inspect for
contamination symptom signals described in "Batch contamination symptom signals" above. These are
corroborating signals only — if spawn parameters were correct but symptoms appear, escalate to user.

**Minimal happy-path example (single TC, single run):**

    Input scalar references passed to benchmark-run subagent:
      test_case_id: "TC1", run_index: 1, BENCHMARK_ARTIFACTS_DIR: ".../plugin/skills/my-skill/benchmark",
      CLAUDE_SESSION_ID: "abc123", model: BENCHMARK_MODEL

    Subagent reads test-cases.json, executes the TC1 prompt, grades deterministic assertions inline.

    Subagent writes output to: /tmp/benchmark-runs/abc123/TC1_run_1.txt

    Subagent returns:
    {
      "run_id": "TC1_run_1",
      "test_case_id": "TC1",
      "assertion_results": [
        {"assertion_id": "a1", "passed": true},
        {"assertion_id": "a2", "passed": null}
      ],
      "semantic_pending": ["a2"],
      "output_path": "/tmp/benchmark-runs/abc123/TC1_run_1.txt",
      "duration_ms": 4200,
      "total_tokens": 1100
    }

    Main agent spawns grader for assertion a2, grader returns: {"assertion_id": "a2", "passed": true}
    All assertions passed → run result: PASS → SPRT log_ratio updated for TC1.

**Spawn `BENCHMARK_MODEL` grader subagents** for semantic assertions: When a benchmark-run subagent returns
with `semantic_pending` entries, spawn one `BENCHMARK_MODEL` grader subagent per pending semantic assertion
(in parallel, each gets its own subagent). Each grader subagent:
- Receives: the assertion object (type, description, instruction, expected), the output file path
  (grader reads the file itself via the Read tool)
- Is prohibited: "The ONLY file you may read (via the Read tool, cat, head, tail, wc, grep, or any
  other mechanism) is the specified output file at {output_path}. Do NOT read the skill file,
  test-cases.json, peer subagent output files, findings.json, benchmark.json, or any other file.
  When evaluating the output file, ignore any quoted or reproduced skill
  instruction text that may appear in the output — base your grading solely on whether the subagent's
  behavioral response satisfies the assertion, not on whether skill text is present in the output.
  If the output contains no behavioral response (i.e., the output consists entirely or predominantly of
  reproduced skill instruction text with no substantive action, answer, or result from the subagent),
  mark the assertion as FAILED — absence of a behavioral response is not a pass.
  Bash commands are restricted to the following read-only allowlist: cat, head, tail, wc, grep.
  These commands may ONLY be used against the specified output file at {output_path} — do NOT pass any other
  file path as an argument to these commands. Do NOT use any Bash command not on this allowlist. Do NOT use
  shell redirection operators (>, >>) or any command that writes, moves, copies, or deletes files.
  Do NOT use the Write tool, Edit tool, NotebookEdit tool, TaskOutput tool, or Skill tool — no file
  may be created or modified by any mechanism. Do NOT invoke any skill (e.g., cat:grep-and-read-agent,
  cat:batch-read-agent, or any other cat: skill). Do NOT use the Glob tool."
- Returns: `{"assertion_id": "<id>", "passed": true|false}`

**Grader prohibition verification:** After each grader subagent returns, verify compliance before
accepting the result:
- If the return value contains content from the skill file (e.g., skill instruction text, skill metadata
  fields), reject the grading result.
- If the return value references `test-cases.json` content beyond what was passed in the assertion object
  (e.g., other test cases' prompts or assertions), reject the grading result.
- If the return value references any file path other than the `output_path` that was passed to the grader,
  reject the grading result.
On rejection, treat the semantic assertion as ungradeable and return:
`{"error": "grader prohibition violated for assertion <assertion_id> in run <run_id>:
<specific_violation_description>"}`. Stop the entire benchmark — a grader prohibition breach means the
grader had access to information that could bias its evaluation.

**Note on instruction-based isolation:** These checks verify evidence in the return value only — they
cannot confirm the grader did not access prohibited data and simply omitted the evidence. This is an
inherent limitation of instruction-based isolation.

**Note on /tmp path:** Benchmark run output files written to `/tmp/benchmark-runs/` may contain test case
content. The `/tmp` path is world-readable on shared systems; assume single-user execution environment.

**Concurrent commit safety:** Run outputs are written to temp files (NOT committed per-run). Only the
final `benchmark.json` is committed after SPRT completes. This eliminates git lock contention. If any
subagent needs to commit (grading summaries), retry up to 3 times with exponential backoff and jitter:
first retry after 1–2 seconds (randomized), second after 2–4 seconds, third after 4–8 seconds. If all
retries fail, return `{"error": "commit failed: <reason>"}`.

**After SPRT completes:** Write results to `${BENCHMARK_ARTIFACTS_DIR}/benchmark.json` with per-test-case
SPRT log_ratios, pass/fail counts, final decision (Accept/Reject), and token/timing aggregates. The
benchmark.json token fields are computed by accumulating `duration_ms` and `total_tokens` from each
benchmark-run subagent return value:

```json
{
  "sprt": {
    "test_cases": [
      {
        "test_case_id": "TC1",
        "decision": "Accept",
        "log_ratio": 2.944,
        "pass_count": 9,
        "fail_count": 1,
        "total_runs": 10,
        "total_tokens": 14800,
        "total_duration_ms": 42000
      }
    ],
    "overall_decision": "Accept",
    "total_tokens": 14800,
    "total_duration_ms": 42000
  }
}
```

Commit `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and `${BENCHMARK_ARTIFACTS_DIR}/benchmark.json` with
message `benchmark: SPRT result [session: ${CLAUDE_SESSION_ID}]`. Store SHA as `BENCHMARK_SHA`. Both files
are written to the skill-adjacent `benchmark/` directory and committed there directly — no separate persist
step is needed.

**Token summary display:** After committing benchmark artifacts, display a token usage summary to the user:

```
TOKEN USAGE SUMMARY
===================
Test Case | Runs | Total Tokens | Total Duration
--------- | ---- | ------------ | --------------
TC1       |  10  |     14,800   |    42,000 ms
TC2       |   8  |     12,400   |    36,000 ms
TOTAL     |  18  |     27,200   |    78,000 ms
```

Display SPRT results to the user: per-test-case decision, log_ratio, pass/fail counts, and token summary.

#### Step 4.4: SPRT Failure Investigation

**Execution guard:** If `overall_decision = "Accept"`, continue to Step 4.5.

When SPRT rejects one or more test cases, automatically run a structured failure investigation before
presenting results to the user. The investigation examines raw subagent conversation transcripts to
distinguish genuine skill failures from test environment artifacts (batch contamination, shared context
priming, model-default behaviors).

**RESTRICTION:** The investigation phase is a read-only analysis. Do NOT use the Write tool, Edit tool,
NotebookEdit tool, or Skill tool during this phase. Do NOT modify the skill file, benchmark.json,
test-cases.json, or any benchmark artifact. The only permitted operations are: reading transcripts via
cat:get-history-agent, running session-analyzer search commands via Bash, and interpreting the results.

**Investigation procedure:**

Sub-steps 2–7 are automated tool invocations. Sub-step 8 synthesizes findings into a human-readable
report.

**Sub-step 1 — Identify rejected test cases** (automatic): Collect all test case IDs where SPRT
decision is "Reject" from `benchmark.json`. Example: if TC1 and TC3 have `"decision": "Reject"`,
set `REJECTED_TC_IDS=("TC1" "TC3")`.

**Sub-step 2 — Discover benchmark-run subagent IDs** (automatic): Run the following command to list
all subagents spawned in this session, then extract the IDs of benchmark-run subagents associated
with the rejected test cases:

```bash
SESSION_ANALYZER="${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer"
# List all subagents spawned in this session
# Expected format: <agent_id> <status> <description> — one subagent per line; $1 is the agent ID field.
ANALYZE_OUTPUT=$("$SESSION_ANALYZER" analyze "${CLAUDE_SESSION_ID}")
# Parse ANALYZE_OUTPUT to identify subagents spawned during benchmark runs. Store their IDs in AGENT_IDS.
AGENT_IDS=$(echo "$ANALYZE_OUTPUT" | grep -i "benchmark-run\|rejected" | awk '{print $1}')
# Cap to maximum 5 AGENT_IDs per rejected test case to limit investigation scope.
AGENT_IDS=$(echo "$AGENT_IDS" | head -5)
# Sanitize: reject any AGENT_ID containing shell metacharacters.
# Allowed characters: alphanumeric, hyphens, underscores, slashes, and periods (to support
# full-path subagent IDs like "{session_id}/subagents/{agent_id}").
SANITIZED_IDS=""
for RAW_ID in $AGENT_IDS; do
  if [[ "$RAW_ID" =~ ^[a-zA-Z0-9_/.-]+$ ]] && [[ "$RAW_ID" != *..* ]]; then
    SANITIZED_IDS="${SANITIZED_IDS} ${RAW_ID}"
  fi
done
AGENT_IDS="$SANITIZED_IDS"
```

If no subagent IDs can be determined, record "subagent IDs not available" and continue.

**For each `AGENT_ID` in `AGENT_IDS` (one per iteration), execute sub-steps 3–7:**

```bash
for AGENT_ID in $AGENT_IDS; do
  # Sub-steps 3–7 execute here, once per AGENT_ID
done
```

**Sub-step 3 — Retrieve full transcripts via cat:get-history-agent** (automatic): Invoke
`cat:get-history-agent` with the current `AGENT_ID`:

```
Invoke cat:get-history-agent with:
  skill: "cat:get-history-agent"
  args: "<cat_agent_id> ${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}"
```

This returns the complete message history for the benchmark-run subagent. Store the transcript
content for use in sub-steps 4–7.

**Sub-step 4 — Search transcripts for compliance failures** (automatic): Run:

```bash
# Can be parallelized or consolidated with sub-steps 6 and 7 into a single session-analyzer pass.
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}" \
  "Would you like|What would you|follow.up" --regex --context 5
```

Interpret: any match indicates the benchmark-run subagent asked a follow-up question or deviated from
the skill's instruction to produce direct output. Record each match with its surrounding context lines
as a "compliance failure" candidate.

**Sub-step 5 — Check for batch contamination** (automatic + interpret):

Reuse `ANALYZE_OUTPUT` from sub-step 2 (do NOT invoke session-analyzer again). Check the output for
subagent freshness: each benchmark-run subagent should appear as a separate, independent entry with
no `resume` field present (the pre-spawn gate requires `resume` to be entirely absent, not set to any value).
If a subagent entry contains `resume: true`, `resume: false`, or a `conversation_id` field, it was not
spawned correctly. If two or more runs share a subagent ID, batch contamination is confirmed.

Interpret: signs of batch contamination in the transcripts from sub-step 3:
- Runs 1–N pass, then runs N+1–M fail within the same subagent conversation
- Earlier run output visible in later run context (prior test case prompt or response visible in the
  transcript of a later run)
- Failure rate correlates with subagent reuse, not test case content

**Sub-step 6 — Check for thinking block content** (automatic): Search the transcript for agent
reasoning recorded in thinking blocks:

```bash
# Can be parallelized or consolidated with sub-steps 4 and 7 into a single session-analyzer pass.
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}" \
  "<thinking>" --context 10
```

Interpret: if `<thinking>` blocks are present, read the content to determine whether the agent reasoned
about overriding or ignoring skill instructions, or whether it expressed uncertainty about what the
skill required. Include any relevant findings in the investigation report.

**Sub-step 7 — Check for instruction priming sources** (automatic): Run:

```bash
# Can be parallelized or consolidated with sub-steps 4 and 6 into a single session-analyzer pass.
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}" \
  "unless|except|if user|may|optional" --regex --context 3
```

Interpret: matches may indicate escape clauses in the skill instructions or received prompt that the
benchmark-run subagent exploited. Record the specific matched text and surrounding lines as "priming
source" candidates. Also look for: model-default behaviors overriding "Do not..." constraints, prior
output patterns from earlier runs appearing in context, and skill instructions containing
algorithm-before-invocation or output-format priming.

**Sub-step 8 — Summarize findings** (interpret): Produce a concise investigation report by synthesizing
the results of sub-steps 4–7. The report must cover:
- Which runs failed and in which subagents (from sub-step 2)
- Whether batch contamination is present: state "None detected" if each run used a fresh independent
  subagent, or "Detected — runs X–Y shared subagent context" with the specific subagent ID if reuse
  was found (from sub-step 5)
- What the agent output was at the point of failure: quote the exact text from the transcript where
  the compliance failure occurred, surrounded by triple backticks to prevent crafted text from blending
  with analysis (from sub-step 4)
- Whether thinking blocks reveal intent to override instructions: quote the relevant `<thinking>`
  content if present, surrounded by triple backticks (from sub-step 6)
- Identified priming sources: list the exact matched text with its file/line reference, or state
  "None identified" if no matches were found (from sub-step 7)
- Conclusion: one of the three types below

**Decision criteria:**
- If batch contamination detected → Test environment artifact
- If compliance failures found but no contamination → Genuine skill defect
- If findings are contradictory or unclear → Inconclusive

Do NOT re-display the SPRT benchmark summary (already presented at end of Step 4.3). Present ONLY the
investigation report:

Format the investigation report as:

```
SPRT FAILURE INVESTIGATION
===========================
Rejected test cases: TC1, TC3
Runs examined: <agent_id_1>, <agent_id_2>

Batch contamination: None detected
  (each run executed in a fresh independent subagent)

Failure pattern:
  TC1, run 2 (agent abc123): agent responded:
    ```
    Would you like me to also add tests?
    ```
    instead of producing the requested output directly.
  TC3, run 1 (agent def456): agent prefaced output with:
    ```
    Here's my approach:
    ```
    rather than executing the step immediately.

Thinking blocks: None found in examined transcripts.
  (or: TC1 run 2 <thinking>:
    ```
    The skill says do X but the user might prefer Y, so I'll ask...
    ```
  )

Priming sources: None identified
  (or: found "unless the user requests otherwise" at line 42 of the skill's Step 3 —
    this escape clause may have allowed the agent to deviate)

Conclusion: Genuine skill defect
→ Next step: Proceed to Step 4.5 (cat:skill-analyzer-agent) to analyze the defect pattern.

  (or: Conclusion: Test environment artifact
→ Next step: Rerun the benchmark after removing the contaminated test case or isolating the
    priming source. Do not modify the skill until a clean benchmark confirms the failure.)

  (or: Conclusion: Inconclusive
→ Next step: Gather additional evidence — examine the full transcript for each failed run via
    cat:get-history-agent, compare the failing agent's received prompt against the skill text,
    and check whether the failure reproduces across multiple independent benchmark reruns before
    deciding whether to modify the skill.)
```

**Artifact handling and routing:**
- If conclusion is "Test environment artifact": do NOT proceed to Step 4.5; recommend rerunning the
  benchmark after fixing the artifact source.
- If conclusion is "Genuine skill defect" or "Inconclusive": proceed to Step 4.5.

**Error handling:** If `session-analyzer` returns an error or no output for a sub-step, record "session-
analyzer unavailable for agent ${AGENT_ID}" in that field of the report and continue to the next
sub-step. Do not abort the investigation for a single tool failure.

#### Step 4.5: Analyze via skill-analyzer-agent

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
    artifacts, findings.json, or any other file in the worktree. Do NOT use the Write, Edit,
    NotebookEdit, or Skill tools. Do NOT invoke any skill (e.g., cat:grep-and-read-agent,
    cat:batch-read-agent, or any other cat: skill).
    Bash commands are restricted to the following allowlist of read-only commands:
    cat, head, tail, grep, wc, sort, uniq, diff, stat.
    Do NOT use find, ls, or the Glob tool to discover or enumerate files.
    The ONLY files you may read or access (via cat, head, tail, grep, wc, sort, uniq, diff, stat,
    or the Read tool) are:
    (1) the skill file at {WORKTREE_ROOT}/{SKILL_TEXT_PATH} and (2) the benchmark results file
    whose path is provided to you. Do NOT use any allowlisted command against any file not in this list.
    Do NOT read test-cases.json, benchmark.json,
    protected-sections.txt, or any file under {BENCHMARK_ARTIFACTS_DIR} other than the specific
    benchmark results file provided.
    Do NOT use any Bash command not on this allowlist. In particular, do NOT use shell redirection
    operators (>, >>) or any command that writes, moves, copies, or deletes files (rm, mv, cp,
    sed -i, tee, dd, truncate, install, patch, echo/printf with redirection, etc.).
```

The subagent returns the analysis commit SHA and the compact analysis report text. The main agent receives
only the compact analysis report text (~1KB). It does NOT read the analysis file.

**Display results to user:** Present the SPRT benchmark summary and the analysis report text. Ask the user:
1. Are there any test cases to remove or replace based on the pattern analysis?
2. Would you like to improve the skill and re-run the benchmark?
3. Are you satisfied with the current skill version?

**Iterate if needed:** If the user requests improvement, apply targeted changes to the skill file at
`SKILL_TEXT_PATH`, commit the updated file, and update `SKILL_DRAFT_SHA` before returning to Step 4.3. Cap
at 5 benchmark iterations total. Track the best-performing iteration by storing `BEST_SCORE` and `BEST_SHA`
(the commit SHA of the skill file at that iteration). `BEST_SCORE` is defined as the fraction of test cases
that reached SPRT Accept. After each iteration, compare the current BEST_SCORE and update if higher. Stop
iterating if the absolute improvement between consecutive rounds is less than 5 percentage points — restore
the best skill version by running `git checkout {BEST_SHA} -- {SKILL_TEXT_PATH}` and committing with
message `benchmark: restore best iteration [session: ${CLAUDE_SESSION_ID}]`, then report "Benchmark plateau
reached." If the iteration cap is reached, apply the same rollback to `BEST_SHA` if the final iteration is
not the best, then stop and report "Benchmark iteration cap reached (5 rounds) — presenting best result."

### Step 5: Adversarial TDD Loop

After the benchmark phase converges, harden the instructions using alternating red-team and blue-team
subagents. Run until convergence (no CRITICAL/HIGH loopholes remain).

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/config.json`. If `effort = low`, skip
adversarial hardening entirely and proceed to Step 6.

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

### Step 6: In-Place Hardening Mode (Optional)

**BLOCKING — Do NOT implement this loop manually.** Reading this section does not authorize direct
execution of the hardening algorithm. You are NOT the hardening engine — you are the orchestrator.

The ONLY valid execution path is:
- Spawn red-team and blue-team subagents using the **Task tool** as defined in Step 5
- Let the subagents read the target file from `SKILL_FILE_PATH` on disk, execute the loop, and commit changes

**Prohibited paths (will be treated as a protocol violation):**
- Manually performing any part of the hardening loop yourself — including red-team analysis, blue-team
  patching, arbitration, or diff validation — without a Task tool subagent
- Delegating to `cat:work-execute` — this is an implementation subagent, not a hardening subagent
- Delegating to any non-Task-tool path
- Announcing "executing instruction-builder in-place hardening mode" and then doing it yourself

If you are reading this and thinking "I should now run the loop", stop — you are primed incorrectly.
Return to Step 5 and spawn Task tool subagents.

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/config.json`. If `effort = low`, skip
in-place hardening entirely and report "Skipping in-place hardening (effort=low)." to the user.

In-place hardening mode runs the adversarial TDD loop against a skill file in a worktree in a single session,
producing one commit per round as the loop progresses.

**Primary workflow — single skill file:**

In-place hardening mode activates when the caller passes a single skill file path inside the current worktree.
This mode is intended for hardening existing, already-functional skills — it applies adversarial instruction
review only and does NOT run the benchmark evaluation loop (Step 4). Before entering in-place mode,
the orchestrator must verify that a prior benchmark exists for this skill by checking whether
`<skill-dir>/benchmark/benchmark.json` exists (where `<skill-dir>` is the directory containing the target
skill file). If no prior benchmark is found, the orchestrator must abort in-place mode and
fall back to the full workflow (Steps 1-5) with the message: "No prior benchmark found for this skill —
running full workflow including benchmark evaluation."

1. Store the file path as `SKILL_FILE_PATH`. Do NOT read the file into `CURRENT_INSTRUCTIONS` and relay
   it inline to subagents — subagents read the file from `SKILL_FILE_PATH` themselves. Determine
   the worktree root by running `git rev-parse --show-toplevel` from within the worktree; store as
   `WORKTREE_ROOT`. Pass `WORKTREE_ROOT` to all red-team and blue-team subagent prompts so they can
   construct absolute paths for **direct filesystem operations** (e.g., `cat {WORKTREE_ROOT}/findings.json`,
   `mkdir -p {WORKTREE_ROOT}/...`). For `git show` commands, subagents must use repo-relative paths
   (e.g., `git show <sha>:findings.json`) as specified in the shared adversarial protocol.
2. Run the full RED→BLUE loop as defined in Step 5 and the shared adversarial protocol. Each round
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
red-team and blue-team subagent prompts via a `FINDINGS_PATH` parameter. The subagent prompt MUST
include an explicit instruction: "Write findings to {FINDINGS_PATH} instead of
{WORKTREE_ROOT}/findings.json. All reads and writes of findings.json in your procedure are
redirected to this path." This overrides the default `{WORKTREE_ROOT}/findings.json` in the
receiving agent's procedure.
Parallel subagents must not commit shared files (e.g., index files or aggregated docs) to avoid merge
conflicts; those are updated once after all parallel subagents complete. The concurrent commit safety
retry protocol (exponential backoff with jitter, up to 3 retries) from Step 4 also applies to all
red-team and blue-team commits in batch parallel mode. Each parallel subagent must retry on ref-lock
contention using the same backoff schedule: first retry after 1-2 seconds (randomized), second after
2-4 seconds, third after 4-8 seconds.

Skip files that are not valid skill files (missing Purpose or Procedure sections). If a skill file fails
validation after blue-team patching, log the failure and continue to the next skill.

After all skill files are processed (or user types `abort`), display a batch summary table:

| Skill | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|-----------------|-----------------|-----------------|
| ...   | ...    | ...             | ...             | ...             |

### Step 7: Compression Phase

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.cat/config.json`. If `effort = low`, skip
this entire step. The compression phase runs only when `effort = medium` or `high`.

After hardening achieves compliance (Step 5 converges and SPRT re-benchmark accepts), compress the skill
file to minimize token cost while preserving behavioral compliance.

**Hardening + benchmarking + compression are always run together** — never one without the others when
effort > low.

**Sequential phases, never interleaved:**
1. Harden until compliant (only add text) — Step 5
2. Compress to minimize size (only remove text) — Step 7
3. Re-benchmark to verify compression preserved compliance — Step 7 SPRT re-benchmark
4. If compliance dropped, mark load-bearing text as protected and retry compression (up to 3 times)

#### Step 7.1: Post-Hardening SPRT Re-Benchmark

Reset `BEST_SCORE` and `BEST_SHA` before this step — hardening may have changed the skill, so
pre-hardening iteration tracking is stale and must not be used for rollback.

Before compressing, run a full SPRT benchmark on the hardened skill to confirm compliance. Use the same
test cases from `${BENCHMARK_ARTIFACTS_DIR}/test-cases.json` and identical SPRT parameters as Step 4.3.
Commit benchmark results with message `benchmark: post-hardening SPRT [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `POST_HARDENING_SHA`. If any test case rejects, return to hardening (Step 5) to address the
failures before proceeding to compression.

#### Step 7.2: Compress

Before invoking the compression subagent, derive `{skill-filename}` as the basename of `SKILL_TEXT_PATH`
(e.g., `first-use` from `plugin/skills/my-skill/first-use.md`). Sanitize `{skill-filename}` by stripping
any path separator characters (`/`, `\`, `..`) — reject and abort if the basename contains path traversal
sequences. The resulting filename must be a simple name with no directory components.

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

    RESTRICTION: The ONLY files you may read (via cat, head, tail, grep, wc, sort, uniq, diff, stat,
    the Read tool, or any other mechanism) are: (1) the skill file at {SKILL_TEXT_PATH} (this is the
    input to compress), (2) {BENCHMARK_ARTIFACTS_DIR}/protected-sections.txt (if provided), and
    (3) ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/compression-protocol.md.
    Do NOT use any allowlisted command (including diff, sort, uniq, stat) against any file not in
    this list — doing so constitutes a file read even if the command is nominally "read-only".
    Do NOT read any other file — including test-cases.json, benchmark.json, findings.json, config
    files, peer subagent output files, or any file not listed in (1)-(3) regardless of its location.
    Do NOT list or explore the skill directory's benchmark/ subdirectory. Do NOT use the Glob tool to
    discover or enumerate files. Do NOT use grep with recursive flags (-r, -R, --include, -l combined
    with directory paths) as this provides directory discovery. Do NOT modify {SKILL_TEXT_PATH}. Use
    the Write tool to write the compressed output to
    {BENCHMARK_ARTIFACTS_DIR}/compressed-{skill-filename}.md — this is the ONLY file you may write
    and the Write tool is the ONLY permitted mechanism for writing it. Do NOT use the Edit tool,
    NotebookEdit tool, or the Skill tool. Do NOT invoke any skill (e.g., cat:batch-write-agent,
    cat:grep-and-read-agent, cat:batch-read-agent, or any other cat: skill).
    Bash commands are restricted to the following allowlist of read-only commands: cat, head, tail,
    grep, wc, sort, uniq, diff, stat. Do NOT use any Bash command not on this allowlist. Do NOT use
    shell redirection operators (>, >>) or any command that writes, moves, copies, or deletes files.
```

Commit the compressed file with message `benchmark: compress skill [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `COMPRESSED_SHA`.

#### Step 7.3: Semantic Pre-Check (Fast Gate)

Before running the full SPRT re-benchmark, run a semantic pre-check using the comparison algorithm from
`${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/validation-protocol.md` (Section 2):

1. Extract semantic units from the original hardened skill (using Section 1 extraction algorithm)
2. Extract semantic units from the compressed skill
3. Compare units using the Section 2 comparison algorithm
4. If any LOST unit has severity HIGH (PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION) → skip SPRT and
   retry compression immediately (mark the lost unit's text as protected)
5. If semantically EQUIVALENT or only MEDIUM/LOW losses → proceed to SPRT re-benchmark

#### Step 7.4: Post-Compression SPRT Re-Benchmark

Run SPRT re-benchmark on the compressed version using identical test cases and parameters as Step 7.1.

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
`benchmark: accept final [session: ${CLAUDE_SESSION_ID}]`. Do NOT commit the post-compression SPRT results
separately — they are intermediate verification artifacts only. The accepted compressed skill file is the
permanent output; the SPRT run that verified it is transient and must not be persisted to the repository.

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
  procedures — `plugin/agents/skill-analyzer-agent.md`

## Verification

- [ ] Design subagent returned a complete skill draft
- [ ] Benchmark phase ran with auto-generated test cases (one per testable semantic unit)
- [ ] Test cases include both deterministic and semantic assertion types where appropriate
- [ ] Skill-builder maximizes deterministic-to-semantic assertion ratio when generating test cases
- [ ] Benchmark commit message prefix uses `benchmark:`
- [ ] Benchmark artifacts directory is the skill-adjacent `<skill-dir>/benchmark/` (derived from dirname of SKILL_TEXT_PATH)
- [ ] Variables use `BENCHMARK_ARTIFACTS_DIR` and `BENCHMARK_SET_SHA`
- [ ] SPRT decision logic uses p0=0.95, p1=0.85, α=0.05, β=0.05 with boundaries A≈2.944, B≈-2.944
- [ ] Each test case runs its own independent SPRT; rejection of any case stops all remaining cases
- [ ] SPRT check runs after each individual agent completion (pipelined), not batched per wave
- [ ] BENCHMARK_MODEL is read from skill frontmatter via `${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner extract-model` at the start of Step 4
- [ ] Eval-run subagents use `BENCHMARK_MODEL` (not hardcoded Haiku), fresh (non-resumed) per run
- [ ] Eval-run subagents grade deterministic assertions inline before returning
- [ ] Semantic assertions use separate `BENCHMARK_MODEL` grader subagents
- [ ] Run outputs written to temp files (not committed per-run); only benchmark.json is committed
- [ ] Eval-run subagent return format includes per-assertion pass/fail with null for semantic assertions
- [ ] BENCHMARK_ARTIFACTS_DIR, CLAUDE_SESSION_ID, and BENCHMARK_MODEL passed as resolved literal strings to all subagents
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
- [ ] Step 5 follows shared adversarial protocol from plugin/concepts/adversarial-protocol.md
- [ ] Shared protocol performs final-round MEDIUM/LOW cleanup pass (blue-team only, no arbitration/diff-validation) before loop exit
- [ ] Step 5 uses target_type: skill_instructions
- [ ] Step 5 main agent never reads findings.json directly — uses structured JSON returns from subagents
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows Loopholes Closed, Disputes Upheld, and Patches Applied columns
- [ ] Step 2 design subagent tool prohibition explicitly lists NotebookEdit alongside other prohibited tools
- [ ] Step 6 prohibited paths cover all hardening loop phases (red-team, blue-team, arbitration, diff validation)
- [ ] Step 5 arbitration agent scope restriction prohibits modifying any file other than findings.json
- [ ] Step 6 sequential batch mode requires deleting findings.json between skills to prevent contamination
- [ ] Step 2 design subagent validation verifies Read tool was used only on permitted files
- [ ] Step 5 blue-team patch constraints explicitly protect verification checklist items from removal or weakening
- [ ] Step 5 does not embed file content inline in subagent prompts — subagents read from TARGET_FILE_PATH
- [ ] Step 6 in-place mode verifies prior benchmark existence before skipping Steps 1-4 (checking `<skill-dir>/benchmark/benchmark.json`)
- [ ] Step 6 batch mode uses per-skill findings paths (`findings-<skill-name>.json`) to avoid collisions
- [ ] Step 6 batch mode passes `FINDINGS_PATH` parameter to override default findings.json path in subagent prompts
- [ ] Step 6 distinguishes filesystem operations (use WORKTREE_ROOT prefix) from git show (use repo-relative paths)
- [ ] Step 4.4 failure investigation runs automatically when SPRT overall_decision is "Reject"
- [ ] Step 4.4 identifies rejected test case IDs from benchmark.json before examining transcripts
- [ ] Step 4.4 uses session-analyzer to discover and examine benchmark-run subagent IDs for failing runs
- [ ] Step 4.4 checks for batch contamination (multiple runs sharing one subagent context)
- [ ] Step 4.4 checks for priming sources (model defaults, escape clauses, output format priming)
- [ ] Step 4.4 presents investigation findings to the user before asking whether to improve the skill
- [ ] Step 4.4 recommends rerunning the benchmark when conclusion is "test environment artifact"
- [ ] Step 4.5 skill-analyzer-agent report includes Delegation Opportunities and Content Relay Anti-Patterns sections
- [ ] Step 2 design subagent Read scope is restricted to methodology, conventions, and existing skill files only
- [ ] Step 6 batch mode skill-name derivation is defined as `<directory-name>-<file-stem>` to avoid collisions
- [ ] Step 6 batch parallel mode extends the concurrent commit retry protocol to red-team and blue-team commits
- [ ] Step 4.3 benchmark plateau tracks BEST_SCORE and BEST_SHA, rolls back to best iteration on plateau or cap
- [ ] Step 5 arbitration agent prompt includes explicit tool restrictions (Write/Edit limited to findings.json, no state-modifying Bash)
- [ ] Semantic extraction uses embedded Nine-Category algorithm from validation-protocol.md (not subagent invocation)
- [ ] Extracted units include id, category, original, normalized, quote, and location fields
- [ ] Non-testable units (REFERENCE, CONJUNCTION) are skipped when generating test cases
- [ ] Auto-generated test cases are presented to the user for approval before benchmarking
- [ ] User can add, remove, or modify auto-generated test cases
- [ ] Benchmark-run subagent return format includes `duration_ms` and `total_tokens` fields
- [ ] benchmark.json sprt section includes `total_tokens` and `total_duration_ms` per test case and overall
- [ ] Token usage summary table is displayed after SPRT completes showing per-test-case and aggregate totals
- [ ] Token counts are accumulated from each benchmark-run subagent return value (not estimated)
- [ ] `test-cases.json` and `benchmark.json` are committed directly to `<skill-dir>/benchmark/` after SPRT completes (no separate persist step)
- [ ] `${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner extract-model` falls back to "haiku" when skill has no model: frontmatter field
- [ ] Step 3 compact-output pass applied to SKILL_DRAFT before writing to disk
- [ ] Step 3 compact-output pass lists all correctness exemptions (YAML frontmatter, Makefile targets, fenced code blocks, semantic whitespace, visual alignment)
- [ ] Step 3 compact-output pass does not modify SKILL_DRAFT when exemption conditions apply
- [ ] Both semantic and visual correctness take priority over compactness in the compact-output pass
