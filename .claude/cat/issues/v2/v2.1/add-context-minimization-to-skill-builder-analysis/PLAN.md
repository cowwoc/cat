# Plan: add-context-minimization-to-skill-builder-analysis

## Goal

Update `plugin/agents/skill-analyzer-agent/SKILL.md` to detect two new patterns when analyzing a skill:
(1) main agent steps that could be delegated to subagents, and (2) content relay anti-patterns where the
skill loads data onto the main agent only to pass it to a subagent. Update
`plugin/skills/skill-builder-agent/first-use.md` to ensure the design step checks for these patterns.
Both files must reference `plugin/concepts/subagent-context-minimization.md` for the pattern definitions.

## Parent Requirements

None

## Approaches

### A: Extend skill-analyzer-agent with two new detection patterns, update skill-builder design step (chosen)

- **Risk:** LOW
- **Scope:** 2 files (both modifications to existing files)
- **Description:** Add two new `## Patterns to Detect` subsections to
  `plugin/agents/skill-analyzer-agent/SKILL.md` — delegation opportunity detection and content relay
  anti-pattern detection — with corresponding detection rules, procedure steps, and report format
  entries. Update `plugin/skills/skill-builder-agent/first-use.md` Step 2 to include a checklist item
  instructing the design subagent to apply these new checks. No new files are created; content references
  the already-planned `plugin/concepts/subagent-context-minimization.md`.

### B: Create a separate delegation-analyzer-agent (rejected)

- **Risk:** MEDIUM
- **Scope:** 1 new file + 1 modification
- **Description:** Extract delegation and relay detection into a new standalone agent. Rejected because
  skill-analyzer-agent already reads skill content for benchmark analysis, and adding two more pattern
  checks to the same agent avoids spawning an extra subagent, keeping the skill-builder workflow lean.

### C: Add detection purely to skill-builder design subagent prompt (rejected)

- **Risk:** MEDIUM
- **Scope:** 1 file
- **Description:** Embed the detection rules inline in the skill-builder design subagent prompt rather
  than skill-analyzer-agent. Rejected because the design subagent is explicitly restricted to reading
  methodology files and returning a draft — adding runtime analysis logic there violates separation of
  concerns. skill-analyzer-agent is the correct place for pattern analysis.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** skill-analyzer-agent currently receives only benchmark JSON (not the skill text itself).
  The two new patterns require the skill text, not benchmark data. The new patterns must therefore accept
  the skill text as an additional input OR be invoked with a different call path.
- **Mitigation:** Extend the `## Inputs` section of skill-analyzer-agent to accept an optional
  `skill_text` field alongside the benchmark JSON. The new patterns are skipped when `skill_text` is
  absent, preserving backward compatibility with existing benchmark-only call sites.

## Research Findings

### Current skill-analyzer-agent input model

`plugin/agents/skill-analyzer-agent/SKILL.md` currently accepts only a benchmark JSON object
(structure documented in the `## Inputs` section). The three existing patterns (non-discriminating eval,
high-variance evals, time/token tradeoffs) operate entirely on benchmark numbers — they do not need the
skill text.

### New patterns require skill text, not benchmark data

Delegation opportunity detection and content relay detection require reading the skill's step-by-step
procedure (the SKILL.md or first-use.md content). These checks are structurally similar to the existing
benchmark analysis but operate on a different input.

### skill-builder-agent Step 3 invokes skill-analyzer-agent

`plugin/skills/skill-builder-agent/first-use.md` Step 3 (Benchmark Evaluation Loop) invokes
`skill-analyzer-agent` via `Task tool` after the benchmark runs. The invoking prompt already passes the
benchmark JSON; it will need to also pass the `skill_text` so the new patterns can run.

### No Java changes required

All analysis logic lives in the agent's markdown SKILL.md (interpreted by Claude). No Java code changes
are needed.

## Files to Modify

- `plugin/agents/skill-analyzer-agent/SKILL.md` — extend `## Inputs`, add two new subsections in
  `## Patterns to Detect`, add two new steps in `## Procedure`, extend the `## Produce Analysis Report`
  output format, and add two entries to `## Verification`
- `plugin/skills/skill-builder-agent/first-use.md` — update Step 3 Task prompt to pass `skill_text`,
  and add a checklist item to `## Verification` confirming the new pattern checks run

## Pre-conditions

- [ ] All dependent issues are closed (add-subagent-context-minimization-concept must be closed)

## Sub-Agent Waves

### Wave 1

- Update `plugin/agents/skill-analyzer-agent/SKILL.md` with the two new patterns per the specification
  below
  - Files: `plugin/agents/skill-analyzer-agent/SKILL.md`
- Update `plugin/skills/skill-builder-agent/first-use.md` Step 3 and `## Verification` per the
  specification below
  - Files: `plugin/skills/skill-builder-agent/first-use.md`

### Wave 2

- Run `mvn -f client/pom.xml test` and confirm all tests pass
- Update `STATE.md` to reflect implementation complete
  - Files: `.claude/cat/issues/v2.1/add-context-minimization-to-skill-builder-analysis/STATE.md`

## Detailed Change Specification

### Changes to `plugin/agents/skill-analyzer-agent/SKILL.md`

#### 1. Extend `## Inputs` section

After the existing benchmark JSON description, add:

```markdown
An optional `skill_text` field may be provided alongside the benchmark JSON when the invoking agent
wants delegation and relay pattern analysis. When `skill_text` is absent, the two new pattern checks
(Delegation Opportunity and Content Relay Anti-Pattern) are skipped.

```json
{
  "benchmark": { ...existing benchmark JSON structure... },
  "skill_text": "full text of the SKILL.md or first-use.md being analyzed (optional)"
}
```
```

#### 2. Add two new `## Patterns to Detect` subsections

After the existing `### Time/Token Tradeoffs` subsection, add:

---

```markdown
### Delegation Opportunity

A **delegation opportunity** exists when the skill procedure contains one or more steps that perform
tool calls (Read, Grep, Bash, Glob, Write, Edit) without requiring main-agent decision-making.
These steps load data onto the main agent's context that a subagent could obtain independently.

**Detection rule:** When `skill_text` is provided, scan the procedure steps for:
- Sequential Read/Grep/Bash/Glob calls that gather information used only in the next step
- Steps that explicitly say "read file X, then pass to subagent" or equivalent
- Any step that runs 3+ tool calls before spawning a Task subagent

Flag as delegation opportunity if any of the above are found. Reference
`plugin/concepts/subagent-context-minimization.md` for when delegation is appropriate.

**Skip this check** if `skill_text` is absent from the inputs.

### Content Relay Anti-Pattern

A **content relay anti-pattern** exists when the skill procedure instructs the main agent to read
file content and then include that content verbatim in a subagent prompt, rather than passing the
file path and letting the subagent load the content itself.

**Detection rule:** When `skill_text` is provided, scan the procedure steps for:
- A Read/Grep/Bash call immediately followed (within 1-2 steps) by a Task tool invocation where the
  prompt template includes the variable populated by that read (e.g., `{FILE_CONTENT}`, `{DIFF_OUTPUT}`,
  `{TEST_RESULTS}`)
- Explicit instructions like "read X and include in the subagent prompt"
- Prompt templates that embed full file content rather than file paths

Flag as content relay anti-pattern if any of the above are found. Reference
`plugin/concepts/subagent-context-minimization.md` for the correct pattern.

**Exception:** If the step comment or surrounding text indicates the main agent needed the content for
its own decision-making before passing it to the subagent, do NOT flag it as an anti-pattern.

**Skip this check** if `skill_text` is absent from the inputs.
```

#### 3. Add two new steps in `## Procedure`

After the existing `### Step 3: Detect Time/Token Tradeoffs`, add:

```markdown
### Step 4: Detect Delegation Opportunities

If `skill_text` is absent, skip this step and mark Delegation Opportunity as "Skipped (no skill_text)".

Scan the procedure section of `skill_text` for the delegation opportunity patterns defined above.
Collect the specific step numbers and descriptions of steps that meet the detection criteria.

### Step 5: Detect Content Relay Anti-Patterns

If `skill_text` is absent, skip this step and mark Content Relay Anti-Pattern as "Skipped (no skill_text)".

Scan the procedure section of `skill_text` for the content relay anti-pattern patterns defined above.
For each flagged instance, record:
- The step number
- The variable name being relayed (e.g., `{FILE_CONTENT}`)
- The source tool call (Read/Grep/Bash) that populated it
- Whether the exception clause applies
```

Renumber the existing `### Step 4: Produce Analysis Report` to `### Step 6: Produce Analysis Report`.

#### 4. Extend the report format in `### Step 6: Produce Analysis Report`

Add two new sections to the report format, after `Time/Token Tradeoff:`:

```
Delegation Opportunities:
  [Skipped (no skill_text provided)]
  [or: NONE FOUND]
  [or: FOUND in steps: <step numbers and brief descriptions>
   Recommendation: Consider delegating these steps to a subagent. See
   plugin/concepts/subagent-context-minimization.md for delegation criteria.]

Content Relay Anti-Patterns:
  [Skipped (no skill_text provided)]
  [or: NONE FOUND]
  [or: FOUND:
   - Step N: variable {VAR_NAME} populated by <tool> and relayed to subagent prompt.
     Fix: pass file path or task description instead of file content.
     See plugin/concepts/subagent-context-minimization.md for the correct pattern.]
```

Add recommendation content for each new pattern in the Recommendations guidance text:

- **Delegation opportunity**: List the steps by number and suggest wrapping them in a subagent Task call.
  Reference `plugin/concepts/subagent-context-minimization.md` for the decision table.
- **Content relay anti-pattern**: Name each relayed variable and its source tool call. Recommend passing
  the file path or search term instead. Reference `plugin/concepts/subagent-context-minimization.md`.

#### 5. Add two entries to `## Verification`

```markdown
- [ ] Delegation opportunity check runs when `skill_text` is provided; skipped when absent
- [ ] Content relay anti-pattern check runs when `skill_text` is provided; skipped when absent
- [ ] Both new patterns produce actionable recommendations referencing
  `plugin/concepts/subagent-context-minimization.md`
```

### Changes to `plugin/skills/skill-builder-agent/first-use.md`

#### 1. Update Step 3 Task prompt to pass `skill_text`

In the Step 3 Task tool invocation that calls skill-analyzer-agent, change the prompt to wrap the
benchmark JSON in the new envelope format:

Before (schematic):
```
prompt: |
  ...
  {BENCHMARK_JSON}
```

After (schematic):
```
prompt: |
  ...
  {
    "benchmark": {BENCHMARK_JSON},
    "skill_text": {SKILL_DRAFT}
  }
```

Where `SKILL_DRAFT` is the skill text returned by the design subagent in Step 2 (already in context).

#### 2. Add `## Related Concepts` reference (if not already added by Issue A)

If the `## Related Concepts` section added by the prior issue (`add-subagent-context-minimization-concept`)
does not already mention the skill-analyzer pattern checks, add a bullet:

```markdown
- **skill-analyzer-agent**: Detects delegation opportunities and content relay anti-patterns in skill
  procedures — `plugin/agents/skill-analyzer-agent/SKILL.md`
```

#### 3. Add two entries to `## Verification`

```markdown
- [ ] Step 3 Task prompt passes `skill_text` to skill-analyzer-agent alongside benchmark JSON
- [ ] skill-analyzer-agent report includes Delegation Opportunities and Content Relay Anti-Patterns
  sections when skill_text is provided
```

## Post-conditions

- [ ] `plugin/agents/skill-analyzer-agent/SKILL.md` `## Patterns to Detect` contains `### Delegation
  Opportunity` and `### Content Relay Anti-Pattern` subsections with detection rules
- [ ] `plugin/agents/skill-analyzer-agent/SKILL.md` `## Procedure` has Steps 4 and 5 for the new
  patterns (original Step 4 renumbered to Step 6)
- [ ] `plugin/agents/skill-analyzer-agent/SKILL.md` report format includes `Delegation Opportunities:`
  and `Content Relay Anti-Patterns:` sections
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 3 passes `skill_text` to skill-analyzer-agent
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Invoke skill-analyzer-agent with a benchmark JSON + `skill_text` from a skill that has a clear
  delegation opportunity (e.g., `optimize-execution/first-use.md` Step 3 manual extraction pattern) and
  verify the analysis report includes a `Delegation Opportunities: FOUND` entry with the step reference
