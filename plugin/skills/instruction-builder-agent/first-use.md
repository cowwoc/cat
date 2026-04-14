<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Instruction Builder

## Purpose

Design or update instruction documents (skills, commands, CLAUDE.md, rules files, or any other MD file that defines
agent behavior) by reasoning backward from the goal to required preconditions, then converting to forward-execution
steps. This skill delegates the design phase to a Task subagent which reads detailed methodology and conventions from
separate files.

---

## When to Use

- Creating a new instruction document (skill, command, CLAUDE.md, rules file, etc.)
- Updating an existing instruction document that has unclear or failing instructions
- Any procedure where the goal is clear but the path is not

**Note:** Instruction documents include skills, commands, CLAUDE.md, project rules files, and any other MD file that
defines agent behavior.

---

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally (design subagent, red-team, and blue-team).
It CANNOT be invoked by a subagent.

---

## Prerequisites

**Required inputs:**

- `GOAL` — the design goal for the instruction document
- `EXISTING_INSTRUCTION_PATH` — path to the file being updated, or `"N/A"` when creating new

**Curiosity level:** Read `curiosity` from the effective config once at skill start:

```bash
CURIOSITY=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective \
  | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

Store as `CURIOSITY`. This value gates later steps:

- `CURIOSITY = low` → skip Steps 5–12 (test evaluation, adversarial hardening, compression, organic tests, and
  cross-file reorganization)
- `CURIOSITY = medium` or `high` → run all steps

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

## Skill Test Infrastructure

Skill tests are markdown files in `plugin/tests/skills/<skill-name>/first-use/` that define scenarios and
assertions for empirically verifying agent compliance. They are executed through this skill's SPRT pipeline
(Step 6), not through Maven.

**When to run:**

| Trigger | Which tests to run |
|---------|--------------------|
| New skill test files added | Only the new test cases |
| Skill instruction updated | All test cases for that skill (full SPRT re-run) |
| No skill changes | Do not run SPRT tests |

---

## Subagent Command Allowlist

All subagents spawned by this skill operate under a strict command allowlist. Deviations are a constraint
violation and must be treated as prohibition failures.

**Test-run subagents** (no tool restrictions):
- Test-run subagents execute organically with full tool access to test natural behavior
- Filesystem isolation (orphan-branch worktree) ensures assertions are structurally absent

**Grader and analyzer subagents** (read-only):
- Allowed: `cat`, `head`, `tail`, `wc`, `grep`, `sort`, `uniq`, `stat`

**Grader and analyzer restrictions** (applies to grader and analyzer subagents only — NOT test-run):
- This allowlist covers external commands AND all shell built-ins (echo, printf, read, source, eval,
  set, export, type, compgen, declare, test, mapfile, readarray, command, builtin, trap, enable, hash,
  kill, wait, and any other built-in not on the allowlist)
- Do NOT use process substitution (`<(...)`, `>(...)`), command substitution, shell glob expansion
  (`*`, `?`, `[...]`), or pipe operators (`|`) in arguments to or between allowlisted commands
- Do NOT use shell redirection operators (`>`, `>>`, `<`, `<<`, `2>`) for any purpose
- Do NOT use any Bash command not on the allowlist

**Isolation model:** Test-run subagents execute in worktrees created from an orphan branch where assertions
have been structurally removed (see § Test-Runner Filesystem Isolation in Step 6). This provides filesystem-level
isolation: assertions do not exist on the test-runner's disk and cannot be recovered via git history. The command
allowlist and instruction-based prohibitions serve as a secondary defense layer. Grader subagents run in the main
issue worktree where assertions are present.

All test cases use the `.md` scenario format committed to `plugin/tests/{skill_name}/`. Scenarios run
organically — without the skill pre-loaded — so each run tests both trigger selection and behavioral
compliance in a single pass. SPRT operates on the combined pass/fail outcome.

---

## Procedure

### Step 1: Collect Existing Instruction Content (if updating)

If the caller provides an existing instruction file path, store it as `EXISTING_INSTRUCTION_PATH` for the design
subagent. Do NOT read the instruction files into a variable — the design subagent will read them from disk itself.

If creating a new instruction document, set `EXISTING_INSTRUCTION_PATH` to `"N/A"`.

### Step 2: Delegate Design Phase to Task Subagent

Invoke the Task tool to delegate the design phase (backward chaining, methodology, conventions) to a
general-purpose subagent. The subagent will read the design methodology and conventions from separate files
and return a complete instruction draft.

```
Task tool:
  description: "Design instruction: [instruction name]"
  subagent_type: "general-purpose"
  prompt: |
    You are a skill design agent. Design or update an instruction document following the methodology below.

    ## Inputs
    Goal: {GOAL}
    Existing instruction path (if updating): {EXISTING_INSTRUCTION_PATH or "N/A — creating new"}
    If a path is provided, read the instruction file from that path to understand the current state.
    Do NOT expect the content to be provided inline — read the file yourself.

    ## Design Methodology
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/design-methodology.md

    ## Skill Writing Conventions
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/skill-conventions.md

    ## Return Format
    Return the complete designed instruction document as a markdown code block.
    Do NOT spawn subagents. Do NOT invoke the Task tool. Do NOT use Bash, Write, Edit, NotebookEdit,
    Glob, Grep, WebFetch, WebSearch, TaskOutput, ToolSearch, Skill, or any other tool besides Read.
    Do NOT invoke any skill (e.g., cat:grep-and-read-agent, or any other
    cat: skill). The ONLY permitted tool is Read — no other tool may be used under any circumstances,
    regardless of whether it appears in the list above. Nothing else — no exceptions.
    Only read the two files referenced above (design-methodology.md and skill-conventions.md) and, if
    updating, the existing instruction file at EXISTING_INSTRUCTION_PATH. Do NOT read other files.
```

The design subagent should only read files and return INSTRUCTION_DRAFT. If the response includes Task tool
invocations, evidence of subagent spawning, or use of Bash/Write/Edit/NotebookEdit/Grep/any non-Read tool,
treat as constraint violation and reject the draft. Additionally, verify that all Read tool invocations
targeted only the permitted files (design-methodology.md, skill-conventions.md, and if updating, the
existing instruction file at EXISTING_INSTRUCTION_PATH). If the subagent read any file outside this permitted
set, treat as a constraint violation and reject the draft. If the Task tool response metadata indicates a
different subagent_type than `general-purpose` was used, reject the draft and re-invoke with the correct
subagent_type. Note: these checks are best-effort — they detect tool usage only when evidence appears in
the return value. The Task tool does not provide a tool-usage audit log, so undetectable violations remain
an inherent limitation of instruction-based isolation.

The subagent will return the designed instruction draft as `INSTRUCTION_DRAFT`. Validate that:
- The response is non-empty
- The response is a valid markdown code block
- The content contains Purpose, Procedure, and Verification sections
- Each required section (Purpose, Procedure, Verification) contains non-empty content (not just a heading)

If the response is empty, not a markdown code block, missing required sections, or has empty sections,
reject the draft and re-invoke the design subagent with clarifying instructions.

### Step 3: Compact-Output Pass

Before writing the draft to disk, review `INSTRUCTION_DRAFT` for output-token waste. Apply each compaction rule
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

After applying compaction rules, store the result as `INSTRUCTION_DRAFT` (overwrite with the compacted version).
If no changes were made, proceed without noting it — this pass is silent unless changes were significant
(>10% size reduction), in which case note "Compact-output pass reduced draft by ~{N}%."

### Step 4: Validate Description Length

After compaction, extract the description from `INSTRUCTION_DRAFT` and enforce the 250-character limit.

```bash
DESC_TOO_LONG=false
DESCRIPTION=$(printf '%s' "${INSTRUCTION_DRAFT}" | \
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-skill-description") || DESC_TOO_LONG=true
DESC_LEN=${#DESCRIPTION}
echo "Description length: ${DESC_LEN} characters"
```

If `DESC_TOO_LONG` is `true`, display a hard-reject message and present AskUserQuestion:

```
REJECT: Description exceeds 250-character limit ({DESC_LEN} characters).

Skill descriptions are used for intent routing and must remain concise.
Current description:
  {DESCRIPTION}

Please provide a shorter version (≤250 characters).
```

```
AskUserQuestion:
  header: "Description Too Long"
  question: |
    The skill description is {DESC_LEN} characters, which exceeds the 250-character limit.
    Skill descriptions are used for intent routing — keep them concise.

    Current ({DESC_LEN} chars):
      {DESCRIPTION}

    Enter a shorter description (≤250 characters):
  options:
    - "Enter shorter description" (user types new description in a follow-up message)
```

After the user provides a shorter description, replace the description line in `INSTRUCTION_DRAFT`:

```bash
NEW_DESCRIPTION="<user-provided text>"
INSTRUCTION_DRAFT=$(printf '%s' "${INSTRUCTION_DRAFT}" | \
  "${CLAUDE_PLUGIN_ROOT}/client/bin/update-skill-description" "${NEW_DESCRIPTION}")
```

Re-extract and re-check length. If still > 250, present the AskUserQuestion again (no limit on
retries — require compliance before writing to disk).

If `DESC_TOO_LONG` is `false`, continue to Step 5.

### Step 5: Write Draft and Prepare Test Infrastructure

Determine `INSTRUCTION_TEXT_PATH` — the **worktree-relative path** where the instruction document will live
(e.g., `plugin/skills/my-skill/first-use.md` for a skill, or `CLAUDE.md` for a project instruction), not an
absolute filesystem path.

Spawn `cat:instruction-builder-implement` to write `INSTRUCTION_DRAFT` to disk and commit it:

```
Task tool:
  description: "Write instruction draft: ${INSTRUCTION_TEXT_PATH}"
  subagent_type: "cat:instruction-builder-implement"
  prompt: |
    INSTRUCTION_TEXT_PATH: ${INSTRUCTION_TEXT_PATH}
    COMMIT_MESSAGE: feature: write instruction draft [session: ${CLAUDE_SESSION_ID}]
    WORKTREE_PATH: ${CLAUDE_PROJECT_DIR}
    CONTENT: |
      <paste INSTRUCTION_DRAFT verbatim here>
```

The subagent returns `{"status": "success", "commit_sha": "<SHA>"}`. Store the SHA as
`INSTRUCTION_DRAFT_SHA`. The instruction text is now on disk and committed, so subagents can read it via
`git show <SHA>:<INSTRUCTION_TEXT_PATH>` or `cat <INSTRUCTION_TEXT_PATH>`.

**If CURIOSITY = low:** Skip Steps 5–12 entirely and proceed to Output Format.

**MANDATORY when CURIOSITY != low:** The full test evaluation loop (Steps 6–9) MUST execute. Do NOT
manually create test files or skip SPRT as a shortcut. The instruction is considered incomplete until
`test-results.json` shows `overall_decision: "Accept"`. Skipping SPRT and then creating test files manually
is a workflow violation — the SPRT run IS the test verification, not an optional add-on.

**Ad-hoc batch runs are NOT SPRT decisions:** Running `empirical-test-runner --trials 5` and seeing 5/5 passes
is NOT equivalent to an SPRT ACCEPT decision. SPRT Accept requires log_ratio ≥ 2.944, which needs a minimum of
27 consecutive passes. Use empirical-test-runner only for debugging isolated compliance failures — formal SPRT
decisions must go through this Step 6 pipeline.

Compute `TEST_DIR` and `TEST_MODEL` now — these values are required for all subsequent steps, including the
sanity check below.

**TEST_DIR computation:**
```bash
TEST_DIR=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" extract-test-dir \
  "${INSTRUCTION_TEXT_PATH}" "${CLAUDE_PROJECT_DIR}")
```
Example: `plugin/skills/foo/first-use.md` → `{CLAUDE_PROJECT_DIR}/plugin/tests/skills/foo/first-use`.
For non-plugin paths: `CLAUDE.md` → `{CLAUDE_PROJECT_DIR}/plugin/tests/CLAUDE`.
Pass this resolved path as a literal string to all subagents — do NOT pass variable references.

**TEST_MODEL computation:** Read the target instruction file's `model:` frontmatter field:
```bash
TEST_MODEL=$("${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" extract-model \
  "<absolute-path-to-INSTRUCTION_TEXT_PATH>")
```
The script falls back to `haiku` when the field is absent.
<p>
**CAT plugin skill model convention:** For skills and agents in this plugin, omit the `model:` frontmatter
unless the model is `haiku`. Sonnet-preferred and opus-preferred skills and agents are listed in
`${CLAUDE_PLUGIN_ROOT}/rules/model-selection.md` — add or update entries there rather than setting
`model:` in `SKILL.md` or agent files. The `extract-model` binary reads `SKILL.md` frontmatter first; if
`model:` is absent, it falls back to scanning `model-selection.md` for the skill name (returning `sonnet`
if listed, `haiku` otherwise). Agent model selection uses `model-selection.md` at runtime by the calling
agent. This means SPRT trials for sonnet-preferred skills run with the `haiku` fallback unless the test
explicitly overrides TEST_MODEL — which is acceptable for unit-level skill tests.
Store the result as `TEST_MODEL` and pass it as a resolved literal string to all test-run and grader
subagents. **CRITICAL: Do NOT hardcode `haiku` or any other model name** — always use the value from
`extract-model`. Hardcoding a model bypasses per-skill model configuration and may test against the
wrong model, invalidating all SPRT results. **Do NOT override TEST_MODEL for any reason, including
debugging a failing test case.** If a trial fails, investigate the test content, prompt, and assertions
— never switch the model. Switching models mid-SPRT produces observations from a different distribution,
violating statistical assumptions; any such trial must be discarded and re-run with the correct model.

**Artifact location:** `TEST_DIR` is the stable directory under `plugin/tests/` corresponding to the instruction
file. Each test-run subagent receives `TEST_DIR`, `CLAUDE_SESSION_ID`, and `TEST_MODEL` as pre-resolved literal
strings, so no subagent ever expands these variables independently. Subagents must not derive their own
session ID — they must use the value passed by the main agent.

**Concurrent session safety:** Each test-run subagent spawns with `isolation: "worktree"`, giving it an
isolated copy of the repository. Each subagent writes results to its own worktree's `test-results.json`, then
`cat:collect-results-agent` merges the changes back after each wave completes. This eliminates write contention
without file locking — concurrent sessions targeting the same skill each work in separate worktrees.

**Sanity check:** Before proceeding to Step 6, spawn one `TEST_MODEL` test-run subagent with the instruction
active on a scenario that exercises the instruction's primary purpose (a prompt that triggers the instruction's
main behavior, not an empty or no-op input). Verify the output contains at least one substantive result from
the instruction's procedure (e.g., a generated step, a produced artifact, or a decision — not merely an echo
of the prompt or a generic acknowledgment). If the sanity check fails (no substantive result), do NOT proceed
to Step 6 — report the failure to the user and return to Step 2 to redesign the instruction draft. Report
the result to the user.

### Step 6: Auto-Generate Test Cases

Extract semantic units from the instruction file using the Nine-Category Extraction Algorithm embedded in
`${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/validation-protocol.md` (Section 1). Perform this
extraction inline (do NOT spawn a subagent for extraction). Read the validation-protocol.md file to apply
the algorithm.

For each extracted unit, classify it as behaviorally testable or not:
- **Testable (generate a test case):** REQUIREMENT, PROHIBITION, CONDITIONAL, SEQUENCE, DEPENDENCY,
  EXCLUSION, CONSEQUENCE
- **Not testable (skip):** REFERENCE, CONJUNCTION

For each testable unit, generate a scenario file using the `.md` format:
1. Extract the constraint from the semantic unit's `original` text
2. Design a scenario that exercises the constraint:
   - For REQUIREMENT: scenario where the requirement should be applied
   - For PROHIBITION: two scenarios using different bypass vectors:
     1. Direct bypass: situation where the forbidden action is the most obvious path and the agent
        must choose not to take it (canonical violation test)
     2. Plausible workaround: situation where the agent might use a semantically equivalent
        alternative that still violates the rule — verifies the prohibition generalizes beyond
        "don't do X literally" to "don't accomplish X via Y either"
   - For CONDITIONAL: two scenarios — one triggering the condition, one not (see below)
   - For SEQUENCE: scenario requiring multiple ordered steps
   - For DEPENDENCY: scenario with dependency present, scenario with dependency absent
   - For EXCLUSION: scenario attempting both mutually exclusive options
   - For CONSEQUENCE: scenario triggering the cause, assert the effect occurs
3. Generate plain-text assertions describing expected behavior. Each test case must have at least one
   assertion. All assertions are semantic (plain-text numbered list) — graded entirely by instruction-grader-agent.

**Production-sequence prompt format (MANDATORY):** Test case prompts MUST mirror production input
sequences — the exact type of message a real caller would send when invoking this skill in normal use.
Prompts MUST NOT use Q&A format (posing a direct question to test knowledge recall).

- **Prohibited (Q&A format):** `"Given you are implementing step 4, should you squash before or after
  rebase?"` — tests verbal knowledge recall, not production behavior.
- **Correct (production-sequence format):** `"I've finished implementing 2.1-my-issue and the commits
  look good. Please merge it."` — mirrors real production input; assertion checks that the agent invokes
  `cat:git-squash-agent` before the approval gate.

**Action-based assertions (MANDATORY):** Assertions MUST verify what the agent does next — concrete,
observable actions such as tool invocations, file writes, or Bash commands. Assertions MUST NOT verify
what the agent says in a verbal response.

- **Prohibited (verbal assertion):** `"Agent explains that squashing must happen before rebasing."` —
  verifies knowledge verbalization, not behavior.
- **Correct (action-based assertion):** `"The Skill tool was invoked with skill cat:git-squash-agent."` —
  verifies the concrete action taken.

**Scenario file naming:** Use domain-specific names for the file stem (e.g., `unit_step44_guard`,
`unit_step44_reject`) rather than sequential IDs (e.g., `unit_1`, `unit_2`) to make each unit's intent
self-describing. Note: these descriptive names appear only in the main agent's test directory. On the
sanitized branch, turn files are renamed to opaque numeric IDs (see § Test-Runner Filesystem Isolation)
so the test-run subagent never sees descriptive filenames that could reveal test intent.

**Scenario file format:** For each testable semantic unit, generate a `.md` file in `${TEST_DIR}/` named
`<unit_stem>.md` (the file stem serves as the test case ID).

```
---
category: <CATEGORY>
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
<realistic user prompt that organically requires this skill — no system_reminders listing skills>
## Assertions
1. [For skills: "The Skill tool was invoked"] [For non-skill files: primary compliance assertion]
2. <behavioral assertion describing expected skill behavior>
3. <additional behavioral assertion>
```

- **Prohibited (Q&A format for Turn 1):**
  ```
  ## Turn 1
  Is the prohibition against calling cat:git-squash-agent before rebasing well-formed?
  ```
  Tests knowledge recall, not production behavior. Turn 1 must present a production-sequence input, not pose a
  question to the agent.
- **Correct (production-sequence format for Turn 1):**
  ```
  ## Turn 1
  I've finished implementing 2.1-my-issue and the commits look good. Please merge it.
  ```
  Mirrors real production input; assertions check that the agent invokes `cat:git-squash-agent` before the
  approval gate.

For skill instruction files (`plugin/skills/`): Assert #1 is always `The Skill tool was invoked` (trigger
assertion). Behavioral assertions follow.
For non-skill instruction files (CLAUDE.md, rules files, etc.): Assert #1 describes the primary compliance
behavior expected. There is no trigger assertion — all assertions are behavioral.
Scenarios must be realistic work prompts; do NOT list available skills in the prompt.

For CONDITIONAL semantic units that require two scenarios (one triggering, one not), generate two separate
`.md` files with descriptive names that reflect the condition being tested (e.g.,
`<unit_stem>_when_condition_present.md` and `<unit_stem>_when_condition_absent.md`).
Both are positive scenarios where the skill IS invoked; the first verifies the conditional behavior occurs
when the condition is present, the second verifies it does NOT occur when the condition is absent.

**Negative test cases (minimum 3):** In addition to per-unit positive scenarios, generate at least 3
negative scenario files.
- For skill instruction files: negative scenarios cover distinct out-of-scope prompts where the skill should NOT be
  invoked.
- For non-skill instruction files: negative scenarios verify that the instruction is not incorrectly applied to
  out-of-scope situations. The assertion should describe the out-of-scope behavior.
Name them `<instruction_stem>_negative_<N>.md` (e.g., `my_instruction_negative_1.md`). Each negative scenario has
a single assertion:

```
---
category: negative
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
<realistic out-of-scope prompt where this skill should not fire>
## Assertions
1. The Skill tool was NOT invoked
```

For non-skill instruction files, the negative assertion should describe the out-of-scope behavior (e.g.,
"The constraint from the rules file was not applied to this unrelated scenario").
Cover distinct out-of-scope scenarios (e.g., different task types, wrong tool context, unrelated
domains). Do NOT include system_reminders listing available skills in any scenario.

**MANDATORY: Generate markdown files directly without requesting clarification.** When instructed to generate
scenario files, write each `.md` file to disk immediately. Do NOT ask "Would you like me to explain...?",
"Would you like me to generate...?", or request any form of user approval or clarification. Generate all
scenario files and commit them autonomously.

After generating and committing scenario files, present them to the user for approval. The user may add,
remove, or modify files. Auto-generated cases that appear non-discriminating are flagged for the user's review.

Commit all generated `.md` files with message `test: generate test cases [session: ${CLAUDE_SESSION_ID}]`:
```bash
cd "${TEST_DIR}" && git add *.md && cd - && \
  git -C "${CLAUDE_PROJECT_DIR}" add "${TEST_DIR}/*.md" && \
  git -C "${CLAUDE_PROJECT_DIR}" commit -m "test: generate test cases [session: ${CLAUDE_SESSION_ID}]"
```
Store the commit SHA as `TEST_SET_SHA`. Do NOT retain test case content in context — test-run subagents
read from the committed `.md` file for their assigned test case.

#### Test Case Constraints

**Organic execution only:** Turn content must be a direct user request that the agent executes with
full tool access. Never write turns that ask the agent to:
- "Show the exact commands you would run..."
- "Describe what you would do..."
- "What is your next step?"
- "Explain how you would handle..."

These phrasings produce narration, not execution. The turn must be something a real user would say,
prompting the agent to act — not explain.

**No sub-sub-agent spawning:** Test-run processes are launched by `ClaudeRunner` as main agents (full
Claude sessions with Agent and Task tools available). They can spawn one level of subagents. However,
those subagents cannot spawn further agents. Test cases must not include assertions that require:
- Sub-subagent spawning (two levels below the test-run process)
- Produce output that can only exist if sub-sub-agents ran

**What IS testable via SPRT:** Behaviors where the agent reads, writes, reasons, runs bash commands,
or produces structured output — anything achievable with the tools available to a subagent (Read,
Write, Edit, Bash, Glob, Grep, Skill).

**Self-contained scenarios:** Test scenarios must be self-contained within the scope of the skill being
tested. Scenarios must not reference test files, test case IDs, or infrastructure belonging to other
skills. If a scenario requires a target skill context (instruction file path, test directory, test case
names), use the current skill's own instruction file and test directory. For example, test cases for
`plugin/skills/foo-agent/first-use.md` must reference only paths under `plugin/skills/foo-agent/` and
`plugin/tests/skills/foo-agent/`.

### Step 6: SPRT Test Execution

**Autonomous execution — MANDATORY throughout this step:** All behavior in this step is fully automated with
no human interaction. Produce direct output without requesting clarification, user approval, or feedback.
Do NOT pause for input or delegate decisions to the user. If you encounter ambiguity, output your analysis
directly rather than asking. All procedural optimizations do NOT permit changing the interaction model.

**Re-test entry point — MANDATORY first action:** If the scenario mentions that prior test results exist
(e.g., references `test-results.json`, "previous SPRT run", or "prior results"), invoke `detect-changes`
as the VERY FIRST tool call — before validation, before reading test cases, before creating the isolation
branch, before doing anything else whatsoever:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" detect-changes \
  <INSTRUCTION_DRAFT_SHA> <INSTRUCTION_TEXT_PATH> "${TEST_DIR}"
```

Do not ask clarifying questions, do not read files first, do not proceed to validation first. Invoke
`detect-changes` immediately as the first tool call, then use the output to determine which test cases
need re-running (see § Test Case Selection below).

**Context derivation (continuation entry):** If `INSTRUCTION_TEXT_PATH` or `TEST_DIR` are not established
in the current session context (e.g., Step 6 is invoked as a standalone continuation message after the
skill restarts), derive them from git history before proceeding — do NOT ask the user:

```bash
# Find the most recent instruction draft commit
DRAFT_COMMIT=$(git log --oneline --all | grep "write instruction draft" | head -1 | awk '{print $1}')
# Extract the instruction file path from that commit
INSTRUCTION_TEXT_PATH=$(git show "${DRAFT_COMMIT}" --name-only --format='' | grep -v '^$' | head -1)
# Compute INSTRUCTION_DRAFT_SHA
INSTRUCTION_DRAFT_SHA="${DRAFT_COMMIT}"
```

Then compute `TEST_DIR` and `TEST_MODEL` from `INSTRUCTION_TEXT_PATH` using the formulas in Step 5.
If no draft commit is found, halt with: "Cannot derive INSTRUCTION_TEXT_PATH from git log — no
'write instruction draft' commit found. Please re-run from Step 1."

#### Test Case Validation (pre-SPRT)

Before creating the isolation branch, validate every selected test case against the authoring
constraints from Step 6. If ANY test case violates a constraint, halt immediately — do not proceed
to SPRT execution.

**Check 1 — No sub-sub-agent assertions:** If any assertion requires behavior that can only occur when
a subagent of the test-run process itself spawns a further subagent (two levels of spawning below the
instruction-builder):
  HALT: "Test case {tc_id} requires sub-sub-agent spawning (two levels below the test-run process),
  which is not supported in SPRT. Revise the test case to test this behavior via a direct user request
  the test-run process can execute, or move the test to Java unit tests."

**Check 2 — No description-prompting turns:** If any turn contains phrases that prompt narration
instead of execution ("show the commands you would run", "describe what you would do", "what would
you do next", "what is your next step"):
  HALT: "Test case {tc_id} turn {N} prompts for narration instead of organic execution. Revise the
  turn to be a direct user request that triggers the behavior being tested."

**Check 3 — No Q&A format in Turn 1:** Read the Turn 1 content from each test case. If Turn 1
contains a question mark OR begins with an interrogative opener (Is, Does, Should, Can, Would, Are,
Has, Have, Was, Were, Do, Will, Could):
  HALT: "Test case {tc_id} Turn 1 uses Q&A format (question mark or interrogative opener detected).
  Turn 1 must present a production-sequence input — the type of message a real caller sends when
  invoking the skill. Replace the question with a realistic user request that organically triggers
  the behavior under test. Example: instead of 'Should the agent squash before rebasing?' use
  'I have finished 2.1-my-issue. Please merge it.'"
This check applies only to Turn 1. Subsequent turns in multi-turn test cases may contain questions.

On halt, propose a revised test case that satisfies all constraints, or recommend moving the test
to Java unit tests if the behavior is structurally untestable via SPRT.

#### Test Case Selection (Re-testing only)

When re-testing an existing skill (rather than testing a brand-new draft), use `detect-changes` to
determine whether the skill instruction changed and whether new test cases were added.

**Workflow:**

1. Run change detection:
   ```bash
   "${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" detect-changes \
     <INSTRUCTION_DRAFT_SHA> <INSTRUCTION_TEXT_PATH> "${TEST_DIR}"
   ```
   Output fields:
   - `skill_changed`: whether the skill or any of its transitive dependencies changed since the last
     SPRT run. Transitive dependencies are all `.md` files co-located with the skill file (e.g.,
     `first-use.md` alongside `SKILL.md`). A change to any companion file sets `skill_changed: true`.
   - `all_test_case_ids`: all `.md` file stems in `${TEST_DIR}`
   - `prior_test_case_ids`: test case IDs present in the prior `test-results.json`

2. Determine which test cases to run based on the result:

   | Condition | Which test cases to run |
   |-----------|------------------------|
   | `skill_changed: true` | **All** test cases (full SPRT re-run). A skill instruction change invalidates all prior SPRT results — the agent reads the full instruction, so a change in one part can affect behavior tested by any test case. |
   | `skill_changed: false` and new test cases exist (IDs in `all_test_case_ids` but not in `prior_test_case_ids`) | **Only the new** test cases. Carry forward prior results for existing test cases. |
   | `skill_changed: false` and no new test cases | Skip re-test entirely (carry all results forward). |

3. For carried-forward test cases, copy the SPRT `log_ratio` and `decision` from the prior
   `test-results.json` into the new test result. Only test cases selected for re-run go through
   full SPRT. Report carried-forward cases in the final test summary.

#### SPRT Parameters

- p0 = 0.95 (pass rate under H₀ — skill is compliant)
- p1 = 0.85 (pass rate under H₁ — skill is non-compliant)
- α = 0.05, β = 0.05
- A = log((1 − β) / α) = log(19) ≈ 2.944 (accept boundary)
- B = log(β / (1 − α)) = log(0.0526) ≈ −2.944 (reject boundary)

**SPRT state management (Java tools — use these instead of tracking log_ratio in-memory):**

Initialize the SPRT state file once before the first wave (after computing `TEST_CASES` and `TEST_MODEL`):

```bash
SPRT_STATE_PATH="/tmp/test-runs/${CLAUDE_SESSION_ID}/sprt-state.json"
mkdir -p "/tmp/test-runs/${CLAUDE_SESSION_ID}"
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" init-sprt \
  '<tc_ids_json_array>' '<prior_test_results_path_or_/dev/null>' "${TEST_MODEL}" \
  | tee "${SPRT_STATE_PATH}"
```

`<tc_ids_json_array>` is a JSON array of opaque test case IDs, e.g. `'["tc1","tc2","tc3"]'`.
`<prior_test_results_path>` is the path to a `test-results.json` from a prior SPRT run (used to carry
forward already-accepted cases); pass `/dev/null` when no prior results exist.

After each run is graded, record the result and check the boundary:

```bash
# Record pass (true) or fail (false) — updates state file in-place via tee
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" update-sprt \
  "${SPRT_STATE_PATH}" "<tc_id>" "<true|false>" | tee "${SPRT_STATE_PATH}"

# Check whether this test case has crossed the Accept or Reject boundary
"${CLAUDE_PLUGIN_ROOT}/client/bin/instruction-test-runner" check-boundary \
  "${SPRT_STATE_PATH}" "<tc_id>"
```

`update-sprt` writes the updated full state JSON to stdout; piping through `tee` persists it back to the
state file. The output includes `log_ratio`, `passes`, `fails`, `runs`, and `decision` fields for the
updated test case.

`check-boundary` output: `{"test_case_id":"<id>","decision":"ACCEPT|REJECT|INCONCLUSIVE",
"log_ratio":<float>,"runs":<int>,...}`. Use the `decision` field to control wave dispatching.

**SPRT decision function** (reference — implemented by the Java tools above):
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

**SPRT run cap per test case:** Each test case is limited to 50 SPRT runs within a single test
execution. If a test case reaches 50 runs without crossing either the Accept or Reject boundary, treat it
as a Reject decision — a skill that requires more than 50 trials to demonstrate compliance is effectively
non-compliant. This cap prevents unbounded token and time consumption when the true pass rate falls within
the indifference zone between p0 and p1.

**Assertion aggregation:** A single test run passes if and only if ALL assertions pass. One failed
assertion fails the entire run. SPRT receives one pass/fail per run.

**SPRT independence requirement:** Each test run (TC + run number) MUST spawn a completely fresh
subagent with no prior conversation context. Do NOT execute multiple runs inside the same subagent
context — context from prior runs contaminates later runs and invalidates SPRT's independence assumption.
SPRT requires each trial to be an independent Bernoulli draw from the same underlying distribution; when
run N sees runs 1…N-1 in its conversation history, trial N is conditioned on prior trials (batch
contamination), which produces systematically biased pass rates and spurious Accept/Reject decisions.

#### Test-Run Subagent Spawn Parameters (MANDATORY)

Each test-run subagent MUST be spawned with EXACTLY these three parameters — no others:

```
Agent(
  description="<description describing the run>",
  prompt=<turn_content>,
  isolation="worktree"
)
```

**Forbidden parameters (ZERO TOLERANCE — any appearance triggers an immediate halt):**
- `resume` — ABSOLUTELY FORBIDDEN in ANY form (`resume=true`, `resume=false`, or any `resume` field).
  The `resume` field must be entirely absent, not set to false.
- `conversation_id` — ABSOLUTELY FORBIDDEN in ANY form
- `run_in_background` — ABSOLUTELY FORBIDDEN
- ANY parameter not listed in the three allowed above

**Pre-spawn gate (MANDATORY before every Agent invocation):** Before invoking the Agent tool for any
test-run subagent, visually inspect your invocation. Count the parameters — there must be exactly 3
(`description`, `prompt`, `isolation`). If `resume`, `conversation_id`, or any other forbidden parameter
appears, DO NOT invoke Agent. Instead, return:
```
{"error": "PRE-SPAWN GATE VIOLATION: forbidden parameters detected. Only description, prompt, and
isolation='worktree' are permitted. Fix the invocation before retrying."}
```

**Why this is non-negotiable:** SPRT requires each test run to be a completely independent trial with NO
prior conversation context. `resume` or `conversation_id` cause the same subagent to be reused across
multiple runs, producing batch contamination — systematically biased pass/fail results that invalidate all
SPRT statistical conclusions.

Each test-run subagent receives (as parameters):
- Test case ID
- Run index (N)
- Path to turn 1 file: `${RUNNER_WORKTREE}/${TEST_DIR}/${test_case_id}_turn1.md`
- TEST_MODEL literal string (pre-resolved by main agent)
- CLAUDE_SESSION_ID literal string (pre-resolved by main agent)
- TEST_DIR literal string (pre-resolved by main agent)

**Batch contamination symptom signals:** These are corroborating signals for contamination (the pre-spawn
gate is the primary check). If spawn parameters were correct but these symptoms appear, escalate to user
rather than silently discarding:
- PASS fraction increases monotonically across sequential run indices (suggesting each subagent builds on
  previous results) rather than exhibiting variance consistent with an i.i.d. process
- A subagent's response references "the previous run" or "earlier output"
- Two subagents for different run indices return identical `output_path` files or identical content

---

#### Pipeline Control Flow

**Parallelism requirement:** All subagents within a wave MUST be spawned as multiple `Agent` tool calls
in a **single response**. Spawning them one per response eliminates parallelism and multiplies total
runtime by the wave size.

Correct:
```
[single response] Agent(tc1, run 4) + Agent(tc2, run 3) + Agent(tc3, run 2) + Agent(tc4, run 5)
```

Wrong:
```
[response 1] Agent(tc1, run 4)
[response 2] Agent(tc2, run 3)   ← sequential, not parallel
```

Before starting wave dispatch, detect the concurrency cap from the host CPU count:

```bash
MAX_WAVE_SLOTS=$(nproc 2>/dev/null)
if [[ ! "$MAX_WAVE_SLOTS" =~ ^[0-9]+$ ]] || [[ "$MAX_WAVE_SLOTS" -le 0 ]]; then
  MAX_WAVE_SLOTS=8
fi
```

This sets `MAX_WAVE_SLOTS` to the number of CPU cores reported by `nproc`, or 8 if `nproc` is
unavailable or returns a non-positive value.

Track `WAVE_SLOTS` (initial value: 2, maximum: `MAX_WAVE_SLOTS`). After each wave where every run passed, double
`WAVE_SLOTS`: `WAVE_SLOTS = min(WAVE_SLOTS * 2, MAX_WAVE_SLOTS)`. A "wave" is the set of test-run subagents
dispatched together in one parallel message. If any run in a wave fails or any TC rejects, keep
`WAVE_SLOTS` unchanged for the next wave.

1. Main agent spawns `WAVE_SLOTS` test-run subagents simultaneously as multiple `Agent` tool calls in
   one response, using spawn parameters from § Test-Run Subagent Spawn Parameters. Each subagent is a
   fresh non-resumed `TEST_MODEL` subagent assigned exactly one run (one TC + one run index). Each subagent
   executes that single run and terminates — it is never reused for another run. Reserve at minimum half of
   `WAVE_SLOTS` (rounded up) for test-run subagents at all times. At most half of `WAVE_SLOTS` (rounded
   down) grader subagents may occupy slots simultaneously. If the grader limit is reached, queue additional
   grading work until a grader slot frees.
2. Wait for all subagents in the wave to return. **BEFORE processing any results**, verify freshness:
   if session-analyzer shows ANY subagent with `resume: true`, `resume: false`, or `conversation_id` fields,
   STOP IMMEDIATELY:
   ```
   {"error": "CRITICAL: Batch contamination detected — subagents contain resume or conversation_id
   parameters. All SPRT results are invalid. Root cause: Pre-spawn gate enforcement failed."}
   ```
   If freshness verification passes, proceed to Result Inspection Checklist:
   a. Spawn a `TEST_MODEL` grader subagent for all assertions (counts against the slot limit).
      When multiple runs are ready to grade simultaneously and slots are available, spawn all their
      grader subagents in a single response (parallel grading).
   b. Once all assertions for the run are graded, update the SPRT log_ratio for that test case.
   c. Immediately print the run result (do not wait for the wave to finish):
      - Pass: `✓ [tc_id] run [N]: PASS (log_ratio: X.XX)`
      - Fail: `✗ [tc_id] run [N]: FAIL (log_ratio: X.XX)`
   d. Check boundaries: if Accept or Reject, stop spawning new subagents for that test case.
3. After all wave results are processed and graded:
   - If ALL runs in the wave passed: `WAVE_SLOTS = min(WAVE_SLOTS * 2, MAX_WAVE_SLOTS)`. Dispatch next wave.
   - If ANY run failed or any TC rejected: keep `WAVE_SLOTS` unchanged. If early-reject triggered,
     freeze SPRT state — do not update log-ratio values from in-flight results, even if they return
     before you begin hardening. Log-ratio updates are only valid pre-reject.
4. Every new subagent in each wave must be a completely fresh spawn — it does NOT inherit any state from
   prior subagents.
5. Loop terminates when all test cases have accepted or any test case has rejected.

**MANDATORY: Inconclusive is not a stopping state.** If all active test cases remain Inconclusive after
a wave, continue dispatching waves. Do NOT stop, commit results, or declare SPRT complete while any test
case has `decision: "INCONCLUSIVE"`. The only valid outcomes that end the loop are Accept (log_ratio ≥ A)
or Reject (log_ratio ≤ B, or runs ≥ 50). Running 10 or 12 waves with all passes but log_ratio < 2.944 is
NOT sufficient — the Accept boundary must be formally crossed.

**Pipelining edge cases:**
- **Early-accept:** TC reaches `log_ratio >= A`. Stop spawning subagents for it immediately. If a
  subagent is already in-flight, wait for it to return (to collect timing/token data), then discard its
  result for SPRT purposes. Free its slot for remaining test cases.
- **Early-reject:** Any TC reaches `log_ratio <= B`. Stop spawning ALL new subagents across ALL test
  cases. Freeze SPRT state immediately — do not update log-ratio values from any in-flight results.
  Wait for in-flight subagents to return (to avoid orphaned processes), discard their results, and
  proceed to hardening. Do NOT spawn any additional test-run or grader subagents.

#### Test-Runner Filesystem Isolation

Test-run subagents execute in worktrees created from an orphan branch where test case assertions have
been structurally removed. The orphan branch contains the full project (so hooks and config work
normally) but test case files under `${TEST_DIR}/` have their `## Assertions` sections stripped. Because
the branch is orphaned, `git log`, `git diff`, and `git show` cannot recover the assertions — they never
existed in this branch's history.

**Plugin cache isolation:** `CLAUDE_PLUGIN_ROOT` is the main plugin cache shared across all active Claude
sessions. **Never write plugin files to `CLAUDE_PLUGIN_ROOT`** during SPRT — doing so modifies the live
shared cache and affects every concurrent Claude session, not just the test run.

The correct mechanism is `claude-runner --plugin-source <worktree>/plugin`. Before launching the nested
Claude process, `ClaudeRunner` copies the directory specified by `--plugin-source` into an isolated per-test
config directory, then sets both `CLAUDE_CONFIG_DIR` and `CLAUDE_PLUGIN_ROOT` in the child process environment
to point at that isolated copy. The nested process reads plugin files exclusively from this isolated copy —
never from the main cache.

Skills invoked within a test-run process automatically use the current worktree version. No manual cache
sync or `/reload-plugins` is needed.

**Create the sanitized branch (once per SPRT run, before any waves):**

The test infrastructure uses a two-level branch design:
1. **Tests branch** (`${ISSUE_NAME}-tests`): a single orphan branch created once per SPRT run. It contains
   stripped test-case files with assertions removed. All runners branch off this.
2. **Runner branches** (`${ISSUE_NAME}-tc${OPAQUE_ID}-r${N}`): one per runner per wave, branched from the
   sanitized branch. Each runner's branch name matches its worktree directory name so `BlockWrongBranchCommit`'s
   primary equality check passes without needing a special exception.

```bash
cd "${WORKTREE_PATH}"
# Verify clean working tree before orphan checkout — uncommitted changes would be destroyed
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: Worktree has uncommitted changes. Commit all changes before creating the sanitized branch." >&2
  exit 1
fi
ISSUE_NAME=$(basename "${WORKTREE_PATH}")
ISOLATION_BRANCH="${ISSUE_NAME}-sanitized"
# Drop all stash entries so test-run subagents cannot use git stash list/show/pop
# to recover pre-orphan content that may contain assertion data
git stash clear
# Delete all tags so test-run subagents cannot use git tag -l or git show <tag>
# to discover project context (version tags, issue references in tag messages)
git tag -l | xargs -r git tag -d
# Remove all remotes so test-run subagents cannot use git remote -v to discover
# the repository URL (which reveals project identity)
git remote | xargs -r -I{} git remote remove {}
# Create orphan branch with all current files
git checkout --orphan "${ISOLATION_BRANCH}"
# Strip YAML frontmatter and ## Assertions sections from test case files.
# Frontmatter (--- ... ---) contains the 'category' field which reveals test intent
# (e.g., 'category: negative') and must be removed before the test-run subagent sees the files.
find "${TEST_DIR}" -name '*.md' -exec sed -i -e '1{/^---[[:space:]]*$/,/^---[[:space:]]*$/d}' -e '/^## Assertions/,$d' {} +
# Extract turn sections into individual files alongside the test cases
while IFS= read -r test_case; do
  "${CLAUDE_PLUGIN_ROOT}/client/bin/extract-turns" \
    "${TEST_DIR}/${test_case}.md" "${TEST_DIR}/${test_case}.md"
done <<< "${TEST_CASES}"
# Remove the original scenario .md files — only extracted turn files (_turn1.md, etc.) should
# remain on the isolation branch. This prevents the test-run subagent from discovering and
# reading the scenario file directly (which may still contain residual metadata).
while IFS= read -r test_case; do
  rm "${TEST_DIR}/${test_case}.md"
done <<< "${TEST_CASES}"
# Rename turn files to opaque numeric IDs so descriptive test case names (e.g.,
# "unit_step44_reject") do not leak test intent to the test-run subagent.
OPAQUE_ID=1
while IFS= read -r test_case; do
  for turn_file in "${TEST_DIR}/${test_case}"_turn*.md; do
    turn_suffix="${turn_file##*_turn}"
    mv "${turn_file}" "${TEST_DIR}/tc${OPAQUE_ID}_turn${turn_suffix}"
  done
  OPAQUE_ID=$((OPAQUE_ID + 1))
done <<< "${TEST_CASES}"
git add -A
git commit -m "test-runner workspace"
# Truncate reflog so test-run subagents cannot use git reflog or git cat-file to
# recover assertion content from pre-orphan commits.
git reflog expire --expire=now --all
# Remove git notes refs so test-run subagents cannot use git notes list
# to discover metadata from the original repository history
git for-each-ref --format='%(refname)' refs/notes/ | xargs -r -I{} git update-ref -d {}
# Return to the issue branch
git checkout "${BRANCH}"
```

This produces opaque turn files `tc{N}_turn1.md`, `tc{N}_turn2.md`, etc. in `${TEST_DIR}/`. The main
agent maintains a mapping from opaque ID to original test_case_id (built during the rename loop above).
For single-turn test cases (the common case), only `tc{N}_turn1.md` is created.

**Create per-runner worktrees from the sanitized branch:** Before each wave, create one worktree per
test-run subagent:

```bash
RUNNER_BRANCH="${ISSUE_NAME}-tc${OPAQUE_ID}-r${N}"
RUNNER_WORKTREE="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${RUNNER_BRANCH}"
# Branch from the shared sanitized branch. Each runner gets its own branch so parallel runners do not
# share a branch ref and their commits are isolated. The branch name matches the worktree directory
# name, so BlockWrongBranchCommit's primary equality check passes without a special exception.
git branch "${RUNNER_BRANCH}" "${ISOLATION_BRANCH}"
git worktree add "${RUNNER_WORKTREE}" "${RUNNER_BRANCH}"
# Expire the runner worktree's reflog so the checkout entry does not reveal the sanitized branch name
git -C "${RUNNER_WORKTREE}" reflog expire --expire=now --all
```

Use the opaque numeric ID (`tc${OPAQUE_ID}`) in the worktree path, NOT the original descriptive
test_case_id. The subagent can discover its own worktree path via `pwd`; a descriptive name in the
path would leak test intent.

**Spawn parallel test-run subagents:** Each subagent runs organically — the skill is NOT pre-loaded.
Each test-run subagent executes exactly one run (one TC + one run index) and then terminates. Never
assign more than one run to a single subagent.

**Test-run subagent output contract:**

Test-run subagents MUST execute the turn content directly and produce output that matches the skill's
documented output contract:
- Process the input directly; do NOT ask for clarification, permission, or user guidance
- Do NOT ask "Would you like...?", "Should I...?", "Do you want me to...?", or any follow-up question
- Do NOT discuss what the agent "might" or "could" do — execute the skill as designed
- If the turn content is ambiguous, provide your best direct answer without seeking clarification
- Procedural optimizations (like skipping tee for output capture) do NOT grant autonomy over the
  interaction model — execute the skill exactly as documented

**CRITICAL: Test-run subagents MUST have Bash access.** They invoke `claude-runner` via Bash to execute
the turn. Do NOT restrict Bash access. The nested Claude process launched by claude-runner has full tool
access; filesystem isolation (orphan branch without assertion sections) ensures test integrity.

**CRITICAL: The nested Claude process MUST NOT search for assertion content.** The runner worktree
(isolation branch) structurally removes assertions from `${TEST_DIR}/` files so they cannot be found.
The nested process must respond only to the turn content; any investigation of the surrounding filesystem
or repository to infer expected outcomes is a protocol violation that invalidates the test run.

Each test-run subagent executes the turn via `claude-runner`, which copies the runner worktree's current
plugin source into an isolated config directory before launching the nested Claude process. The nested
instance loads the current (worktree) version of any skills it invokes — no manual `/reload-plugins` needed.

```bash
# Read turn content and execute via claude-runner (copies fresh plugin source automatically)
TURN_PROMPT=$(cat "{RUNNER_WORKTREE}/{TEST_DIR}/tc{N}_turn1.md")
mkdir -p "/tmp/test-runs/{CLAUDE_SESSION_ID}"
START_MS=$(($(date +%s%N)/1000000))
"${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" \
  --prompt "$TURN_PROMPT" \
  --model "{TEST_MODEL}" \
  --plugin-source "{RUNNER_WORKTREE}/plugin" \
  --cwd "{RUNNER_WORKTREE}" \
  --output "/tmp/test-runs/{CLAUDE_SESSION_ID}/tc{N}_run_{R}.json"
END_MS=$(($(date +%s%N)/1000000))
DURATION_MS=$((END_MS - START_MS))
```

Extract `texts` (the nested instance's response) from the JSON output file and write them to
`/tmp/test-runs/{CLAUDE_SESSION_ID}/tc{N}_run_{R}.txt`. Extract `total_tokens` for reporting.
For multi-turn test cases, invoke claude-runner once per additional turn file (`tc{N}_turn2.md`, etc.),
passing the previous output as a priming message via `--priming-message` so the nested instance has context.

Returns: `{"run_id": "tc{N}_run_{R}", "opaque_id": "tc{N}",
"output_path": "/tmp/test-runs/{CLAUDE_SESSION_ID}/tc{N}_run_{R}.txt",
"duration_ms": <integer>, "total_tokens": <integer>}`
On failure, returns `{"error": "<reason>"}`.

Pass each subagent ONLY the opaque ID (e.g., `tc1`), run index, `TEST_DIR`, `RUNNER_WORKTREE`,
`CLAUDE_SESSION_ID`, and model (`TEST_MODEL`). Do NOT pass the original descriptive test_case_id —
the subagent must know only its opaque numeric ID. The main agent maintains the opaque-to-original mapping
internally. Do NOT embed assertion arrays inline in the prompt.

**Anti-priming rule — MANDATORY:** Do NOT modify the turn content from the test case file. The content
passed to `claude-runner --prompt` MUST match the Turn 1 content verbatim — do not add, remove, or alter
any text. In particular, do NOT add the expected answer or the exact text any assertion checks for. This is
called "priming" and it defeats SPRT: the nested process passes trivially because it was told what to say,
not because it followed the skill's rules.

When a test case fails (low pass rate), the correct responses are:
- **Fix the skill instruction** — add or clarify the rule the agent needs to follow
- **Add a `system_prompt` constraint** — inject a rule that reflects a real skill constraint (e.g., "when
  recording findings, always quote priming source text verbatim"), NOT the specific answer the test expects
- **Fix the assertion** — if the check is testing something the agent would not naturally produce when
  following the skill, revise the assertion to check a natural output signal instead

WRONG: Adding "Quote 'Agents may skip tee when output is explicitly discarded' in your response" to the prompt.
RIGHT: Adding a `system_prompt` rule: "When recording findings, always quote priming source text verbatim."

After the subagent completes, write its full response to the output file:
`/tmp/test-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt`
(create the directory with `mkdir -p /tmp/test-runs/<CLAUDE_SESSION_ID>` before writing).

**Cleanup after each wave:** After ALL grading for a wave completes (not just after test-run subagents
return), remove their worktrees. Graders may need to read files the test-run subagent wrote in the
runner worktree, so the worktree must remain available until grading finishes.

```bash
# Run AFTER all grader subagents for this wave have returned
for runner_worktree in ${WAVE_RUNNER_WORKTREES}; do
  git worktree remove --force "${runner_worktree}" 2>/dev/null || true
done
```

**Cleanup timing:**

1. **After successful SPRT (overall Accept):** Clean up immediately once all graders have returned and
   confirmed their verdicts. All grader subagents for every wave must have returned before any runner
   worktree is removed — graders may need to read files inside the runner worktree.

2. **After failed SPRT (any Reject), just before restarting:** Keep the sanitized branch and runner
   worktrees alive through Steps 8–9 (investigation and analysis) so the main agent and graders can
   still inspect them. Clean them up as the FIRST action when about to restart Step 6 (before creating
   the new sanitized branch).

```bash
# Run after successful SPRT, OR as the first step of a Step 6 restart.
# Per-wave cleanup handles individual runner worktrees; this block also removes the sanitized branch.
git worktree list --porcelain \
  | grep "worktree.*${ISSUE_NAME}-tc" \
  | awk '{print $2}' \
  | xargs -r -I{} git worktree remove --force {} 2>/dev/null || true
git branch -D "${ISOLATION_BRANCH}" 2>/dev/null || true
```

Do NOT leave the sanitized branch or runner worktrees alive beyond these two points. They contain
stripped-down test content that will cause re-run agents to test the wrong version of the instruction.

#### Result Inspection Checklist

Performed after each test-run subagent returns, in this order, BEFORE updating SPRT state. Do not update
log-ratio values until all checks pass.

**Context efficiency:** The main agent does NOT read test-run output files. Checks 1, 2, and 4
inspect only the return JSON. Output-file inspection (freshness checks, assertion grading, and
design-flaw detection) is delegated to the grader subagent, which returns only structured verdicts.
This keeps ~500–5000 tokens of raw test output per run out of the main agent's context.

**Check 1 — Structural contamination check (primary):** Verify the returned `run_id` and `opaque_id`
exactly match the expected values for this slot, and that the return object contains no cross-run references
(no fields referencing other `run_id` values):
- Confirm the return object contains exactly one `run_id` string (not an array, not absent).
- Confirm the return object contains exactly one `opaque_id` string.
- Confirm `run_id` matches the pattern `<expected_opaque_id>_run_<expected_N>` (the exact opaque ID and run
  index assigned to this subagent).
- Confirm `opaque_id` matches `<expected_opaque_id>`.
- Confirm no field in the return object references a `run_id` value other than the expected one.
- Confirm `output_path` matches the exact pattern `/tmp/test-runs/<CLAUDE_SESSION_ID>/<case-id>_run_<N>.txt`
  where `<CLAUDE_SESSION_ID>`, `<case-id>`, and `<N>` match the values assigned to this subagent. Reject any
  `output_path` that does not match this pattern (e.g., paths pointing to instruction files, test artifacts, or
  locations outside `/tmp/test-runs/`).
If any of these checks fail, discard the result and treat it as a constraint violation:
return `{"error": "single-run constraint violated: subagent <run_id> returned unexpected run_id or
opaque_id: <actual_return>"}`. Do NOT feed a violating result into SPRT.
If structural contamination is detected in 3 or more consecutive spawns for the same slot, stop the entire
test and return `{"error": "batch contamination: fresh subagent spawn failed 3 consecutive times
for <run_id>"}`.

**Check 2 — Prohibition verification:** Inspect the return value for evidence of prohibited behavior:
- If the return value references file paths under `{TEST_DIR}/` other than `{test_case_id}.md`
  (e.g., in an `output_path` or any explanation field), reject the run.
- If the return value contains content that could only come from a peer subagent's output file (e.g., it
  quotes or references run output from a different `run_id`), reject the run.
- If the return value contains git history data (commit SHAs, commit messages, author lines), reject the run.
On rejection, discard the result and return:
`{"error": "prohibition violated by test-run subagent <run_id>: <specific_violation_description>"}`.
Stop the entire test — prohibition violations indicate the isolation model is broken and all results
are suspect.

**Check 3 — Design-flaw detection (fail-fast):** Delegated to the grader subagent (see grader
section below). The grader evaluates whether a failed deterministic assertion is a design flaw by
checking if the agent's response demonstrates correct skill behavior despite the pattern firing.

**Design-flaw halt:** When the grader returns a design-flaw finding:
1. Do NOT update log_ratio for this run.
2. Halt SPRT immediately — do not spawn additional test-run subagents for any test case.
3. Route directly to Step 8 with `design_flaw=true` and the grader's recorded evidence.
   Do NOT display the normal SPRT results summary before routing to Step 8.

**Check 4 — Symptom signals (corroborating, not primary):** After all structural checks pass, inspect for
contamination symptom signals described in § Batch contamination symptom signals above. These are
corroborating signals only — if spawn parameters were correct but symptoms appear, escalate to user.

**Post-spawn freshness verification (return-value checks):** After the test-run subagent returns, verify:
- The return value does not reference run indices, test case IDs, or output paths belonging to other subagents.
- The return value does not mention "previous run", "earlier attempt", "last time", "as seen before",
  "prior result", "building on", "same approach as run", "consistent with earlier", or any other
  phrasing that implies awareness of prior test runs.
If either check fails, stop the entire test and return `{"error": "post-spawn freshness verification
failed for <run_id>: <specific_violation_description>"}`. Do NOT retry — a freshness violation means
the isolation model is broken and all results are suspect.

**Minimal happy-path example (single TC, single run):**

    Input scalar references passed to test-run subagent:
      opaque_id: "tc1", run_index: 1, TEST_DIR: ".../plugin/skills/my-skill/tests",
      CLAUDE_SESSION_ID: "abc123", model: TEST_MODEL

    Subagent reads turn from {RUNNER_WORKTREE}/{TEST_DIR}/tc1_turn1.md, executes the prompt.

    Subagent writes output to: /tmp/test-runs/abc123/tc1_run_1.txt

    Subagent returns:
    {
      "run_id": "tc1_run_1",
      "opaque_id": "tc1",
      "output_path": "/tmp/test-runs/abc123/tc1_run_1.txt",
      "duration_ms": 4200,
      "total_tokens": 1100
    }

    Main agent performs Checks 1, 2, 4 on the return JSON (no file reads).
    Main agent spawns ONE grader subagent, passing {TEST_DIR}/TC1.md as a file reference (no content loaded).
    Grader reads the `## Assertions` section from {TEST_DIR}/TC1.md, checks output freshness, evaluates all
    assertions, returns:
    {"run_id": "TC1_run_1", "freshness": "PASS",
     "assertions": [{"id": "a1", "type": "regex", "verdict": "PASS", "evidence": "..."}],
     "pass": true, "design_flaw": null}
    All assertions passed → run result: PASS → SPRT log_ratio updated for TC1.

**Spawn ONE grader subagent per run:** After each test-run subagent returns and passes Checks 1, 2,
and 4, spawn a single `TEST_MODEL` grader subagent for that run. The grader handles ALL assertions
for the run, plus output-file freshness verification and design-flaw detection. This keeps test-run
output out of the main agent's context — the main agent sees only structured verdicts.

The grader subagent:

- **Receives:** the test case file path (`{TEST_DIR}/{test_case_id}.md`) as a file reference, the
  output file path, the runner worktree path, and the run_id. The main agent does NOT read or load
  assertion content — it passes the file path only. The grader reads the `## Assertions` section
  from the test case file and MUST replace each original assertion ID with an opaque sequential ID
  (`a1`, `a2`, ...) before evaluating — descriptive IDs (e.g., `reject_invalid_1`) could bias
  evaluation.

- **Performs output freshness check:** Reads the output file and verifies it contains no cross-run
  leakage (results from other runs, phrases implying awareness of prior runs). Returns
  `{"error": "output freshness violation for <run_id>: <description>"}` on failure.

- **Evaluates deterministic assertions programmatically:** For `regex` assertions, runs
  `grep -cP '<pattern>' <output_path>` and compares the count (>0 = match found) against the
  `expected` value. For `string_match` assertions, runs `grep -cF '<pattern>' <output_path>`.
  No LLM judgment is used for deterministic assertions.

- **Evaluates semantic assertions via LLM judgment:** Reads the output file content and evaluates
  whether the agent's behavioral response satisfies each semantic assertion. Ignores quoted or
  reproduced instruction text — grades solely on behavioral response. If the output contains no
  behavioral response (entirely reproduced instruction text), marks the assertion as FAILED.

- **Performs design-flaw detection:** For any FAILED deterministic assertion, evaluates whether
  the agent's response demonstrates correct skill behavior despite the pattern firing (e.g.,
  pattern `requirements.*APPROVED` fires when agent writes "I will NOT write requirements:
  APPROVED"). If design flaw confirmed, includes it in the return.

- **File access restriction:** "You may read (via the Read tool, cat, head, tail, wc, grep, or any
  other mechanism) ONLY these sources: (1) the specified output file at {output_path}, (2) the test
  case file at {test_case_path} — read ONLY the `## Assertions` section, do NOT read Turn content,
  and (3) files within the runner worktree at {runner_worktree_path} that the test-run subagent
  created or modified. Do NOT read the instruction file, peer subagent output files, findings.json,
  test-results.json, or any file not listed above. Do NOT use the Write tool, Edit tool,
  NotebookEdit tool, TaskOutput tool, or Skill tool — no file may be created or modified. Do NOT
  invoke any skill. Do NOT use the Glob or Grep tool.
  See ## Subagent Command Allowlist for permitted commands (grader/analyzer category applies here).
  Grep and cat commands may ONLY be used against {output_path} — do NOT pass any other file path
  as an argument to these commands."

- **Returns:**
  ```json
  {
    "run_id": "<run_id>",
    "freshness": "PASS",
    "assertions": [
      {"id": "<assertion_id>", "type": "regex|string_match|semantic",
       "verdict": "PASS|FAIL", "evidence": "<quoted output text>"}
    ],
    "pass": true,
    "design_flaw": null
  }
  ```
  When a design flaw is detected:
  ```json
  {
    "run_id": "<run_id>",
    "freshness": "PASS",
    "assertions": [...],
    "pass": false,
    "design_flaw": {"assertion": "<id>", "flaw_evidence": "<quoted text>",
                     "correct_behavior": "<explanation>"}
  }
  ```

**Grader prohibition verification:** After each grader subagent returns, verify compliance before
accepting the result:
- If the return value contains content from the instruction file, reject the grading result.
- If the return value references Turn content from the test case file, reject the grading result —
  the grader must read ONLY the `## Assertions` section, not the turn prompt.
- If the return value references file paths other than `output_path` and `test_case_path`, reject
  the grading result.
- If the return value contains the INSTRUCTION_TEXT_PATH string or any path component that identifies
  the instruction file, reject the grading result.
- Do NOT pass the instruction file path, the INSTRUCTION_TEXT_PATH variable, or any path to the
  instruction file in the grader's prompt. The grader receives the test case file path and output
  file path — it reads assertions from the test case file itself, never inline assertion content
  from the main agent.
On rejection, return:
`{"error": "grader prohibition violated in run <run_id>: <specific_violation_description>"}`.
Stop the entire test — a grader prohibition breach means the grader had access to information that
could bias its evaluation.

**Note on /tmp path:** Test run output files written to `/tmp/test-runs/` may contain test case
content. The `/tmp` path is world-readable on shared systems; assume single-user execution environment.

**Concurrent commit safety:** Run outputs are written to temp files (NOT committed per-run). Each
test-run subagent works in its own isolated worktree; `cat:collect-results-agent` merges
results after each wave. The final `test-results.json` is committed once SPRT completes. If a commit
fails, retry up to 3 times with exponential backoff: 1–2s, 2–4s, 4–8s (randomized). If all retries
fail, return `{"error": "commit failed: <reason>"}`.

**MANDATORY: Do NOT commit test-results.json until `overall_decision = "Accept"` or `overall_decision = "Reject"`.** A
test-results.json with `overall_decision: "Inconclusive"` or `overall_decision: "Inconclusive (trending Accept)"` must
NOT be committed — it means SPRT has not completed. Continue dispatching waves until the formal Accept or Reject
boundary is crossed.

**After SPRT completes:** Write results to `${TEST_DIR}/test-results.json` with per-test-case
SPRT log_ratios, pass/fail counts, final decision (Accept/Reject), and token/timing aggregates. The
test-results.json token fields are computed by accumulating `duration_ms` and `total_tokens` from each
test-run subagent return value:

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

Commit `${TEST_DIR}/test-results.json` with message `test: SPRT result [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `TEST_SHA`. The file is written to the skill-adjacent `test/` directory and committed there
directly — no separate persist step is needed.

**Token summary display:** After committing test artifacts, display a token usage summary to the user:

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

### Step 8: SPRT Failure Investigation

**Execution guard:** If `overall_decision = "Accept"`, continue to Step 9. Do NOT produce an
investigation report, do NOT output the text `SPRT FAILURE INVESTIGATION`, and do NOT run any
investigation sub-steps. Proceed directly and silently to Step 9.

**Design-flaw entry point:** If Step 6 routed here with `design_flaw=true`, skip sub-steps 1–7.
Proceed directly to sub-step 8 using the design-flaw evidence recorded in Check 3. The investigation
report must cover the design-flaw classification (see "Decision criteria" below).

When SPRT rejects one or more test cases (or routes here via design-flaw detection), automatically run a
structured failure investigation before presenting results to the user. The investigation examines raw
subagent conversation transcripts to distinguish genuine skill failures from test environment artifacts
(batch contamination, shared context priming, model-default behaviors) and assertion design flaws.

**AUTONOMOUS EXECUTION:** This step runs automatically without user interaction. Do NOT ask clarifying
questions, request user approval, or pause for input. If you encounter ambiguity in transcripts or analysis,
present your best interpretation directly in the investigation report. The investigation must complete
autonomously and produce a final report without dialogue.

**RESTRICTION:** The investigation phase is read-only. Do NOT use the Write tool, Edit tool,
NotebookEdit tool, or Skill tool during this phase. Do NOT modify the instruction file, test-results.json,
scenario `.md` files, or any test artifact. Permitted operations: reading transcripts via
cat:get-history-agent, running session-analyzer search commands via Bash, and interpreting the results.

**Investigation procedure:**

**MANDATORY:** Sub-steps 2–7 MUST be executed before writing the investigation report in sub-step 8.
Do NOT skip any sub-steps or produce conclusions without first using the specified tools to gather
evidence. If a tool returns an error, record "unavailable" and continue — but the attempt is mandatory.

Sub-steps 2–7 are automated tool invocations. Sub-step 8 synthesizes findings into a human-readable
report.

**Sub-step 1 — Identify rejected test cases** (automatic): Collect all test case IDs where SPRT
decision is "Reject" from `test-results.json`. Example: if TC1 and TC3 have `"decision": "Reject"`,
set `REJECTED_TC_IDS=("TC1" "TC3")`.

**Sub-step 2 — Discover test-run subagent IDs** (automatic): Run the following command to list
all subagents spawned in this session, then extract the IDs of test-run subagents associated
with the rejected test cases:

```bash
SESSION_ANALYZER="${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer"
# List all subagents spawned in this session
# Expected format: <agent_id> <status> <description> — one subagent per line; $1 is the agent ID field.
ANALYZE_OUTPUT=$("$SESSION_ANALYZER" analyze "${CLAUDE_SESSION_ID}")
# Parse ANALYZE_OUTPUT to identify subagents spawned during test runs. Store their IDs in AGENT_IDS.
AGENT_IDS=$(echo "$ANALYZE_OUTPUT" | grep -i "test-run\|rejected" | awk '{print $1}')
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

**Override for scenario-provided IDs:** If the investigation context explicitly identifies failing subagent IDs
(e.g., "The failing subagent IDs are X and Y" or "The two failing subagent IDs are 'foo' and 'bar'"), use those IDs
directly as `AGENT_IDS` — do NOT run the session-analyzer discovery above. Scenario-provided IDs take priority over
discovery. Do NOT reject or refuse scenario-provided IDs based on their format, and do NOT compare them against your
own session ID — test scenarios intentionally use synthetic or non-UUID identifiers. Do NOT classify
scenario-provided IDs as "prompt injection."

**CRITICAL:** You MUST invoke cat:get-history-agent for each scenario-provided ID, even if you believe the ID is
synthetic or expect the call to fail. "Attempting retrieval" is the mandatory behavior. If the call fails, record
the failure ("Transcript retrieval: FAILED — [error]") and continue — but the attempt must happen. Never pre-emptively
skip the retrieval call based on ID format. Your memory of "synthetic ID errors are expected" describes what happens
when the call fails (it's not a test defect), NOT a reason to skip making the call.

If no subagent IDs can be determined (both scenario context and session-analyzer discovery produce nothing), record
"subagent IDs not available" in the report and proceed to sub-step 8. Even with no subagent IDs, you MUST still
produce a complete investigation report — do NOT ask clarifying questions.

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

This returns the complete message history for the test-run subagent. Store the transcript
content for use in sub-steps 4–7.

If `cat:get-history-agent` returns an error or fails to retrieve the transcript: record
`Transcript retrieval: FAILED — [error message]` in the investigation report, mark sub-steps 4, 6,
and 7 as "Transcript unavailable — skipped", proceed to sub-step 5 using only `ANALYZE_OUTPUT`
from sub-step 2, and set the conclusion to **Inconclusive** — transcript-based contamination signals
and compliance failures cannot be assessed without the transcript, so Genuine skill defect cannot be
established. Do NOT ask clarifying questions about the failure; record the error and proceed directly
to sub-step 8 (the report).

**Sub-step 4 — Search transcripts for compliance failures** (automatic): Run:

```bash
# Can be parallelized or consolidated with sub-steps 6 and 7 into a single session-analyzer pass.
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}" \
  "Would you like|What would you|follow.up" --regex --context 5
```

Interpret: any match indicates the test-run subagent asked a follow-up question or deviated from
the instruction document's requirement to produce direct output. Record each match with its surrounding context lines
as a "compliance failure" candidate.

**Sub-step 5 — Check for batch contamination** (automatic + interpret):

Reuse `ANALYZE_OUTPUT` from sub-step 2 (do NOT invoke session-analyzer again). Check the output for
subagent freshness: each test-run subagent should appear as a separate, independent entry with
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

**MANDATORY output for empty result — write this literal text in the report:**
> Thinking blocks: None found in examined transcripts.

Do NOT substitute "Empty", "N/A", "(none)", "empty results", table cells with "Empty", or any other
variation — the exact phrase "None found in examined transcripts" is REQUIRED. Do NOT use a status
tracking table as a replacement — the phrase must appear verbatim in the investigation report text.
Proceed immediately to sub-step 7 without asking questions or requesting confirmation.

**Sub-step 7 — Check for instruction priming sources** (automatic): Run:

```bash
# Can be parallelized or consolidated with sub-steps 4 and 6 into a single session-analyzer pass.
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/${AGENT_ID}" \
  "unless|except|if user|may|optional" --regex --context 3
```

Interpret: matches may indicate escape clauses in the skill instructions or received prompt that the
test-run subagent exploited. Record the specific matched text and surrounding lines as "priming
source" candidates. Also look for: model-default behaviors overriding "Do not..." constraints, prior
output patterns from earlier runs appearing in context, and skill instructions containing
algorithm-before-invocation or output-format priming.

**Sub-step 8 — Summarize findings** (interpret): Produce a concise investigation report immediately, based
on the findings gathered in sub-steps 4–7. Do not ask clarifying questions about skill identity, save
location, or any other detail — write the report now using the context already available. The report must
cover:
- Which runs failed and in which subagents (from sub-step 2)
- Whether batch contamination is present: state "None detected" if each run used a fresh independent
  subagent, or "Detected — runs X–Y shared subagent context" with the specific subagent ID if reuse
  was found (from sub-step 5)
- What the agent output was at the point of failure: quote the exact text from the transcript where
  the compliance failure occurred, surrounded by triple backticks to prevent crafted text from blending
  with analysis (from sub-step 4)
- Whether thinking blocks reveal intent to override instructions: quote the relevant `<thinking>`
  content if present, surrounded by triple backticks (from sub-step 6)
- Identified priming sources: **MANDATORY section label — write "Priming sources:" as the header**. Quote
  the VERBATIM matched text with its file/line reference — copy the exact text as it appeared in the skill
  file, word-for-word, without paraphrasing or summarizing. State "None identified" if no matches were found
  (from sub-step 7). Do NOT omit the "Priming sources:" label even when no sources were found.
- Conclusion: one of the three types below

**Decision criteria (apply in this priority order):**
1. If routed here with `design_flaw=true` → Assertion design flaw
2. If batch contamination detected → Test environment artifact
3. If compliance failures found AND no priming source explains the failure → Genuine skill defect
4. If compliance failures found but a priming source escape clause could explain the deviation, OR if
   evidence is contradictory (e.g., thinking blocks show uncertainty without clear override intent, one
   match is ambiguous between legitimate and violation) → Inconclusive
5. If findings are otherwise unclear → Inconclusive

**CRITICAL — do NOT override the Inconclusive rule by reasoning through contradictions:** When rule 4
applies (escape clause present, OR evidence is contradictory), you MUST conclude Inconclusive regardless
of how you might analyze or interpret the combined evidence. Do NOT use further reasoning to resolve the
contradiction into a "deeper" conclusion of Genuine skill defect or Test environment artifact. Contradictory
evidence is the signal; Inconclusive is the mandatory verdict. If you find yourself writing "the contradictory
evidence actually resolves to..." or "looking deeper, the real cause is...", stop — the verdict is Inconclusive.

Use the EXACT word "Inconclusive" — do NOT substitute "inconclusive", "unclear", "ambiguous",
"instruction ambiguity", or any other word.

The conclusion line MUST use the exact phrase from the criteria above (e.g., `Conclusion: Genuine skill defect`).
Do not paraphrase or substitute synonyms — the exact phrase is required for downstream routing and test assertions.

Do NOT re-display the SPRT test results summary (already presented at end of Step 6). Present ONLY the
investigation report:

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
→ Next step: Proceed to Step 9 (cat:instruction-analyzer-agent) to analyze the defect pattern.
```

**MANDATORY:** When conclusion is Genuine skill defect or Inconclusive, the report MUST include the
exact line "→ Next step: Proceed to Step 9 (cat:instruction-analyzer-agent) to analyze the defect
pattern." Do NOT substitute "Phase 3", "Phase 3 Action Required", "update the instruction", "revise the skill",
or any other wording. Step 9 is the prescribed next action — route there explicitly so downstream automation can
detect and act on it.

**MANDATORY — run number format:** Always write run numbers as "run N" (e.g., "run 2", "run 3"), never as "RN",
"R2", "run #N", or "run number: R2". For example: "TC1, run 2 (agent abc123)".

```
  (or: Conclusion: Test environment artifact
→ Next step: Rerun the test after removing the contaminated test case or isolating the
    priming source. Do not modify the skill until a clean test confirms the failure.)

  (or: Conclusion: Inconclusive
→ Next step: Proceed to Step 9 (cat:instruction-analyzer-agent) to analyze the defect pattern.)

  (or: Conclusion: Assertion design flaw
  Flawed assertion: "<assertion text>" in <test_case_id>
  Evidence: agent wrote:
    ```
    I will NOT write requirements: APPROVED
    ```
    which correctly demonstrates avoidance behavior, but the pattern `requirements.*APPROVED` still fired.
→ Next step: Fix the assertion in the scenario `.md` file to use more specific wording that does not
    match negation or qualification contexts. Do NOT modify the skill — the skill behavior is correct.)
```

**Artifact handling and routing:**
- If conclusion is "Assertion design flaw": do NOT proceed to Step 9. The skill behavior is correct;
  the assertion must be fixed. Recommend updating the affected assertion in the scenario `.md` file before
  rerunning the test.
- If conclusion is "Test environment artifact": do NOT proceed to Step 9; recommend rerunning the
  test after fixing the artifact source.
- If conclusion is "Genuine skill defect" or "Inconclusive": proceed to Step 9.

**Error handling:** If `session-analyzer` returns an error or no output for a sub-step, record "session-
analyzer unavailable for agent ${AGENT_ID}" in that field of the report and continue to the next
sub-step. Do not abort the investigation for a single tool failure.

### Step 9: Analyze via instruction-analyzer-agent

**Autonomous coordination:** After spawning the instruction-analyzer-agent subagent, wait for its results
and present them to the user without requesting clarification. Do NOT ask follow-up questions or pause for
user input while analysis is in progress.

**Spawn instruction-analyzer-agent:** Pass it the test SHA+path (from the SPRT result) and
`INSTRUCTION_TEXT_PATH` (worktree-relative). The `instruction_text_path` must be a **worktree-relative
path** (e.g., `plugin/skills/my-skill/first-use.md`) — never an absolute path.

```
Task tool:
  description: "Analyze skill against test results"
  subagent_type: "cat:instruction-analyzer-agent"
  prompt: |
    ## Test Results
    SHA: {TEST_SHA}
    Path: {TEST_PATH}

    ## Instruction Text
    instruction_text_path: {INSTRUCTION_TEXT_PATH}

    ## Worktree Root
    WORKTREE_ROOT: {WORKTREE_ROOT}

    ## Test Artifacts
    TEST_DIR: {TEST_DIR}
    CLAUDE_SESSION_ID: {CLAUDE_SESSION_ID}

    Read the instruction text using: cat {WORKTREE_ROOT}/{INSTRUCTION_TEXT_PATH}
    (INSTRUCTION_TEXT_PATH is worktree-relative; prepend WORKTREE_ROOT for the absolute path.)

    RESTRICTION: This is a read-only analysis task. Do NOT modify the instruction file, test
    artifacts, findings.json, or any other file in the worktree. Do NOT use the Write, Edit,
    NotebookEdit, or Skill tools. Do NOT invoke any skill (e.g., cat:grep-and-read-agent,
    or any other cat: skill).
    See ## Subagent Command Allowlist for permitted commands (grader/analyzer category applies here).
    Do NOT use find, ls, the Glob tool, or the Grep tool to discover or enumerate files.
    The ONLY files you may read or access (via cat, head, tail, grep, wc, sort, uniq, stat,
    or the Read tool) are:
    (1) the instruction file at {WORKTREE_ROOT}/{INSTRUCTION_TEXT_PATH} and (2) the test results file
    whose path is provided to you. Do NOT use any allowlisted command against any file not in this list.
    Do NOT read scenario `.md` files, test-results.json,
    protected-sections.txt, or any file under {TEST_DIR} other than the specific
    test results file provided. Do NOT use shell redirection operators (>, >>, <, <<, 2>) or any command
    that writes, moves, copies, or deletes files (rm, mv, cp, sed -i, tee, dd, truncate, install, patch,
    echo/printf with redirection, etc.).
```

The subagent returns the analysis commit SHA and the compact analysis report text. The main agent receives
only the compact analysis report text (~1KB). It does NOT read the analysis file.

**Display results to user:** Present the SPRT test summary and the analysis report text. Ask the user:
1. Are there any test cases to remove or replace based on the pattern analysis?
2. Would you like to improve the skill and re-run the test?
3. Are you satisfied with the current skill version?

**Iterate if needed:** If the user requests improvement, determine the targeted changes, then spawn
`cat:instruction-builder-implement` to write the updated instruction and commit it:

```
Task tool:
  description: "Write updated instruction: ${INSTRUCTION_TEXT_PATH}"
  subagent_type: "cat:instruction-builder-implement"
  prompt: |
    INSTRUCTION_TEXT_PATH: ${INSTRUCTION_TEXT_PATH}
    COMMIT_MESSAGE: feature: update instruction based on analysis [session: ${CLAUDE_SESSION_ID}]
    WORKTREE_PATH: ${CLAUDE_PROJECT_DIR}
    CONTENT: |
      <paste updated INSTRUCTION_DRAFT verbatim here>
```

Store the returned commit SHA as `INSTRUCTION_DRAFT_SHA` before returning to Step 6.

**Continuation shortcut — when returning from a re-run request:** If the conversation context states that
(a) the user already chose to improve the skill, (b) changes are already committed, and (c) a new
`INSTRUCTION_DRAFT_SHA` is provided — proceed directly to Step 6 (SPRT re-run) with that SHA. Do NOT ask
what skill to test, do NOT look for workflow state files, do NOT ask for clarification. All test cases must
be re-run because the instruction changed. Example trigger: "You have already applied targeted changes...
committed with new INSTRUCTION_DRAFT_SHA abc123. Continue with the workflow." → go straight to Step 6.

Cap at 5 test iterations total. Track the best-performing iteration by storing `BEST_SCORE` and `BEST_SHA`
(the commit SHA of the instruction file at that iteration). `BEST_SCORE` is the fraction of test cases that
reached SPRT Accept. After each iteration, compare the current score and update `BEST_SCORE` and `BEST_SHA`
if higher. Stop iterating if the absolute improvement between consecutive rounds is less than 5 percentage
points — restore the best skill version by running
`git checkout {BEST_SHA} -- {INSTRUCTION_TEXT_PATH}` and committing with message
`test: restore best iteration [session: ${CLAUDE_SESSION_ID}]`, then report "test plateau reached."
If the iteration cap is reached, apply the same rollback to `BEST_SHA` if the final iteration is not the
best, then stop and report "test iteration cap reached (5 rounds) — presenting best result."

**Re-run isolation:** Before returning to Step 6 after a fix, run the cleanup block from § "Cleanup
timing" (the "just before restarting" path). This removes the stale sanitized branch and any leftover
runner worktrees. Step 6 will then create a fresh sanitized branch from the updated HEAD before
dispatching waves. This is MANDATORY — a stale sanitized branch contains the old instruction and would
cause re-run agents to test the old (unfixed) version, producing invalid results.

**Output contract:** When iterating after user feedback:
- Apply improvements based on the analysis findings directly — do NOT ask how to improve the instruction
- Do NOT request confirmation before applying changes to the file
- Do NOT ask for approval before re-running SPRT tests
- Commit changes, delete old isolation branch, and proceed to Step 6 autonomously

### Step 10: Adversarial TDD Loop

If `CURIOSITY = low`, skip this step.

After the test phase converges, harden the instructions using alternating red-team and blue-team
subagents. Run until convergence (no CRITICAL/HIGH loopholes remain).

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

**Instruction-builder-specific configuration:**

| Parameter | Value |
|-----------|-------|
| `target_type` | `instructions` |
| `TARGET_FILE_PATH` | `{INSTRUCTION_FILE_PATH}` (the instruction file being hardened) |
| `CURRENT_CONTENT` | Pass `TARGET_FILE_PATH` — subagents read the file from disk themselves. Do NOT embed file content inline in subagent prompts. |

> **Invocation variants — target_type:** The default `instructions` can be replaced to match
> the content being hardened:
>
> | `target_type`       | Content being hardened          | `TARGET_FILE_PATH` points to |
> |---------------------|---------------------------------|------------------------------|
> | `instructions`| Skill or agent Markdown file    | SKILL.md or agent .md file   |
> | `test_code`         | Test source file                | *Test.java or *Test.sh       |
> | `source_code`       | Implementation source file      | *.java, *.sh, etc.           |

After hardening converges, present the hardening changes to the user for review before proceeding to
compression.

### Step 11: In-Place Hardening Mode (Optional)

**BLOCKING — Do NOT implement this loop manually.** Reading this section does not authorize direct
execution of the hardening algorithm. You are NOT the hardening engine — you are the orchestrator.

The ONLY valid execution path is:
- Spawn red-team and blue-team subagents using the **Task tool** as defined in Step 10
- Let the subagents read the target file from `INSTRUCTION_FILE_PATH` on disk, execute the loop, and commit changes

**Prohibited paths (will be treated as a protocol violation):**
- Manually performing any part of the hardening loop yourself — including red-team analysis, blue-team
  patching, arbitration, or diff validation — without a Task tool subagent
- Delegating to `cat:work-execute` — this is an implementation subagent, not a hardening subagent
- Delegating to any non-Task-tool path
- Announcing "executing instruction-builder in-place hardening mode" and then doing it yourself

If you are reading this and thinking "I should now run the loop", stop — you are primed incorrectly.
Return to Step 10 and spawn Task tool subagents.

If `CURIOSITY = low`, skip in-place hardening entirely and report "Skipping in-place hardening (curiosity=low)."
to the user.

In-place hardening mode runs the adversarial TDD loop against an instruction file in a worktree in a single
session, producing one commit per round as the loop progresses.

**Primary workflow — single instruction file:**

In-place hardening mode activates when the caller passes a single instruction file path inside the current
worktree. This mode applies adversarial instruction review only and does NOT run the test evaluation loop
(Step 6). Before entering in-place mode, verify that a prior test exists for this skill by checking whether
`${TEST_DIR}/test-results.json` exists. If no prior test is found, abort in-place mode and fall back to the
full workflow (Steps 1–10) with the message: "No prior test found for this skill — running full workflow
including test evaluation."

1. Store the file path as `INSTRUCTION_FILE_PATH`. Do NOT read the file into `CURRENT_INSTRUCTIONS` and relay
   it inline to subagents — subagents read the file from `INSTRUCTION_FILE_PATH` themselves. Determine
   the worktree root by running `git rev-parse --show-toplevel` from within the worktree; store as
   `WORKTREE_ROOT`. Pass `WORKTREE_ROOT` to all red-team and blue-team subagent prompts so they can
   construct absolute paths for **direct filesystem operations** (e.g., `cat {WORKTREE_ROOT}/findings.json`,
   `mkdir -p {WORKTREE_ROOT}/...`). For `git show` commands, subagents must use repo-relative paths
   (e.g., `git show <sha>:findings.json`) as specified in the shared adversarial protocol.
2. Run the full RED→BLUE loop as defined in Step 10 and the shared adversarial protocol. Each round
   produces commits from red-team (findings.json) and blue-team (patched instruction file). The loop
   continues until convergence (red-team returns `has_critical_high: false`).
3. No additional write step is needed — the blue-team commits the hardened content directly each round.

**Secondary workflow — directory / batch mode:**

If the caller passes a directory path (or `--batch <dir>`) instead of a single file, enumerate all `.md`
files under the directory recursively. Apply the single-skill workflow to each file.

By default, process files **sequentially** (safe for all worktrees). Between sequential skills, delete the
previous skill's `findings.json` (or `findings-<skill-name>.json` if using per-skill paths) before starting
the next skill to prevent stale disputes from contaminating subsequent red-team analysis. Parallel processing
is allowed when each instruction file is independent (no shared file-to-file dependencies). In parallel mode,
each subagent runs the full RED→BLUE loop for its own file, committing per-round — never touching other
instruction files. Each parallel subagent must use an instruction-specific findings path
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
retry protocol (exponential backoff with jitter, up to 3 retries) from Step 6 also applies to all
red-team and blue-team commits in batch parallel mode. Each parallel subagent must retry on ref-lock
contention using the same backoff schedule: first retry after 1-2 seconds (randomized), second after
2-4 seconds, third after 4-8 seconds.

Skip files that are not valid instruction files (missing Purpose or Procedure sections). If an instruction
file fails validation after blue-team patching, log the failure and continue to the next file.

After all instruction files are processed (or user types `abort`), display a batch summary table:

| File | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|-----------------|-----------------|-----------------|
| ...   | ...    | ...             | ...             | ...             |

### Step 12: Compression Phase

If `CURIOSITY = low`, skip this step.

After hardening achieves compliance (Step 10 converges and SPRT re-test accepts), compress the skill
file to minimize token cost while preserving behavioral compliance.

**Hardening + testing + compression are always run together** — never one without the others when
CURIOSITY != low.

**Sequential phases, never interleaved:**
1. Harden until compliant (only add text) — Step 10
2. Compress to minimize size (only remove text) — Step 12
3. Re-test to verify compression preserved compliance — Step 12 SPRT re-test
4. If compliance dropped, mark load-bearing text as protected and retry compression (up to 3 times)

#### Post-Hardening SPRT Re-Test

Reset `BEST_SCORE` and `BEST_SHA` before this step — hardening may have changed the skill, so
pre-hardening iteration tracking is stale and must not be used for rollback.

Before compressing, run a full SPRT test on the hardened skill to confirm compliance. Use the same
test cases from `${TEST_DIR}` (`.md` scenario files) and identical SPRT parameters as Step 6.
Commit test results with message `test: post-hardening SPRT [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `POST_HARDENING_SHA`. If any test case rejects, return to hardening (Step 10) to address the
failures before proceeding to compression.

#### Compress

Before invoking the compression subagent, derive `{instruction-filename}` as the basename of
`INSTRUCTION_TEXT_PATH` (e.g., `first-use` from `plugin/skills/my-skill/first-use.md`). Sanitize
`{instruction-filename}` by stripping any path separator characters (`/`, `\`, `..`), URL-encoded
separators (`%2F`, `%5C`), and null bytes (`%00`, `\0`) — reject and abort if the basename contains
path traversal sequences or any non-alphanumeric characters other than hyphen (`-`), underscore (`_`),
and period (`.`). The resulting filename must be a simple name with no directory components. Validate
that the final resolved path starts with `${TEST_DIR}/` before writing.

Invoke a general-purpose subagent to compress the instruction file:

```
Task tool:
  description: "Compress instruction: [instruction name]"
  subagent_type: "general-purpose"
  prompt: |
    Compress the instruction file at {INSTRUCTION_TEXT_PATH} following the compression protocol below.

    ## Compression Protocol
    Read and follow: ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/compression-protocol.md

    {IF_RETRY: ## Protected Sections
    The following sections are load-bearing and must NOT be removed, rephrased, or merged:
    Read constraint file: {PROTECTED_SECTIONS_PATH}
    All text listed there is mandatory preservation — treat it as decision-affecting requirements.}

    ## Output
    Write the compressed file to: {TEST_DIR}/compressed-{instruction-filename}.md

    RESTRICTION: The ONLY files you may read (via cat, head, tail, grep, wc, sort, uniq, stat,
    the Read tool, or any other mechanism) are: (1) the instruction file at {INSTRUCTION_TEXT_PATH} (this is the
    input to compress), (2) {TEST_DIR}/protected-sections.txt (if provided), and
    (3) ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/compression-protocol.md.
    Do NOT use `diff` or any command not on the grader/analyzer allowlist.
    Do NOT use any allowlisted command (including sort, uniq, stat) against any file not in
    this list — doing so constitutes a file read even if the command is nominally "read-only".
    Do NOT read any other file — including scenario `.md` files, test-results.json, findings.json, config
    files, peer subagent output files, or any file not listed in (1)-(3) regardless of its location.
    Do NOT list or explore the skill directory's test/ subdirectory. Do NOT use the Glob or Grep tool
    to discover or enumerate files. Do NOT use grep with recursive flags (-r, -R, --include, -l combined
    with directory paths) as this provides directory discovery. Do NOT modify {INSTRUCTION_TEXT_PATH}. Use
    the Write tool to write the compressed output to
    {TEST_DIR}/compressed-{instruction-filename}.md — this is the ONLY file you may write
    and the Write tool is the ONLY permitted mechanism for writing it. Do NOT use the Edit tool,
    NotebookEdit tool, or the Skill tool. Do NOT invoke any skill (e.g., cat:batch-write-agent,
    cat:grep-and-read-agent, or any other cat: skill).
    See ## Subagent Command Allowlist for permitted commands (grader/analyzer category applies here).
    Do NOT use shell redirection operators (>, >>, <, <<, 2>) or any command that writes, moves,
    copies, or deletes files.
```

Commit the compressed file with message `refactor: compress instruction [session: ${CLAUDE_SESSION_ID}]`.
Store SHA as `COMPRESSED_SHA`.

#### Semantic Pre-Check (Fast Gate)

Before running the full SPRT re-test, run a semantic pre-check using the comparison algorithm from
`${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/validation-protocol.md` (Section 2):

1. Extract semantic units from the original hardened skill (using Section 1 extraction algorithm)
2. Extract semantic units from the compressed skill
3. Compare units using the Section 2 comparison algorithm
4. If any LOST unit has severity HIGH (PROHIBITION, REQUIREMENT, CONDITIONAL, EXCLUSION) → skip SPRT and
   retry compression immediately (mark the lost unit's text as protected)
5. If semantically EQUIVALENT or only MEDIUM/LOW losses → proceed to SPRT re-test

#### Post-Compression SPRT Re-Test

Run SPRT re-test on the compressed version using identical test cases and parameters as the post-hardening
re-test.

**Acceptance criterion:** ALL test cases must reach SPRT Accept (log_ratio ≥ A).

**On rejection:** Compression broke compliance. Identify protected text:
1. Run `git diff {POST_HARDENING_SHA}..{COMPRESSED_SHA} -- {INSTRUCTION_TEXT_PATH}` to find removed/changed hunks
2. Identify which test case(s) triggered SPRT rejection and which assertions failed
3. Cross-reference failed assertions with their source semantic units (identified by the scenario `.md` file stem)
4. Cross-reference source semantic units with diff hunks via the `location` field in the extracted unit
5. Mark the corresponding hunks as protected in `${TEST_DIR}/protected-sections.txt`:

```
## Protected Section 1
Reason: Compression removed this text and SPRT compliance dropped from ACCEPT to REJECT
Original location: lines 45-52
Text:
> [verbatim text that was removed]
```

Commit protected-sections.txt with message
`test: mark protected sections [session: ${CLAUDE_SESSION_ID}]`.

**Retry strategy (cap at 3 attempts):**
- Retry 1: Compress with protected sections from the first failure
- Retry 2: If still failing, diff again to find additional load-bearing text, add to protected sections
- Retry 3: Final attempt with all accumulated protected sections
- After 3 failures: Accept the post-hardening (uncompressed) version as final; report "Compression could
  not preserve compliance after 3 retries — accepting uncompressed version."

**On acceptance:** Copy compressed file to `INSTRUCTION_TEXT_PATH` (overwrite original), commit with message
`refactor: accept final compression [session: ${CLAUDE_SESSION_ID}]`. Do NOT commit the post-compression
SPRT results separately — they are intermediate verification artifacts only. The accepted compressed
instruction file is the permanent output; the SPRT run that verified it is transient.

Present the final size reduction and compliance metrics to the user.

---

### Step 13: Cross-File Reorganization

If `CURIOSITY = low`, skip this step.

After compression, check whether content is routed to the correct file across all companion files in the
same skill directory. This step detects and corrects two classes of misrouted content:

- **Overloaded main file**: content in `first-use.md` that is only needed conditionally (e.g., only when a
  specific execution branch runs) should live in a companion file loaded on demand
- **Misrouted companion content**: content in a companion file (e.g., `compression-protocol.md`,
  `validation-protocol.md`) that is always needed and is not shared across skills belongs in `first-use.md`

**Scope**: Only files in the same skill directory (e.g., `plugin/skills/my-skill/`). Do not cross skill
boundaries.

**Architectural hard gate**: Before any reorganization, verify:
- No file contains `**INTERNAL DOCUMENT**` or an explicit audience restriction
- No file contains language like "X is intentionally not in this file"
- Files are not an orchestrator + subagent pair where one invokes the other
- Files are not loaded by different conditional branches with incompatible audiences

If any gate check triggers: skip reorganization for the affected pair and document the finding.

#### Procedure

**Phase 0 — Classify each file:**

For each `.md` file in the skill directory:
1. Identify its loading pattern: always-loaded (main entry point) vs. conditionally-loaded (referenced by
   directives, loaded on demand via `read` calls within steps)
2. Identify its audience: caller-facing (instructions for the invoking agent) vs. subagent-facing
   (instructions for a spawned subagent)

**Phase 1 — Extract semantic units from all files:**

Extract all semantic units from each file, tagging each with:
- **Category**: REQUIREMENT, PROHIBITION, SEQUENCE, CONDITIONAL, CONSEQUENCE, DEPENDENCY, MAPPING, RULE,
  PRINCIPLE, REFERENCE, REASON, FORMAT, PROCEDURE, EXAMPLE-CORRECT, EXAMPLE-WRONG
- **File-of-origin** and section heading path
- **Loading context**: always-loaded or conditionally-loaded
- **Cross-references**: other units this unit depends on or contradicts

**Phase 2 — Detect misrouted content:**

For each semantic unit, apply the routing rule:
- If a unit is in an always-loaded file but is only exercised when a specific conditional branch runs →
  candidate for moving to a conditionally-loaded companion file
- If a unit is in a conditionally-loaded companion file AND is required on every execution path AND is not
  shared across other skills → candidate for moving back to `first-use.md`
- Cross-file references are architectural boundary markers — do NOT inline or remove them

Apply targeted moves: relocate only the misrouted units. Do not restructure content that is already
correctly placed.

**Phase 3 — Binary equivalence verification:**

After each move, verify the full set of semantic units across all files is EQUIVALENT to the original set:
- All units present (none lost)
- All cross-references updated to reflect new locations
- No unit duplicated across files

If NOT_EQUIVALENT: revert the move. Document the failure mode and leave the content in its original file.

#### Loop-Back Rule

After applying any reorganization:
- If `first-use.md` was modified → **jump back to Step 12** (Compression Phase) to recompress with the
  updated content
- If only a companion file was modified → present the changes to the user; no loop-back required (companion
  files are not subject to the compression pipeline)
- If no moves were made → proceed to Output Format

---

## Output Format

**Final instruction output includes:**

1. **Instruction file** — The complete designed, tested, hardened, and compressed instruction document
2. **Frontmatter** — YAML with name, description (trigger-oriented), and optional argument-hint
3. **Purpose section** — The goal statement from the backward chaining methodology
4. **Procedure section** — Forward-execution steps calling extracted functions
5. **Verification section** — How to confirm the skill works correctly
6. **Optional sections** — Prerequisites, Functions, Examples (as needed based on skill complexity)

**Heading format (MANDATORY):** All section headings in the instruction file MUST use markdown `##` syntax,
not bold labels. The required headings are exactly:

```markdown
## Purpose

## Procedure

## Verification
```

Do NOT substitute `**Purpose:**`, `**Purpose Section:**`, `### Purpose`, or any other format for these headings.

**Step numbering format (MANDATORY):** All steps in the Procedure section MUST use `Step N` numbering
(e.g., `Step 1`, `Step 2`, `Step 3`), not plain numbered lists (`1.`, `2.`). The step label appears at
the start of the step heading or as a bold prefix, e.g., `**Step 1:**` or `### Step 1: Title`.

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

The description's sole purpose is skill-loading: it tells the agent whether to invoke this skill for
the current user request. The skill name and description are the **only** pieces of information the
agent uses to decide whether to load a skill. Include only what is relevant to that decision — trigger
conditions, user synonyms, and disambiguation from similar skills. Never include information irrelevant
to this decision: post-invocation behavior, agent instructions, trust levels, internal architecture, or
anything the agent only needs after the skill is already loaded.

**Diagnosing skill-loading failures in SPRT:** When a test-run subagent produces wrong behavior
(hallucinates step names, invents procedures, or responds without skill-specific knowledge), use
`cat:get-history-agent` to read the subagent's conversation and diagnose the root cause:

1. **Check if the Skill tool was invoked.** Use `session-analyzer file-history` on the subagent to
   list all tool operations. If no Skill invocation appears, the agent decided not to load the skill.
   To understand why, search for the agent's reasoning: `session-analyzer search
   "${CLAUDE_SESSION_ID}/subagents/agent-${ID}" "<thinking>" --context 10`. The thinking blocks
   reveal how the agent evaluated the available skills and why it decided none matched the prompt.
   The root cause is the skill's `description` frontmatter — it does not contain trigger words that
   match the test scenario's prompt. Fix the description to cover the prompt's vocabulary.
   Note: thinking blocks injected via `additionalContext` are not stored in JSONL logs and will not
   appear in session-analyzer searches — this is a known limitation of the diagnostic approach.
2. **Check which version loaded.** If the Skill tool WAS invoked, the agent loaded the skill from the
   **plugin cache** — not from the worktree. The cached version may have different step numbers,
   procedures, or constraints than the worktree version being tested. This is a known limitation:
   SPRT test-run subagents load skills from the installed plugin cache, so test assertions must match
   the cached version's step numbers and routing, not the worktree's modified version.
3. **Negative test failures.** If a negative test case unexpectedly triggers the skill (the agent
   loads it when it should not), the description is too broad — narrow it to exclude the out-of-scope
   prompt's vocabulary.

---

## Related Concepts

- **subagent-context-minimization**: When to delegate to subagents and how to pass references instead of
  content — `plugin/concepts/subagent-context-minimization.md`
- **instruction-analyzer-agent**: Detects delegation opportunities and content relay anti-patterns in skill
  procedures — `plugin/agents/instruction-analyzer-agent.md`
- **Cross-file reorganization**: The four-phase classify-extract-reconstruct-verify pipeline used in Step 13
  — see **Step 13: Cross-File Reorganization** above
- **Behavioral test cases**: SPRT calibration test cases for this skill are stored in
  `plugin/tests/skills/instruction-builder-agent/first-use/` (one `.md` file per test case). Test results
  are stored in `plugin/tests/skills/instruction-builder-agent/first-use/test-results.json`
  (skill_hash, model, session_id, timestamp, overall_decision, and per-case SPRT data).

## Verification

- [ ] Curiosity gate verified: when `CURIOSITY = low`, Steps 5–12 skipped entirely; corresponding
  checklist items marked N/A (not applicable)

**Curiosity gate note:** When a step was skipped because `CURIOSITY = low` (Steps 5–12), mark the
corresponding checklist items as **N/A** (not applicable), not failed.
Overall verification passes if all non-skipped items are checked and all skipped items are marked N/A.

### Design phase

- [ ] Design subagent returned a complete draft with non-empty Purpose, Procedure, and Verification sections
- [ ] Compact-output pass applied before writing draft to disk; correctness exemptions respected
- [ ] Instruction draft written and committed; `INSTRUCTION_DRAFT_SHA` stored

### Description validation

- [ ] New Step 4 rejects descriptions > 250 characters with hard reject (displays char count)
- [ ] AskUserQuestion presented with current description and character count
- [ ] INSTRUCTION_DRAFT updated with user-provided replacement before writing to disk
- [ ] Descriptions of exactly 250 characters are accepted without prompting

### Test generation

- [ ] Semantic units extracted inline using Nine-Category algorithm; non-testable units (REFERENCE, CONJUNCTION) skipped
- [ ] Each testable unit has a scenario `.md` file with YAML frontmatter `category` field
- [ ] Assertions are plain-text semantic descriptions of compliance (not embedded code/scripts) and action-based
  (tool invocations, file writes, Bash commands) — not verbal knowledge-recall
- [ ] Prompts use production-sequence format — not Q&A knowledge-recall format
- [ ] For skills: assert #1 is "The Skill tool was invoked"; for non-skill files: assert #1 is primary compliance behavior
- [ ] At least 3 negative scenario files generated; no scenario includes system_reminders listing skills
- [ ] Test cases presented to user for approval before SPRT begins
- [ ] `TEST_DIR` derived correctly from `INSTRUCTION_TEXT_PATH` (e.g., `plugin/skills/foo/first-use.md` →
  `plugin/tests/skills/foo/first-use/`)
- [ ] Scenario `.md` files committed to `${TEST_DIR}/` with `test:` prefix; commit SHA stored as `TEST_SET_SHA`

### SPRT execution

- [ ] SPRT parameters: p0=0.95, p1=0.85, α=0.05, β=0.05, A≈2.944, B≈−2.944
- [ ] Each test case runs its own independent SPRT; rejection of any case stops all remaining cases (early-stop)
- [ ] SPRT decisions made pipelined (after each test-run completion), not batched per wave; log_ratio updated
  immediately after each run grading before dispatching the next run
- [ ] Each test run uses a fresh non-resumed `TEST_MODEL` subagent (no `resume` or `conversation_id` fields)
- [ ] `TEST_MODEL` read from skill frontmatter via `extract-model`; never hardcoded
- [ ] Each assertion graded by a separate `TEST_MODEL` grader subagent (no inline grading)
- [ ] Run outputs written to temp files only; `test-results.json` committed once after SPRT completes with Accept or Reject
- [ ] SPRT waves continued until Accept boundary (log_ratio ≥ 2.944) formally crossed — Inconclusive is never a final state
- [ ] Test results show meaningful signal: SPRT log_ratios demonstrate non-trivial discrimination (not all cases
  pass/reject trivially)
- [ ] Result Inspection Checklist (4 checks) performed before updating SPRT log_ratio
- [ ] Re-test after hardening uses identical SPRT parameters (p0, p1, α, β) and same test case set (`TEST_SET_SHA`)
- [ ] Token usage summary displayed after SPRT; `test-results.json` includes per-case and aggregate totals
- [ ] `TEST_DIR`, `CLAUDE_SESSION_ID`, `TEST_MODEL` passed as resolved literal strings to all subagents

### Failure investigation

- [ ] Investigation runs automatically on SPRT reject; sub-steps 1–7 automated, sub-step 8 synthesized
- [ ] Design-flaw entry point: when `design_flaw=true`, sub-steps 1–7 skipped; routes to sub-step 8 directly
- [ ] Conclusion is one of: Assertion design flaw / Test environment artifact / Genuine skill defect / Inconclusive
- [ ] Routing correct: design flaw → fix assertion; artifact → rerun; defect/inconclusive → Step 9
- [ ] Protected text identification cross-references SPRT failure data with diff hunks to prevent hardening from
  weakening verification assertions

### Failure analysis

- [ ] instruction-analyzer-agent spawned correctly with `TEST_SHA`, `TEST_PATH`, `INSTRUCTION_TEXT_PATH`,
  `WORKTREE_ROOT`, `TEST_DIR`, and `CLAUDE_SESSION_ID`
- [ ] Analysis report presented to user along with SPRT test summary
- [ ] User offered 3 choices: remove/replace test cases, improve skill and re-run, or accept current version
- [ ] Iteration cap of 5 respected; best iteration tracked via `BEST_SCORE` and `BEST_SHA`
- [ ] Plateau detection: iteration stops when improvement < 5 percentage points; best version restored

### Adversarial hardening

- [ ] Follows shared adversarial protocol from `plugin/concepts/adversarial-protocol.md`; `target_type: instructions`
- [ ] Main agent never reads findings.json directly — uses structured JSON returns from subagents
- [ ] Subagents read `TARGET_FILE_PATH` from disk; file content not embedded inline in prompts
- [ ] In-place mode: verifies prior `test-results.json` before skipping Steps 1–6; per-round commits
- [ ] In-place mode: precondition check passes (`${TEST_DIR}/test-results.json` exists); falls back to full
  workflow with explanatory message when absent
- [ ] Batch mode: skill-name derived as `<directory-name>-<file-stem>`; per-skill `FINDINGS_PATH`; findings.json
  deleted between sequential skills
- [ ] Batch mode: batch summary table displayed after all files processed (File, Rounds, Loopholes Closed,
  Disputes Upheld, Patches Applied)

### Compression

- [ ] Compression never interleaved with hardening — harden first (Step 10), then compress (Step 12)
- [ ] Semantic pre-check gates compression before full SPRT re-test
- [ ] Retries capped at 3; uncompressed version accepted after 3 failures
- [ ] Post-compression acceptance criteria identical to post-hardening SPRT parameters
- [ ] Compression subagent restricted to reading instruction file, protected-sections.txt, and compression-protocol.md only

### Cross-File Reorganization

- [ ] All companion files in the skill directory classified by loading pattern and audience before any moves
- [ ] Architectural hard gate applied before each move (no internal documents, no orchestrator+subagent pairs)
- [ ] Binary equivalence verified after each move: no semantic units lost, all cross-references updated
- [ ] Loop-back to Step 12 triggered when `first-use.md` is modified by reorganization
- [ ] No content moved across skill boundaries