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

## Test Case Structure

Each instruction-test config maps a test name to a `messages` array:

```json
{
  "target_description": "<hypothesis: what behavior this test verifies>",
  "system_reminders": [],
  "system_prompt": "<skill description injected as it appears at runtime — see Skill Availability below>",
  "configs": {
    "positive_case_name": {
      "messages": [
        {
          "prompt": "<realistic work prompt>",
          "success_criteria": {
            "must_use_tools": ["Skill"],
            "_metadata": {
              "uses_tool:Skill": {
                "description": "Tier 1 — agent chose to invoke the skill",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    }
  }
}
```

**Key rules:**

- `system_reminders` must be an empty array (`[]`) — never list available skills here.
- `system_prompt` injects the skill description as it would appear at runtime (see below).
- `target_description` must state a falsifiable hypothesis: what behavior the test proves or disproves.
- Each test config is a single scenario; use separate configs rather than one config with many messages.

## Skill Availability in Tests

Skills with `user-invocable: false` are only available to agents that explicitly list them in their `skills:`
frontmatter. When the empirical test runner launches an isolated claude session, these skills are not automatically
available.

To replicate real runtime conditions, inject the skill's `description` field into the `system_prompt`:

```json
"system_prompt": "You have access to the following skill:\n- cat:grep-and-read-agent: PREFER when searching pattern AND reading matches - single operation (50-70% faster than sequential)\n\nInvoke skills using the Skill tool: Skill(skill=\"cat:grep-and-read-agent\", args=\"<cat_agent_id>\")"
```

This is NOT priming — it replicates how the skill description appears in the agent's context at runtime. The test
still measures organic routing: the agent must recognize the task matches the description and choose to invoke it.

Skills with `user-invocable: true` do not require this step; they are surfaced automatically by the plugin.

## Two-Tier Verification

Every positive test case must verify two things:

### Tier 1 — Skill Chosen

The agent invoked the Skill tool with the target skill name. This confirms the routing decision was correct.

```json
"must_use_tools": ["Skill"]
```

### Tier 2 — Skill Followed Correctly

The agent executed the skill's procedure as documented. The Tier 2 criterion depends on the skill's documented
procedure. Read `first-use.md` before writing Tier 2 criteria.

**Pattern: skill replaces multiple sequential tool calls**

For skills that consolidate multiple tool calls (e.g., `grep-and-read-agent`), Tier 1 alone is sufficient when
verifying Tier 2 would require tracking tools invoked inside the skill. The Skill tool invocation IS the batch
operation — the internal Grep and Read calls run inside the skill, not as separate outer-agent calls.

**Pattern: tool-call sequence verification**

For skills where the procedure requires a specific external tool call sequence visible to the outer agent, add
`must_use_tools` or `must_not_use_tools` criteria. Example: if the skill must call Bash for a git operation
before committing, add `"must_use_tools": ["Skill", "Bash"]`.

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

Negative case format:

```json
"negative_search_only": {
  "messages": [
    {
      "prompt": "List all Java files that reference HookHandler — just the paths, not their contents.",
      "success_criteria": {
        "must_not_use_tools": ["Skill"],
        "_metadata": {
          "not_uses_tool:Skill": {
            "description": "Search-only task — skill must not be invoked",
            "severity": "HIGH"
          }
        }
      }
    }
  ]
}
```

## Example Test Case File

Complete example for `grep-and-read-agent`:

```json
{
  "target_description": "Agent uses grep-and-read-agent when asked to search and read multiple files at unknown paths",
  "system_reminders": [],
  "system_prompt": "You have access to the following skill:\n- cat:grep-and-read-agent: PREFER when searching pattern AND reading matches - single operation (50-70% faster than sequential)\n\nInvoke skills using the Skill tool: Skill(skill=\"cat:grep-and-read-agent\", args=\"<cat_agent_id>\")",
  "configs": {
    "positive_find_implementations": {
      "messages": [
        {
          "prompt": "I need to understand how HookHandler is implemented across the codebase. Search for all Java files that contain HookHandler and read their contents so you can explain what each implementation does.",
          "success_criteria": {
            "must_use_tools": ["Skill"],
            "_metadata": {
              "uses_tool:Skill": {
                "description": "Tier 1 — agent invoked grep-and-read-agent to search and read multiple files in one operation",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    },
    "negative_search_only": {
      "messages": [
        {
          "prompt": "List all Java files that reference HookHandler — just the paths, not their contents.",
          "success_criteria": {
            "must_not_use_tools": ["Skill"],
            "_metadata": {
              "not_uses_tool:Skill": {
                "description": "Search-only — skill should not be invoked",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    }
  }
}
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
  --config plugin/skills/<skill-name>/instruction-test/test-cases.json \
  --trials 3 \
  --model haiku \
  --cwd .
```

Use `--trials 3` during development for fast iteration. Increase to `--trials 10` before accepting an instruction-test
as the canonical pass rate.
