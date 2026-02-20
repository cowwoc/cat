<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Subagent Investigation Guide

Loaded conditionally by phase-investigate.md when the mistake involves a subagent.

## Check the Delegation Prompt

The delegation prompt IS the primary "document" the subagent received. Check it for:
- Expected values embedded in output format (e.g., "score: 1.0 (required)")
- Outcome requirements that conflict with reality (e.g., "MUST be 1.0")
- Any content telling the subagent what to report vs what to measure

## Check for Technically Impossible Instructions

When a subagent fails to follow instructions, check whether the instructions were **technically possible** given Claude
Code's subagent architecture:

| Subagent Capability | Available? | Evidence |
|---------------------|------------|----------|
| Spawn nested subagents (Task tool) | **NO** | Task tool not exposed to subagents |
| Invoke skills dynamically (Skill tool) | **NO** | Skill tool not available to subagents |
| Read/Write/Edit files | YES | Standard file tools available |
| Run bash commands | YES | Bash tool available |
| Web search/fetch | YES | Available to subagents |

**If instructions required unavailable capabilities:**

```yaml
technically_impossible_check:
  instruction_required: "Invoke /cat:{skill-name} for each item"
  capability_needed: "Skill tool"
  available_to_subagent: false
  conclusion: "IMPOSSIBLE - instruction cannot be executed as written"
  root_cause: "architectural_flaw"
  fix_type: "Redesign workflow to invoke skills at main agent level"
```

**Common patterns of impossible instructions:**

| Instruction Pattern | Why Impossible | Correct Design |
|--------------------|----------------|----------------|
| "Subagent must invoke /cat:skill" | Skill tool unavailable | Main agent invokes skill before/after delegation |
| "Spawn reviewer subagents" | Task tool unavailable | Main agent spawns reviewers directly |
| "Delegate to sub-subagent" | Max depth is 1 | Flatten to single delegation level |
| "Use parallel-execute skill" | Skill tool unavailable | Main agent handles parallelization |

**When this check identifies impossible instructions:**

1. Root cause is `architectural_flaw` (not agent error)
2. Prevention must redesign the WORKFLOW, not add guidance
3. The skill/workflow documentation is the source of the bug
4. Do NOT add "agent should have..." instructions - they cannot help

## Check for Missing Skill Preloading

When a subagent fails to follow skill-based guidance correctly, check whether the subagent would have benefited from
having skills preloaded via frontmatter.

**Claude Code `skills` frontmatter field:**

Agents defined in `plugin/agents/` can specify skills to preload:

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

The `skills` field causes Claude Code to inject the listed skill content into the subagent's context at startup - the
subagent receives the knowledge without needing to invoke the Skill tool.

**Questions to ask when subagent makes a mistake:**

| Question | If YES |
|----------|--------|
| Did subagent need skill knowledge it didn't have? | Consider adding skill to frontmatter |
| Was `general-purpose` subagent used for domain-specific work? | Create dedicated agent type |
| Did subagent try to invoke a skill (and fail)? | Move skill knowledge to frontmatter |
| Would preloaded guidance have prevented the mistake? | Add skill to agent's `skills` field |

**If general-purpose agent was used and skills would help:**

```yaml
subagent_skills_analysis:
  subagent_type_used: "general-purpose"
  domain_knowledge_needed: ["git-squash", "git-rebase"]
  skill_invocation_attempted: true
  skill_invocation_succeeded: false  # Skill tool not available to subagents

  recommendation:
    action: "Create dedicated agent type"
    agent_name: "{domain}-agent"
    skills_to_preload: ["skill-1", "skill-2"]
    rationale: "Subagent needs domain knowledge but cannot invoke skills"
```

**Prevention pattern for skill preloading issues:**

1. Identify the skills the subagent needed
2. Check if a dedicated agent type already exists (check `plugin/agents/`)
3. If yes: Use that agent type instead of `general-purpose`
4. If no: Create new agent in `plugin/agents/{name}.md` with `skills` frontmatter
5. Update the delegation code to use the new agent type

**Record in mistake entry:**

```json
{
  "category": "architectural_flaw",
  "root_cause": "Subagent lacked skill knowledge; general-purpose agent used for domain work",
  "prevention_type": "config",
  "prevention_path": "plugin/agents/{new-agent}.md",
  "subagent_skills_needed": ["skill-1", "skill-2"]
}
```
