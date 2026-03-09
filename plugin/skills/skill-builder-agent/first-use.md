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
subagents. Run until no major loopholes remain or 3 iterations complete.

**Why two separate subagents per round:**
A single agent playing both roles anchors on its own attack vectors — it knows exactly which
loopholes it invented, so it subconsciously over-fits the defense to those attacks and under-defends
against variations. Separate subagents with fresh context eliminate this bias.

**Termination criteria:** No major loopholes = red-team finds no CRITICAL or HIGH severity issues.
LOW/MEDIUM concerns may remain if they require disproportionate instruction complexity to close.

**Red-team subagent** (spawn fresh, no prior round context):

```
Task tool:
  description: "Red-team: find loopholes in instructions (round N)"
  subagent_type: "general-purpose"
  prompt: |
    You are a red-team agent. Your job: find concrete ways to defeat or circumvent these instructions.

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

    ## Return Format
    Return a JSON object:
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
```

If red-team returns `major_loopholes_found: false`: STOP loop, instructions are sufficiently hardened.

**Blue-team subagent** (spawn fresh, no prior round context — sequential after red-team):

The blue-team MUST run after red-team completes because it requires red-team findings as input.
Inject the red-team's JSON output into `{RED_TEAM_FINDINGS_JSON}` before spawning.

```
Task tool:
  description: "Blue-team: close loopholes in instructions (round N)"
  subagent_type: "general-purpose"
  prompt: |
    You are a blue-team agent. Your job: close specific loopholes in these instructions.

    ## Current Instructions
    {CURRENT_INSTRUCTIONS}

    ## Loopholes to Close
    {RED_TEAM_FINDINGS_JSON}

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

    ## Return Format
    Return the complete revised instructions as a markdown code block, followed by:
    {
      "changes": [
        {"loophole": "bash-file-write-bypass", "fix": "Added Bash to the list of prohibited tools"}
      ]
    }
```

Update `CURRENT_INSTRUCTIONS` with blue-team output. Increment round counter.
If round < 3 and `major_loopholes_found` was true: continue to next iteration.

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
- [ ] Adversarial TDD loop completed (either converged or 3 iterations reached)
- [ ] Final skill document includes purpose, procedure, and verification sections
- [ ] Frontmatter description is trigger-oriented and contains no implementation details
- [ ] No Functions or Prerequisites sections prime manual construction (per priming prevention checklist)
