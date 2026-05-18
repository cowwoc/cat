<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Agent-Mistake Investigation Guide

Loaded conditionally by phase-investigate.md when the mistake involves an agent.

## Check the Delegation Prompt

The delegation prompt IS the primary "document" the agent received. Check it for:
- Expected values embedded in output format (e.g., "score: 1.0 (required)")
- Outcome requirements that conflict with reality (e.g., "MUST be 1.0")
- Any content telling the agent what to report vs what to measure

## Check for Technically Impossible Instructions

When an agent fails to follow instructions, check whether the instructions were **technically possible** given the
active runtime's agent architecture.

Do not infer impossibility from behavior outside the active agent context. Verify the active agent's available
tools, capability model, and actual tool errors before classifying an instruction as impossible.

**If instructions required unavailable capabilities:**

```yaml
technically_impossible_check:
 instruction_required: "Spawn reviewer agents for each finding"
 runtime: "{active runtime}"
 capability_needed: "{specific tool or capability}"
 available_to_subagent: false
 conclusion: >
  IMPOSSIBLE in this runtime because the required capability is not available to the agent.
 root_cause: "architectural_flaw"
 fix_type: >
  Redesign the workflow so the unavailable capability is performed by an agent or process that has it.
```

**Common patterns of impossible instructions:**

| Instruction Pattern | Why Impossible | Correct Design |
|--------------------|----------------|----------------|
| "Agent must invoke /cat:skill" | Wrong only if the skill name is invalid or missing | Use Skill tool directly with the correct skill name |
| "Spawn reviewer agents" | Only impossible when the active runtime does not expose agent spawning to that agent | Move spawning to an agent/process that has the capability, or keep nested spawning if the runtime supports it |
| "Delegate to nested agent" | Only impossible when the active runtime enforces a delegation-depth limit that blocks it | Flatten to a supported depth, or keep the nested design if the runtime supports it |
| "Use parallel-execute skill" | Wrong only if the skill is unavailable in this environment | Use Skill tool if available, otherwise use equivalent direct tool calls |

**When this check identifies impossible instructions:**

1. Root cause is `architectural_flaw` (not agent error)
2. Prevention must redesign the WORKFLOW, not add guidance
3. The skill/workflow documentation is the source of the bug
4. Do NOT add "agent should have..." instructions - they cannot help

## Check for Missing Skill Preloading

When an agent fails to follow skill-based guidance correctly, check whether the agent would have benefited from
having skills preloaded via frontmatter.

**Runtime-specific skill preloading:**

Runtime agents can specify skills to preload. Keep adapter-specific details in runtime-specific agent files and keep
shared behavior in shared agent bodies.

```yaml
---
name: work-merge
description: Merge phase for /cat:work
tools: Read, Bash, Grep, Glob
model: haiku
skills:
  - git-squash
  - git-rebase
  - git-merge-linear
---
```

**Questions to ask when agent makes a mistake:**

| Question | If YES |
|----------|--------|
| Did agent need skill knowledge it didn't have? | Add it to the runtime-specific agent definition |
| Was `general-purpose` agent used for domain-specific work? | Create dedicated agent type |
| Did agent try to invoke a skill (and fail)? | Move the needed skill knowledge to the agent definition |
| Would preloaded guidance have prevented the mistake? | Add the guidance to the runtime-specific agent definition |

**If general-purpose agent was used and skills would help:**

```yaml
subagent_skills_analysis:
 subagent_type_used: "general-purpose"
 domain_knowledge_needed: ["git-squash", "git-rebase"]
 skill_invocation_attempted: true
 skill_invocation_succeeded: false # Skill name invalid/missing, or invocation omitted

 recommendation:
 action: "Invoke the required skill via Skill tool before proceeding"
 skills_to_load: ["skill-1", "skill-2"]
 rationale: "Agent can load skill instructions directly via Skill tool"
```

**Prevention pattern for skill preloading issues:**

1. Identify the skills the agent needed
2. Check if a dedicated agent type already exists (check `plugin/agents/<runtime>/` and shared `plugin/agents/common/`)
3. If yes: Use that agent type instead of `general-purpose`
4. If no: Create a shared body in `plugin/agents/common/{name}.md` and a runtime wrapper in `plugin/agents/<runtime>/`
5. Update the delegation code to use the new agent type

**Record in mistake entry:**

```json
{
  "category": "architectural_flaw",
  "root_cause": "Agent lacked skill knowledge; general-purpose agent used for domain work",
  "prevention_type": "config",
  "prevention_path": "plugin/agents/common/{new-agent}.md",
  "subagent_skills_needed": ["skill-1", "skill-2"]
}
```
