<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Organic Instruction-Test Design Standard

Reference standard for writing empirical instruction-tests that verify a skill is chosen AND followed correctly, or a rule is applied correctly, without priming the agent to select the skill or apply the rule.

**Scope:** This standard applies to ALL SPRT tests, including:
- **Skill tests** (`plugin/tests/skills/`) — verify skill selection and execution
- **Rule tests** (`plugin/tests/rules/`) — verify behavioral rules are followed

## Rationale: Why No Priming

A "primed" test injects the target skill name into `system_reminders`, telling the agent which skill is available.
This inflates pass rates by removing the routing decision from the test — the agent knows what to use before reading
the prompt. A primed instruction-test measures only *whether the agent follows the skill procedure*, not *whether the
agent recognizes when to use the skill*.

An **organic** instruction-test keeps `system_reminders` empty. The agent must recognize the skill trigger from the
SKILL.md `description` field, then choose the skill based on that description alone. This measures realistic behavior:
an agent working on a real task with no hand-holding.

## Rule Tests vs Skill Tests

**Skill tests** verify two behaviors:
1. **Tier 1:** The agent invokes the Skill tool (routing decision)
2. **Tier 2:** The agent follows the skill's procedure (execution correctness)

**Rule tests** verify one behavior:
- The agent follows the rule during normal work (no Skill tool involved)

**Organic principle for rule tests:**

Do NOT prescribe the mechanism the rule defines. Describe a realistic task where applying the rule is the natural solution.

**Guideline for designing organic test prompts:**

1. **Identify the high-level goal** of the file/rule you're testing, or the set of actions you want to trigger
2. **Figure out what task requires those actions** without explicitly asking for them
3. **For tee-piped-output example:** The rule's purpose is to avoid re-running commands when examining different aspects of output (command is expensive or can only run once)
4. **Ask:** What organic scenario requires capturing output once and filtering it multiple times?
5. **Example scenario:** Run an expensive command, search for an error, and if found, retrieve context around it

This naturally triggers tee-piped-output because:
- Can't re-run the command (expensive)
- Need multiple filters (error search → conditional context retrieval)
- Solution requires capturing output once, filtering multiple times

**Example — tee-piped-output cleanup rule:**

❌ **Prescriptive (not organic):**
```
Help me run find, capture output to a temporary log file, filter it, 
and clean up the temporary log file afterwards.
```
*Explicitly names the mechanism: temp file, capture, cleanup*

✅ **Organic:**
```
Run `mvn verify` (it takes 5 minutes), search the output for test failures, 
and if you find any, show me the context around them.
```
*Expensive command (can't re-run) + conditional filtering (search → context retrieval) organically requires capturing output once and filtering multiple times, without prescribing tee or cleanup*

The assertions then verify the rule was followed (tee used, cleanup included) even though the prompt didn't prescribe it.

## Test Runner Prompt Isolation

Test runners that dispatch SPRT (Sequential Probability Ratio Test) trials must produce clean delegation prompts
containing ONLY the test case file content and minimal working-directory scaffolding. Test runner prompts must NOT
contain explicit behavioral expectations, expected output descriptions, assessment criteria, or hints about what the
test measures.

**Priming patterns to avoid in test runner prompts:**

- **Algorithm exposure:** "You must implement using algorithm X" → Agent executes the prescribed
  approach instead of discovering it
- **Output format priming:** "The output should contain fields A, B, C (value: 1.0)" → Agent
  fabricates expected outputs
- **Behavioral expectations:** "Handle failures gracefully by recording the error" → Agent
  performs scripted behavior instead of following skill instructions
- **Assessment criteria:** "Key behaviors: (a) ..., (b) ..." → Agent learns test objectives
  and executes test-aware responses
- **CRITICAL enforcement:** "CRITICAL: You must record this finding explicitly" → Agent
  anchors on enforcement language instead of instruction content
- **Cost/efficiency language:** "This approach spawns 2 subagents (expensive)" → Agent takes
  shortcuts to minimize cost instead of following procedure

Test runner prompts must deliver ONLY: (1) the Turn file content (exactly as written in the test case),
(2) optional working-directory setup (git init, initial files), and (3) no additional context describing expected
behaviors or test objectives.

**Consequence of prompt priming:** All test runs with primed prompts are invalid and must be re-run with clean prompts.
A "primed SPRT" measures agent compliance with the prompt, not agent compliance with the skill instructions. The test
result is corrupted and cannot be used to evaluate instruction quality.

## Test Case File Format

Each test case is a single `.md` file stored in the skill's test directory (`plugin/tests/{skill_name}/`).
The file stem is the test case ID used by `InstructionTestRunner` and SPRT tracking.

### Frontmatter

Every test case file begins with a YAML frontmatter block declaring the category:

```
---
category: <CATEGORY>
---
```

Valid categories:

| Category | Description |
|----------|-------------|
| `REQUIREMENT` | Verifies a behavior the instruction mandates |
| `PROHIBITION` | Verifies a behavior the instruction forbids |
| `CONDITIONAL` | Verifies a behavior that applies only under a specific condition |
| `SEQUENCE` | Verifies an ordering or sequencing constraint |
| `DEPENDENCY` | Verifies prerequisite or precondition enforcement |
| `negative` | Verifies the skill is NOT invoked for an out-of-scope prompt |

### Section Headings

After the frontmatter, every test case file contains exactly two sections:

- `## Turn 1` — the realistic user prompt sent to the agent under test
- `## Assertions` — a numbered plain-text list of behavioral assertions graded by the instruction-grader-agent

### Turn 1 Content Requirements

**MANDATORY: Test prompts must instruct the agent to do something.** Do not write prompts that merely state requirements or describe needs without asking the agent to take action.

**Invalid prompt patterns:**

```markdown
I need to run a command, capture output to a temp file, filter it, and clean up.
```

This is a **statement of requirements**, not an instruction. The agent has no way to know whether to:
- Help write the commands
- Demonstrate the pattern
- Just acknowledge the statement

**Valid prompt patterns:**

Ask the agent to help with a specific task:
```markdown
Help me run `git log --oneline`, capture the output to a temp file so I can analyze 
it later, filter it to show only commits with "fix", then clean up the temp file.
```

Ask the agent to demonstrate a pattern:
```markdown
Show me how to run a command with a pipe, capture the pre-pipe output to a temp 
file for later re-filtering, and clean up the temp file when done.
```

Both patterns make it clear what action the agent should take. Use imperative verbs ("help me", "show me", "explain", "analyze", "fix") or questions ("how do I", "what's the best way to") to signal intent.

### Assertion Syntax

Assertions are a numbered list of plain-text sentences. All assertions are semantic (no deterministic
patterns). For skill instruction files, assertion 1 is always `The Skill tool was invoked`. For negative
test cases, assertion 1 is always `The Skill tool was NOT invoked`.

**Key rules:**

- `system_reminders` must remain empty in test execution — never list available skills in the prompt.
- `system_prompt` injects the skill description at runtime (see Skill Availability below).
- The file stem serves as the test case ID; choose descriptive names (e.g., `unit_step44_guard.md`).
- One scenario per file; use separate files for positive and negative cases.

### Positive Test Case Template

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

<realistic user prompt that organically requires this skill — do not name or hint at the skill>

## Assertions

1. The Skill tool was invoked
2. <behavioral assertion describing expected skill behavior>
3. <additional behavioral assertion>
```

### Negative Test Case Template

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

## Skill Availability in Tests

Skills with `user-invocable: false` are only available to agents that explicitly list them in their `skills:`
frontmatter. When the empirical test runner launches an isolated claude session, these skills are not automatically
available.

To replicate real runtime conditions, inject the skill's `description` field into the test runner's `system_prompt`
configuration (set at the instruction-test level, not in individual `.md` test case files):

```
You have access to the following skill:
- cat:grep-and-read-agent: PREFER when searching pattern AND reading matches - single operation (50-70% faster
  than sequential)

Invoke skills using the Skill tool: Skill(skill="cat:grep-and-read-agent", args="<cat_agent_id>")
```

This is NOT priming — it replicates how the skill description appears in the agent's context at runtime. The test
still measures organic routing: the agent must recognize the task matches the description and choose to invoke it.

Skills with `user-invocable: true` do not require this step; they are surfaced automatically by the plugin.

## Two-Tier Verification

Every positive test case must verify two things:

### Tier 1 — Skill Chosen

The agent invoked the Skill tool with the target skill name. This confirms the routing decision was correct.

In a `.md` test case, Tier 1 is captured as assertion 1: `The Skill tool was invoked`.

### Tier 2 — Skill Followed Correctly

The agent executed the skill's procedure as documented. The Tier 2 criterion depends on the skill's documented
procedure. Read `first-use.md` before writing Tier 2 criteria.

**Pattern: skill replaces multiple sequential tool calls**

For skills that consolidate multiple tool calls (e.g., `grep-and-read-agent`), Tier 1 alone is sufficient when
verifying Tier 2 would require tracking tools invoked inside the skill. The Skill tool invocation IS the batch
operation — the internal Grep and Read calls run inside the skill, not as separate outer-agent calls.

**Pattern: tool-call sequence verification**

For skills where the procedure requires a specific external tool call sequence visible to the outer agent, add
assertions describing the expected tool invocations. Example: if the skill must call Bash for a git operation
before committing, add an assertion: `The agent invoked Bash for the git operation before calling the Skill tool.`

## Positive Case Design Rules

A positive case prompt must satisfy all of the following:

1. **Realistic work** — the prompt describes a real task an agent would encounter, not a meta-instruction ("use
   skill X" or "read skill X first"). The agent should arrive at the skill through natural problem-solving.

2. **Paths unknown** — the prompt must not provide exact file paths. If the agent knows the paths, it can use
   individual `Read` calls directly, bypassing the skill. Prompts should describe patterns or concepts, not
   locations.

3. **Multiple files** — the task must require reading 3 or more files. Skills that batch file operations have no
   benefit when only one or two files are involved; the agent may choose sequential reads as an equally valid
   approach.

4. **Organically triggers the skill's description** — the prompt wording should match the kind of task the SKILL.md
   `description` mentions. Read the `description` field and ask: "Would an agent reading this description choose the
   skill for my prompt?"

**Avoiding false positives:**

Do not write prompts where the agent might invoke the skill for a reason unrelated to the efficiency benefit. Verify
that the skill choice in the positive case is driven by the description's stated trigger condition.

## Negative Case Design Rules

Negative cases verify that the agent does NOT invoke the skill when the trigger conditions are not met. Three
canonical out-of-scope categories:

| Category | Condition | Example |
|----------|-----------|---------|
| Search-only | The task requires only file paths, not file contents | "List all files that reference X" |
| Single known file | The task names exactly one file to read | "Read plugin/foo/SKILL.md" |
| Two explicit paths | The task provides exactly two file paths | "Read file A and file B" |

Negative case format (file named e.g. `grep_and_read_agent_negative_1.md`):

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

List all Java files that reference HookHandler — just the paths, not their contents.

## Assertions

1. The Skill tool was NOT invoked
```

## Example Test Case Files

Complete examples for `grep-and-read-agent` stored in `plugin/tests/skills/grep-and-read-agent/`:

**`unit_grep_files_with_matches.md`** (positive case):

```
---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to understand how HookHandler is implemented across the codebase. Search for all Java files that
contain HookHandler and read their contents so you can explain what each implementation does.

## Assertions

1. The Skill tool was invoked
2. The agent searched for HookHandler using Grep with output_mode: files_with_matches before reading any files
3. The agent returned the full contents of each matched file, not just paths
```

**`grep_and_read_agent_negative_1.md`** (negative case):

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

List all Java files that reference HookHandler — just the paths, not their contents.

## Assertions

1. The Skill tool was NOT invoked
```

## Pass Threshold

Positive cases must achieve ≥80% pass rate across 10 trials. If a positive case falls below 80%, revise the prompt
to more precisely match the SKILL.md `description` trigger language.

Negative cases must achieve ≥80% pass rate across 10 trials. If a negative case fails frequently (agent keeps
invoking the skill when it should not), the skill's `description` may be over-broad; file a separate issue to tighten
the description.

## Running Instruction-Tests

```bash
RUNNER="${CLAUDE_PLUGIN_ROOT}/client/bin/empirical-test-runner"
"$RUNNER" \
  --test-dir plugin/tests/skills/<skill-name>/ \
  --trials 3 \
  --model haiku \
  --cwd .
```

Use `--trials 3` during development for fast iteration. Increase to `--trials 10` before accepting an instruction-test
as the canonical pass rate.
