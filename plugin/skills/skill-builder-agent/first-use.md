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

**Generate test cases:** Create 2-3 test cases with assertions to measure the skill's effectiveness.

**Spawn parallel benchmark runs:** For each test case, spawn two subagents simultaneously:
- One with the skill active (`with-skill`)
- One with the skill inactive (`without-skill`)

**Grade and aggregate results:** Collect all run outputs, grade them against assertions, and aggregate
the results using the BenchmarkAggregator tool.

**Analyze patterns:** Invoke skill-analyzer-agent to identify non-discriminating assertions, high-variance
evals, and time/token tradeoffs.

**Display results to user:** Present the benchmark summary table and pattern analysis report. Ask the user:
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

**Termination criteria:** No major loopholes = red-team's findings file contains no CRITICAL or HIGH severity
issues. Check by reading the findings file from the red-team's returned commit hash. LOW/MEDIUM concerns may
remain if they require disproportionate instruction complexity to close.

**Termination verification:** Read findings.json using `git show {RED_TEAM_COMMIT_HASH}:findings.json` and scan
the `loopholes` array. If any entry has `"severity": "CRITICAL"` or `"severity": "HIGH"`, continue the loop.
If no such entries exist, STOP — instructions are sufficiently hardened. If the round counter reaches 10 without
convergence, stop and report "Loop cap reached at 10 rounds — {N} CRITICAL/HIGH loopholes remain unresolved."

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
```

**Error handling:**
- If red-team does not return a commit hash on its last line: log an error and abort the loop.
- If `git show {RED_TEAM_COMMIT_HASH}:findings.json` fails or returns empty: log "ERROR: findings.json not readable from commit {RED_TEAM_COMMIT_HASH}" and abort.
- If findings.json is malformed (not valid JSON or missing `loopholes` array): log the error and abort. Do not default to empty.
- Apply the same commit-hash validation to blue-team: abort if no valid commit hash is returned.

After blue-team completes, read `BLUE_TEAM_COMMIT_HASH` from the last line of its response.

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

    First, analyze the diff above to determine whether the blue-team's patches introduced new gaps or
    failed to fully close prior loopholes. Then, re-examine the FULL current instructions (not just the diff)
    for attack vectors not yet explored in previous rounds — the diff focus must not prevent discovery of
    loopholes present in unchanged sections.
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

    Apply fixes for the new loopholes identified in this round's findings.
    Write the revised skill file and commit as before, returning only the commit hash.
```

### Step 5: In-Place Hardening Mode (Optional)

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

| Skill | Rounds | Loopholes Closed |
|-------|--------|-----------------|
| ...   | ...    | ...             |

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

## Verification

- [ ] Design subagent returned a complete skill draft
- [ ] Benchmark phase ran with 2+ test cases
- [ ] Benchmark results show meaningful signal (non-zero pass rate differential)
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
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows all skills reviewed with round counts
