# Plan: restructure-skill-builder-context

## Current State

`plugin/skills/skill-builder-agent/first-use.md` is 2,351 lines (~80KB) and loads entirely onto the main agent
context when `/cat:skill-builder` is invoked. This exhausts a significant portion of the context window before any
work begins.

## Target State

`first-use.md` becomes a thin orchestrator (~150 lines) that delegates the design phase (Steps 1–7) to a Task
subagent which reads detailed instructions from separate files. The main agent retains only the benchmark loop
(Steps 8–13) and adversarial TDD loop (Step 14) because those steps spawn subagents, and a subagent cannot spawn
further subagents. Context load on invocation drops from ~80KB to ~15–20KB.

## Parent Requirements

None

## Research Findings

The current `first-use.md` contains six distinct content regions:

| Region | Content | Lines (approx) | Must Stay on Main Agent? |
|--------|---------|----------------|--------------------------|
| Purpose, When to Use, Document Structure | Overview, XML vs Markdown structure | 1–129 | YES — thin orchestrator preamble |
| Steps 1–6 | Backward chaining methodology | 130–341 | NO — can move to design-methodology.md |
| Step 7 | Skill writing conventions | 342–495 | NO — can move to skill-conventions.md |
| Steps 8–13 | Benchmark evaluation framework (spawns subagents) | 496–728 | YES — spawns cat:benchmark-aggregator subagents |
| Step 14 | Adversarial TDD loop (spawns red/blue team subagents) | 729–829 | YES — spawns general-purpose subagents |
| Output format | Final skill output instructions | 830–902 | YES — thin orchestrator conclusion |
| Examples | Full skill examples, complex cases | 903–1580 | NO — can move to skill-conventions.md |
| Preprocessing & priming prevention | Checklists, priming guidance | 1581–2351 | NO — can move to skill-conventions.md |

**Key architectural constraint:** Subagents spawned via Task tool cannot spawn further subagents. Steps 8–14 must
remain on the main agent because they spawn subagents (benchmark evaluators and red/blue team agents). Steps 1–7
can be fully delegated to a Task subagent since they only read files and return structured design output.

**Rejected approaches:**

A: Move only examples out → saves only ~30% context; design methodology (~340 lines) still loads.

B: Convert to on-demand loading via `<execution_context>` references → skill loading for main-agent skills
happens at skill invocation time, not on-demand; the `<execution_context>` mechanism is for subagent prompts.

C: **Chosen** — Task subagent delegation. Thin orchestrator delegates Steps 1–7 to a design subagent using the
Task tool. The design subagent reads `design-methodology.md` and `skill-conventions.md` and returns the designed
skill draft. Main agent receives the draft and continues from Step 8.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — external callers invoke `/cat:skill-builder` the same way; internal structure change only
- **Mitigation:** E2E test: run `/cat:skill-builder` on a sample skill before and after; output must be equivalent

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` — rewrite as thin orchestrator (~150 lines); move content to new files
- `plugin/skills/skill-builder-agent/design-methodology.md` — CREATE: Steps 1–6 backward chaining methodology content
- `plugin/skills/skill-builder-agent/skill-conventions.md` — CREATE: Step 7 (skill writing conventions), examples,
  complex cases, preprocessing guidance, and priming prevention checklists

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `plugin/skills/skill-builder-agent/design-methodology.md` containing Steps 1–6 content extracted from
  `first-use.md`. Extract from the `### Step 1:` heading through the end of the `### Step 6:` section (i.e., the
  full backward chaining methodology). Add the license header. Content includes: backward decomposition, precondition
  chains, step construction, dependency analysis, priming avoidance in step formulation.
  - Files: `plugin/skills/skill-builder-agent/design-methodology.md` (new)

- Create `plugin/skills/skill-builder-agent/skill-conventions.md` containing: (1) Step 7 content (skill writing
  conventions — XML vs Markdown structure, named step routing, conditional context, command structure guidance)
  extracted from `first-use.md`; (2) Examples section (full worked skill examples, complex cases); (3) preprocessing
  directives reference; (4) priming prevention checklists and guidance. Add the license header.
  - Files: `plugin/skills/skill-builder-agent/skill-conventions.md` (new)

- Rewrite `plugin/skills/skill-builder-agent/first-use.md` as the thin orchestrator. The new file must contain:

  **1. Brief purpose (2–3 lines):** "Design or update skills and commands. Delegates design phase to a subagent."

  **2. When to Use section:** Same triggers as current (creating/updating skills or commands).

  **3. Step 1 — Collect existing skill content (if updating):**
  - If the caller provides an existing skill path: read `SKILL.md` and `first-use.md` from that path
  - Store as `EXISTING_SKILL_CONTENT` variable for the subagent prompt

  **4. Step 2 — Delegate design phase to Task subagent:**
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
      Read and follow: @{CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/design-methodology.md

      ## Skill Writing Conventions
      Read and follow: @{CLAUDE_PLUGIN_ROOT}/skills/skill-builder-agent/skill-conventions.md

      ## Return Format
      Return the complete designed skill as a markdown code block (the full SKILL.md or first-use.md content).
      Do NOT spawn subagents. Do NOT invoke the Task tool.
  ```
  - Receive the designed skill draft as `SKILL_DRAFT`

  **5. Step 3 — Benchmark loop (Steps 8–13 from original first-use.md):**
  Copy the complete Steps 8–13 content from the current `first-use.md` verbatim. Replace any reference to
  "draft from Step 7" with "SKILL_DRAFT received from design subagent".

  **6. Step 4 — Adversarial TDD loop (Step 14 from original first-use.md):**
  Copy the complete Step 14 content from the current `first-use.md` verbatim. Replace any reference to
  "benchmark-hardened instructions" with "CURRENT_INSTRUCTIONS from benchmark phase".

  **7. Output format section:**
  Copy the output format section from the current `first-use.md` verbatim (lines 830–902 approximately).

  The thin orchestrator must NOT include: design methodology details, skill writing conventions, examples, complex
  cases, preprocessing directives, or priming prevention checklists. Those belong in the separate files.

  - Files: `plugin/skills/skill-builder-agent/first-use.md` (rewrite)

## Post-conditions

- [ ] `plugin/skills/skill-builder-agent/first-use.md` file size is ≤ 20KB after refactor
- [ ] `plugin/skills/skill-builder-agent/design-methodology.md` exists and contains Steps 1–6 backward chaining content
- [ ] `plugin/skills/skill-builder-agent/skill-conventions.md` exists and contains Step 7, examples, and priming guidance
- [ ] The thin orchestrator (`first-use.md`) delegates Steps 1–7 via Task tool with `subagent_type: "general-purpose"`
- [ ] The design subagent prompt does NOT include instructions to spawn subagents (no Task tool mentions)
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Run `/cat:skill-builder` on a sample skill and confirm the full workflow completes correctly, including
      the benchmark loop (Steps 8–13) and adversarial TDD loop (Step 14)
