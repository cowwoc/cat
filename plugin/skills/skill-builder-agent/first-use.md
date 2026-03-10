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
    Do NOT spawn subagents. Do NOT invoke the Task tool.
```

The design subagent should only read files and return SKILL_DRAFT. If the response includes Task tool
invocations or evidence of subagent spawning, treat as constraint violation and reject the draft.

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
to the benchmark loop. Repeat until the user is satisfied or pass rate shows no improvement.

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

**Termination verification:** Read the `major_loopholes_found` field from findings.json using `git show {RED_TEAM_COMMIT_HASH}:findings.json`. If the field is absent, abort with an error. If the round counter reaches 10 without convergence, stop and report "Loop cap reached at 10 rounds — {N} CRITICAL/HIGH loopholes remain unresolved."

**Round 1 — spawn persistent agents:**

Spawn the red-team agent using the Task tool and store its `task_id` as `RED_TEAM_TASK_ID`:

```
Task tool:
  description: "Red-team: find loopholes in instructions (round 1)"
  subagent_type: "general-purpose"
  prompt: |
    You are a red-team agent. Your job: find concrete ways to defeat or circumvent these instructions.
    You will be resumed in later rounds — seek NEW attack vectors each round, do not revisit prior findings.

    ## Instructions to Attack
    {CURRENT_INSTRUCTIONS}

    ## Your Task
    For each loophole you find, provide:
    1. **Loophole name**: brief slug (e.g., "bash-file-write-bypass")
    2. **Severity**: CRITICAL | HIGH | MEDIUM | LOW
    3. **Attack**: exact agent reasoning or action that defeats the rule (be specific — quote
       undefined terms, name unlisted tools, describe the rationalization the agent would use)
    4. **Evidence**: why the current instructions permit this (quote the permissive text or
       identify the missing prohibition)

    Do NOT suggest fixes. Do NOT be vague. If you cannot find a concrete attack for a concern,
    do not include it.

    ## Output
    Write your findings to the file: findings.json
    Use this JSON format:
    {
      "loopholes": [
        {
          "name": "bash-file-write-bypass",
          "severity": "CRITICAL",
          "attack": "Agent uses Bash sed -i to modify file, bypassing Edit/Write prohibition",
          "evidence": "Rule says 'MUST NOT use Edit or Write tools' but does not mention Bash"
        }
      ],
      "major_loopholes_found": true
    }
    Set major_loopholes_found to true if any CRITICAL or HIGH severity loopholes exist.

    After writing findings.json, run: git add findings.json && git commit -m "red-team: round 1 findings"
    Return only the commit hash on the last line of your response.
```

After red-team completes, read `RED_TEAM_COMMIT_HASH` from the last line of its response.
Read `findings.json` at that commit to determine whether `major_loopholes_found` is true.

If `major_loopholes_found: false`: STOP loop — instructions are sufficiently hardened.

Spawn the blue-team agent using the Task tool and store its `task_id` as `BLUE_TEAM_TASK_ID`. The blue-team
MUST start only after red-team completes (it requires the findings file from red-team's commit).

```
Task tool:
  description: "Blue-team: close loopholes in instructions (round 1)"
  subagent_type: "general-purpose"
  prompt: |
    You are a blue-team agent. Your job: close specific loopholes in these instructions.
    You will be resumed in later rounds — apply fixes for the current round's findings only.

    ## Current Instructions
    {CURRENT_INSTRUCTIONS}

    ## Loopholes to Close
    Read findings.json from the red-team's commit using:
      git show {RED_TEAM_COMMIT_HASH}:findings.json

    ## Your Task
    For each loophole, revise the instructions to close it:
    - Add explicit prohibitions for unlisted tools/techniques
    - Define undefined terms that enabled rationalization
    - Convert permissive-by-omission lists to exhaustive lists with "nothing else" clauses
    - Add scope boundaries that prevent context-dependent bypass

    Rules:
    - Minimal changes: close the specific loophole, do not rewrite unrelated sections
    - Preserve all existing correct guidance
    - Do NOT add so many caveats that the instructions become unreadable

    ## Output
    Write the complete revised instructions to the skill file at {SKILL_FILE_PATH}.
    Then run: git add {SKILL_FILE_PATH} && git commit -m "blue-team: round 1 patches"
    Return only the commit hash on the last line of your response.
```

**Error handling:**
- If red-team does not return a commit hash on its last line: log an error and abort the loop.
- If `git show {RED_TEAM_COMMIT_HASH}:findings.json` fails or returns empty: log "ERROR: findings.json not readable from commit {RED_TEAM_COMMIT_HASH}" and abort.
- If findings.json is malformed (not valid JSON or missing `major_loopholes_found` field): log the error and abort. Do not default to true or false.
- Apply the same commit-hash validation to blue-team: abort if no valid commit hash is returned.

After blue-team completes, read `BLUE_TEAM_COMMIT_HASH` from the last line of its response.
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

    Focus ONLY on the diff above — analyze whether the blue-team's patches introduced new gaps or
    failed to fully close prior loopholes. Seek attack vectors not yet explored in previous rounds.
    Write new findings to findings.json and commit as before, returning only the commit hash.
```

```
Task tool (resume):
  task_id: {BLUE_TEAM_TASK_ID}
  prompt: |
    Round {N}. Resume your blue-team patching.

    ## What Changed Since Last Round
    {git diff BLUE_TEAM_COMMIT_HASH..RED_TEAM_COMMIT_HASH -- findings.json}

    Apply fixes for the new loopholes identified in this round's findings.
    Write the revised skill file and commit as before, returning only the commit hash.
```

### Step 5: In-Place Hardening Mode (Optional)

In-place hardening mode runs the adversarial TDD loop against a skill file in a worktree in a single session,
producing one commit per round as the loop progresses.

**Primary workflow — single skill file:**

In-place hardening mode activates when the caller passes a single skill file path inside the current worktree.

1. Read the file content as `CURRENT_INSTRUCTIONS`. Store the file path as `SKILL_FILE_PATH`.
2. Run the full RED→BLUE loop (up to 10 rounds) as defined in Step 4. Each round produces two commits:
   one from the red-team (findings.json) and one from the blue-team (patched skill file). The loop
   continues until convergence (`major_loopholes_found: false`) or the round cap is reached.
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
- [ ] Step 4 round 2+ prompts include git diff of previous round's changes for delta-focused analysis
- [ ] In-place hardening mode produces per-round commits (one from red-team, one from blue-team per round)
- [ ] If batch mode was used: summary table shows all skills reviewed with round counts
