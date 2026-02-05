# Plan: refactor-research-to-prompts

## Goal
Convert the research skill from an orchestrator (that spawns subagents) to a prompt template
library. Main agent spawns research subagents directly using templates from the skill.

## Problem Statement
The research skill currently describes a workflow where it spawns exploration subagents.
If invoked by a subagent, this fails (subagents cannot spawn). Even when invoked by main
agent, the orchestration adds unnecessary complexity.

Better pattern: Research skill provides prompt templates; main agent spawns directly.

## Satisfies
M429 - Technically impossible workflow correction

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:**
  - Changing skill paradigm (orchestrator → library)
  - Existing workflows may expect orchestration
- **Mitigation:**
  - Keep prompt content, change delivery mechanism
  - Document new invocation pattern clearly

## Scope

### Files to Modify
| File | Changes |
|------|---------|
| plugin/skills/research/SKILL.md | Convert to prompt template format |

## Design

### Current Pattern (Broken)
```
User → /cat:research topic
         ↓
    Research skill (orchestrates)
         ↓
    Spawns exploration subagent (FAILS if research invoked by subagent)
```

### New Pattern
```
User → /cat:research topic
         ↓
    Research skill expands to prompt template
         ↓
    Main agent spawns exploration subagent using template
    (Main agent has Task tool, so this works)
```

### Prompt Template Structure
The skill becomes a library of research prompt templates:

```markdown
# Research Skill

Provides prompt templates for research subagents. Main agent selects appropriate
template and spawns subagent directly.

## Templates

### Codebase Exploration
Use when: Understanding unfamiliar code areas
```
Task tool:
  subagent_type: "Explore"
  description: "Research: {topic}"
  prompt: |
    Explore the codebase to understand: {topic}

    Focus on:
    - File locations and structure
    - Key patterns and conventions
    - Dependencies and relationships

    Return structured findings.
```

### Implementation Research
Use when: Planning how to implement a feature
```
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Research: {topic}"
  prompt: |
    Research how to implement: {topic}

    Investigate:
    - Similar existing implementations
    - Required dependencies
    - Potential approaches
    - Trade-offs

    Return recommended approach with rationale.
```

### External Documentation
Use when: Understanding external APIs or libraries
```
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
  description: "Research: {topic}"
  prompt: |
    Research external documentation for: {topic}

    Use WebSearch and WebFetch to find:
    - Official documentation
    - Best practices
    - Common patterns

    Summarize findings relevant to our use case.
```
```

### Invocation Change
- Old: `/cat:research {topic}` → skill orchestrates
- New: `/cat:research {topic}` → skill provides template, main agent spawns

## Acceptance Criteria
- [ ] Research skill converted to prompt template format
- [ ] At least 3 research templates provided (codebase, implementation, external)
- [ ] Clear documentation on how main agent uses templates
- [ ] No orchestration logic remains in skill
- [ ] Main-agent-only restriction added (skill provides templates for spawning)

## Execution Steps

### Step 1: Analyze current research skill
- Read current SKILL.md
- Identify orchestration logic to remove
- Identify prompt content to preserve

### Step 2: Design template structure
- Categorize research types
- Create template for each type
- Include spawn configuration (model, subagent_type)

### Step 3: Rewrite skill as template library
- Remove orchestration sections
- Add template sections
- Add usage instructions for main agent

### Step 4: Add invocation restriction
- Mark as main-agent-only (templates require Task tool)
- Reference delegation rules

### Step 5: Test new pattern
- Invoke /cat:research with a test topic
- Verify main agent can use templates to spawn
- Confirm research results returned correctly

### Step 6: Commit changes
- "config: convert research skill to prompt template library (M429)"
