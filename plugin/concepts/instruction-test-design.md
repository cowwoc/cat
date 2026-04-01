<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Organic Instruction-Test Design Standard

Reference standard for writing empirical instruction-tests that verify a skill is chosen AND followed correctly,
without priming the agent to select the skill.

## Rationale: Why No Priming

A "primed" test injects the target skill name into `system_reminders`, telling the agent which skill is available.
This inflates pass rates by removing the routing decision from the test — the agent knows what to use before reading
the prompt. A primed instruction-test measures only *whether the agent follows the skill procedure*, not *whether the
agent recognizes when to use the skill*.

An **organic** instruction-test keeps `system_reminders` empty. The agent must recognize the skill trigger from the
SKILL.md `description` field, then choose the skill based on that description alone. This measures realistic behavior:
an agent working on a real task with no hand-holding.

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
RUNNER="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/empirical-test-runner"
"$RUNNER" \
  --test-dir plugin/tests/skills/<skill-name>/ \
  --trials 3 \
  --model haiku \
  --cwd .
```

Use `--trials 3` during development for fast iteration. Increase to `--trials 10` before accepting an instruction-test
as the canonical pass rate.
