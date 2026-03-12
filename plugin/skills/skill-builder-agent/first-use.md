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
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/design-methodology.md

    ## Skill Writing Conventions
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/skill-conventions.md

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
Commit the file with message `eval: write skill draft [session: ${CLAUDE_SESSION_ID}]` and store the commit SHA
as `SKILL_DRAFT_SHA`. The skill text is now on disk and committed, so subagents can read it via
`git show <SHA>:<SKILL_TEXT_PATH>` or `cat <SKILL_TEXT_PATH>`.

Run the benchmark evaluation loop to measure the skill's impact quantitatively.

At the start of Step 3, compute `EVAL_ARTIFACTS_DIR` as `<worktree-root>/eval-artifacts/${CLAUDE_SESSION_ID}`
(expanding `${CLAUDE_SESSION_ID}` to its actual value). Pass this resolved path as a literal string to all
subagents — do NOT pass variable references.

**Context isolation:** `EVAL_ARTIFACTS_DIR` includes the session ID in its path, ensuring that concurrent
eval-run subagents from different sessions write to separate directories and never collide. Each eval-run
subagent receives `EVAL_ARTIFACTS_DIR` and `CLAUDE_SESSION_ID` as pre-resolved literal strings, so no
subagent ever expands these variables independently. Subagents must not derive their own session ID — they
must use the value passed by the main agent.

**Generate test cases:** Create 2-3 test cases with assertions. Each test case must have at least 2
assertions, and at least one assertion per test case must test the skill's differentiating behavior
(i.e., something the without-skill configuration is expected to fail). Assertions must test specific
output properties — generic assertions like "output is non-empty" or "output contains text" are
prohibited. Store the eval set JSON in
`${EVAL_ARTIFACTS_DIR}/eval-set.json` and commit it with message
`eval: write test cases [session: ${CLAUDE_SESSION_ID}]`. Store the commit SHA as `EVAL_SET_SHA`. The main
agent does NOT retain the eval set JSON in context — grader subagents read assertions from the committed
file via `git show {EVAL_SET_SHA}:eval-artifacts/<SESSION_ID>/eval-set.json` (repo-relative path) or
`cat {EVAL_ARTIFACTS_DIR}/eval-set.json` (absolute path for direct file access).

**Spawn parallel eval-run subagents:** For each test case, spawn two eval-run subagents simultaneously — one
with the skill active (`with-skill`, the skill file is present at `SKILL_TEXT_PATH`) and one with the skill
inactive (`without-skill`, the skill file content is NOT included in the subagent's prompt context — the
file remains on disk but the without-skill subagent must NOT read it). Both the with-skill and
without-skill subagent prompts must include the prohibition against reading eval-artifacts: "Do NOT read
eval-set.json, grading files, run-output files from other subagents, or any file under eval-artifacts/
via any mechanism (Read tool, Bash, Grep, or otherwise). This prohibition is absolute — it applies
regardless of the file's content or purpose, including peer subagent output files." For the with-skill
subagent, this is the only eval-artifact prohibition needed. The without-skill subagent prompt
must include an explicit prohibition: "Do NOT read or reference the file at {SKILL_TEXT_PATH} via any
mechanism — this includes the Read tool, Bash cat/head/tail, Grep, or any other tool. You are
running in the without-skill configuration. Do NOT read eval-set.json, grading files, or any file under
eval-artifacts/ via any mechanism (Read tool, Bash, Grep, or otherwise) — these contain information
about the skill's expected behavior. Do NOT run any git
command that could reveal the skill's content or purpose — this includes but is not limited to git log,
git show, git diff, git rev-list, git shortlog, git format-patch, and any other command that outputs
committed content or commit messages. Your task is to respond using only your baseline capabilities
without any skill-derived context." Pass each
subagent only scalar references
(test case ID, config name, `EVAL_ARTIFACTS_DIR`, `CLAUDE_SESSION_ID`) — do NOT embed test case content or
assertion arrays inline in the prompt. Each eval-run subagent:
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
- The eval set file path: `${EVAL_ARTIFACTS_DIR}/eval-set.json` (grader reads assertions from disk — do NOT
  embed the assertions array inline in the grader prompt)
- The test case ID and config name
- The `${EVAL_ARTIFACTS_DIR}` path and `${CLAUDE_SESSION_ID}` (both already resolved to literal strings)

The grader subagent prompt must include the prohibition: "Do NOT read the skill file at {SKILL_TEXT_PATH}
or access its content via any mechanism. Do NOT run any git command that could reveal the skill's content
or purpose — this includes but is not limited to git log, git show, git diff, git rev-list, git shortlog,
git format-patch, and any other command that outputs committed content or commit messages. Grade the run
output solely against the assertions in eval-set.json — do not use skill content to inform grading
decisions."

**Grader subagent return contract:** Each grader subagent returns only a commit SHA (a bare hex string with
no JSON wrapper). The grading file path is deterministic: `eval-artifacts/<SESSION_ID>/grading/<case-id>-<config>.json`.
The main agent reconstructs the full SHA+path pair for the aggregator by combining the returned SHA with
this naming convention — it does not ask the grader to return the path.

**Concurrent commit safety:** All parallel subagents (eval-run and grader) for a single benchmark pass are
spawned in the same turn, but they each commit to the same worktree. Git serializes commits internally, so
parallel subagents may briefly contend for the ref lock. If any subagent's `git commit` fails with "cannot
lock ref" or similar, it must retry up to 3 times with exponential backoff and jitter: first retry after
1-2 seconds (randomized), second after 2-4 seconds, third after 4-8 seconds. If all retries fail, return
`{"error": "commit failed: <reason>"}`. The main agent does not need to handle this retry — it is the subagent's
responsibility. This retry protocol applies to both eval-run subagents and grader subagents.

Each grader subagent returns a commit SHA for its `grading/<case-id>-<config>.json` file, or
`{"error": "..."}` on failure. If the `git show` command used to read the run output fails (e.g., SHA not
found, path missing), the grader returns `{"error": "git show failed: <reason>"}` and stops — it does not
produce partial grading. The main agent collects only the commit SHAs from grader subagents. It does NOT
read the grading JSON files. If a grader subagent returns an error, exclude that test case from the
aggregation input; if all grading for a config fails, report the failure and ask the user whether to retry.

**Aggregate via BenchmarkAggregator subagent:** Spawn one subagent to perform aggregation. The aggregator
subagent prompt must include the restriction: "Do NOT modify grading files, run-output files, the skill
file, or any existing file in the worktree. You may only create and write to
${EVAL_ARTIFACTS_DIR}/benchmark.json. Do NOT use the Edit tool. Do NOT use Write or Bash to modify any
file other than benchmark.json." Pass it:
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
(from the aggregator subagent return value) and `SKILL_TEXT_PATH` (the worktree-relative path written and
committed at the start of Step 3). The `skill_text_path` must be a **worktree-relative path** (e.g.,
`plugin/skills/my-skill/first-use.md`) — never an absolute filesystem path. The subagent reads the benchmark
via `git show <benchmark_sha>:<benchmark_path>` and the skill text via `cat <skill_text_path>` (since the
skill draft commit may differ from the benchmark commit) — do NOT load and relay file contents.

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

    ## Eval Artifacts
    EVAL_ARTIFACTS_DIR: {EVAL_ARTIFACTS_DIR}
    CLAUDE_SESSION_ID: {CLAUDE_SESSION_ID}

    Read the skill text using: cat {WORKTREE_ROOT}/{SKILL_TEXT_PATH}
    (SKILL_TEXT_PATH is worktree-relative; prepend WORKTREE_ROOT for the absolute path.)

    RESTRICTION: This is a read-only analysis task. Do NOT modify the skill file, eval
    artifacts, findings.json, or any other file in the worktree. Do NOT use the Write or
    Edit tools. Do NOT use Bash to run commands that modify files (rm, mv, sed -i, tee, etc.).
    You may only use Read and Bash (read-only commands like git show, cat, grep) to gather data.
```

The subagent returns the analysis commit SHA and the compact analysis report text. The main agent receives
only the compact analysis report text (~1KB). It does NOT read the analysis file.

**Display results to user:** Present the benchmark summary table (from the aggregator subagent return) and
the analysis report text (from skill-analyzer-agent return). Ask the user:
1. Are there any assertions to remove or replace based on the pattern analysis?
2. Would you like to improve the skill and re-run the benchmark?
3. Are you satisfied with the current skill version?

**Iterate if needed:** If the user requests improvement, apply targeted changes to the skill file at
`SKILL_TEXT_PATH`, commit the updated file, and update `SKILL_DRAFT_SHA` before returning to the benchmark
loop. Cap at 5 benchmark iterations total. Track the best-performing iteration by storing `BEST_SCORE`
and `BEST_SHA` (the commit SHA of the skill file at that iteration). `BEST_SCORE` is defined as the
**with-skill assertion pass rate** from the benchmark aggregator output — specifically, the percentage of
assertions that passed in the `with-skill` configuration (number of passed assertions / total assertions
across all test cases, expressed as a percentage). Do not use the without-skill pass rate, the differential,
or any other metric. After each benchmark iteration, compare the current with-skill assertion pass rate to
`BEST_SCORE` and update both if the current score is higher. Stop iterating if the **absolute improvement**
(current pass rate minus previous pass rate, in percentage points) between consecutive rounds is less than
5 percentage points — restore the best skill version by running `git checkout {BEST_SHA} -- {SKILL_TEXT_PATH}` and
committing with message `eval: restore best iteration [session: ${CLAUDE_SESSION_ID}]`, then report
"Benchmark plateau reached: pass rate improvement below 5% threshold" and present the best result to the
user. If the iteration cap is reached, apply the same rollback to `BEST_SHA` if the final iteration is not
the best, then stop and report "Benchmark iteration cap reached (5 rounds) — presenting best result."

### Step 4: Adversarial TDD Loop

After the benchmark phase converges, harden the instructions using alternating red-team and blue-team
subagents. Run until convergence (no CRITICAL/HIGH loopholes remain).

**Effort gate:** Read `effort` from `${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json`. If `effort = low`, skip
adversarial hardening entirely and proceed to the next step.

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
`eval-artifacts/*/benchmark.json` contains an entry whose skill path matches the target file (search via
`git log --all --oneline -- 'eval-artifacts/*/benchmark.json'` to find benchmark commits, then verify at
least one exists). If no prior benchmark is found, the orchestrator must abort in-place mode and fall back
to the full workflow (Steps 1-4) with the message: "No prior benchmark found for this skill — running
full workflow including benchmark evaluation."

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
is allowed when each
skill file is independent (no shared skill-to-skill dependencies). In parallel mode, each subagent runs the
full RED→BLUE loop for its own file, committing per-round — never touching other skill files. Each
parallel subagent must use a skill-specific findings path (`{WORKTREE_ROOT}/findings-<skill-name>.json`)
instead of the shared `{WORKTREE_ROOT}/findings.json` to avoid overwrite collisions between concurrent
red-team agents. Derive `<skill-name>` as `<directory-name>-<file-stem>` (e.g.,
`work-agent-first-use` for `plugin/skills/work-agent/first-use.md`, `git-commit-agent-SKILL` for
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
- **skill-analyzer-agent**: Detects delegation opportunities and content relay anti-patterns in skill
  procedures — `plugin/agents/skill-analyzer-agent/SKILL.md`

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
- [ ] Step 3 SKILL_DRAFT is written to disk and committed before invoking skill-analyzer-agent
- [ ] Step 3 SKILL_TEXT_PATH is a worktree-relative path (not absolute), suitable for `git show <SHA>:<path>`
- [ ] Step 3 Task prompt template passes `skill_text_path` to skill-analyzer-agent alongside benchmark SHA+path
- [ ] Step 3 skill-analyzer-agent reads skill text via `cat` (not `git show` with benchmark SHA, since commits differ)
- [ ] skill-analyzer-agent report includes Delegation Opportunities and Content Relay Anti-Patterns sections
  when skill_text_path is provided
- [ ] Step 3 iteration re-writes and re-commits skill file at SKILL_TEXT_PATH before re-running analyzer
- [ ] Adversarial TDD loop completed (converged)
- [ ] Final skill document includes purpose, procedure, and verification sections
- [ ] Frontmatter description is trigger-oriented and contains no implementation details
- [ ] No Functions or Prerequisites sections prime manual construction (per priming prevention checklist)
- [ ] Step 4 follows shared adversarial protocol from plugin/concepts/adversarial-protocol.md
- [ ] Shared protocol performs final-round MEDIUM/LOW cleanup pass (blue-team only, no arbitration/diff-validation) before loop exit
- [ ] Step 4 uses target_type: skill_instructions
- [ ] Step 4 main agent never reads findings.json directly — uses structured JSON returns from subagents
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows Loopholes Closed, Disputes Upheld, and Patches Applied columns
- [ ] Step 2 design subagent tool prohibition explicitly lists Grep alongside Bash, Write, Edit, Glob, WebFetch, WebSearch
- [ ] Step 2 draft validation checks that each required section contains non-empty content (not just headings)
- [ ] Step 3 without-skill config does not delete or rename the skill file — it omits the file from the subagent's prompt context
- [ ] Step 4 documents that the agent-specific findings.json schema (name/severity/attack/evidence) takes precedence over the protocol's generic schema
- [ ] Step 3 concurrent commit safety retry protocol covers both eval-run and grader subagents
- [ ] Step 5 batch mode uses per-skill findings paths (`findings-<skill-name>.json`) to avoid collisions
- [ ] Step 4 does not embed file content inline in subagent prompts — subagents read from TARGET_FILE_PATH
- [ ] Step 2 design subagent Read scope is restricted to methodology, conventions, and existing skill files only
- [ ] Shared protocol blue-team patch constraints prohibit removing capabilities to close loopholes
- [ ] Step 3 git show commands for eval artifacts use repo-relative paths (`eval-artifacts/<SESSION_ID>/...`)
- [ ] Step 5 batch mode skill-name derivation is defined as `<directory-name>-<file-stem>` to avoid collisions
- [ ] Step 5 batch mode passes `FINDINGS_PATH` parameter to override default findings.json path in subagent prompts
- [ ] Step 5 distinguishes filesystem operations (use WORKTREE_ROOT prefix) from git show (use repo-relative paths)
- [ ] Step 3 benchmark plateau tracks BEST_SCORE and BEST_SHA, rolls back to best iteration on plateau or cap
- [ ] Step 3 without-skill subagent prompt explicitly prohibits reading the skill file via Read tool
- [ ] Step 2 design subagent tool prohibition explicitly lists NotebookEdit alongside other prohibited tools
- [ ] Step 3 without-skill subagent prompt prohibits reading eval-artifacts, grading files, and git history
- [ ] Step 5 prohibited paths cover all hardening loop phases (red-team, blue-team, arbitration, diff validation)
- [ ] Step 4 arbitration agent scope restriction prohibits modifying any file other than findings.json
- [ ] Step 3 BEST_SCORE is defined as with-skill assertion pass rate; improvement is absolute percentage points
- [ ] Step 5 sequential batch mode requires deleting findings.json between skills to prevent contamination
- [ ] Step 3 without-skill subagent prompt prohibits all git commands that reveal committed content (not just git log/show)
- [ ] Step 2 design subagent validation verifies Read tool was used only on permitted files
- [ ] Step 4 blue-team patch constraints explicitly protect verification checklist items from removal or weakening
- [ ] Step 3 grader subagent prompt prohibits reading the skill file at SKILL_TEXT_PATH
- [ ] Step 3 concurrent commit retry uses exponential backoff with jitter (not undefined "short delay")
- [ ] Step 3 without-skill subagent prohibition explicitly names the Read tool alongside Bash/Grep as blocked mechanisms
- [ ] Step 3 test case assertions require at least 2 per case with at least one testing skill-differentiating behavior
- [ ] Shared protocol diff-validation includes scope check: every hunk must map to a specific loophole
- [ ] Step 3 both with-skill and without-skill subagents are prohibited from reading eval-artifacts
- [ ] Step 4 arbitration agent prompt includes explicit tool restrictions (Write/Edit limited to findings.json, no state-modifying Bash)
- [ ] Step 5 batch parallel mode extends the concurrent commit retry protocol to red-team and blue-team commits
- [ ] Step 3 grader subagent prompt prohibits git commands that reveal skill content (matching without-skill exhaustive prohibition)
- [ ] Step 5 in-place mode verifies prior benchmark existence before skipping Steps 1-3
- [ ] Step 3 eval-artifacts prohibition explicitly names peer run-output files and uses absolute scope (not rationale-dependent)
- [ ] Step 3 aggregator subagent prompt includes write restrictions limiting output to benchmark.json only
- [ ] Step 3 skill-analyzer-agent prompt includes read-only restriction prohibiting Write/Edit tools and file-modifying Bash
