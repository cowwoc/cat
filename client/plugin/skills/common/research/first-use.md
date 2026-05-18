<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Research

Provide prompt templates for dedicated research agents.

<objective>

Provide prompt templates for research work. The main agent delegates to the dedicated research agent. When this file is
read by the dedicated research agent, the agent executes the selected template directly.

**Observable goal:** Main agent receives structured research results from the dedicated research agent.

</objective>

<when_to_use>

**Use when:**
- You need deep research before making implementation decisions
- Topic requires multi-perspective analysis (stakeholder research)
- Understanding unfamiliar code areas (codebase exploration)
- Learning external APIs or libraries (external documentation)
- Planning major architectural changes

**Don't use when:**
- Simple code changes with clear approach
- Topic is well-understood and no research needed
- Just need to read a few files (use Read/Grep directly)

**Dedicated-agent mode:** If the current agent is `cat-research-agent` or `research-agent`, execute the selected
research template directly. Do not spawn another research agent.
Reference: concepts/delegation-rules.md

</when_to_use>

<templates>

## Template 1: Stakeholder Research

**Use when:** Major decisions requiring multi-perspective analysis (payment systems, cloud providers, architecture
patterns)

**Spawn configuration:**
```yaml
Runtime-native agent tool:
  agent_type: "cat-research-agent"
  description: "Stakeholder research: {topic}"
  prompt: |
    Research {topic} from 8 stakeholder perspectives: architecture, security, design,
    testing, performance, ux, business, legal.

    Load stakeholder definitions:
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-architecture.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-security.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-design.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-testing.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-performance.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-ux.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-business.md
    @${CAT_PLUGIN_ROOT}/agents/common/stakeholder-legal.md
    @${CAT_PLUGIN_ROOT}/concepts/research-pitfalls.md

    For each stakeholder:
    1. Use WebSearch and WebFetch to gather 2026 information
    2. Identify 2-3 key concerns from their perspective
    3. Rate implementation options on their dimension (1-5)

    Identify 2-4 distinct implementation approaches. For each:
    - Name and description
    - Top 3 specific providers (if category)
    - Ratings for 10 dimensions: Speed, Cost, Quality, Architecture, Security,
      Testing, Performance, UX, Business, Legal
    - Best-fit scenario

    Output format:
    1. Stakeholder concerns (organized by role)
    2. Implementation options with scorecards
    3. Side-by-side comparison table
    4. Recommended providers with rationale
    5. Sources consulted

    Present concerns FIRST, then options with ratings.
```

**Example usage:**
```
User: Research stakeholder concerns for payment-processing
Main agent: [Spawns agent with template above, substituting {topic}]
```

---

## Template 2: Implementation Research

**Use when:** Planning how to implement a specific feature (need technical approach, not multi-stakeholder analysis)

**Spawn configuration:**
```yaml
Runtime-native agent tool:
  agent_type: "cat-research-agent"
  description: "Implementation research: {topic}"
  prompt: |
    Research how to implement: {topic}

    Investigate:
    1. Similar existing implementations in this codebase
       - Use Grep/Glob to find related code
       - Identify patterns and conventions used

    2. Required dependencies and libraries
       - Check existing package.json/requirements.txt/pom.xml
       - Research latest versions and compatibility

    3. Potential approaches with trade-offs
       - Approach A: [pros/cons]
       - Approach B: [pros/cons]
       - Approach C: [pros/cons]

    4. Recommended approach with rationale
       - Why this approach fits best
       - How it aligns with existing codebase patterns
       - What dependencies/changes required

    Use WebSearch for 2026 best practices and documentation.

    Output structured findings with:
    - Existing patterns found in codebase
    - Recommended approach (with rationale)
    - Implementation steps outline
    - Dependencies required
    - Potential risks/pitfalls
```

**Example usage:**
```
User: Research how to implement rate-limiting-middleware
Main agent: [Spawns agent with template above]
```

---

## Template 3: Codebase Exploration

**Use when:** Understanding unfamiliar code areas before making changes

**Spawn configuration:**
```yaml
Runtime-native agent tool:
  agent_type: "cat-research-agent"
  description: "Codebase exploration: {topic}"
  prompt: |
    Explore the codebase to understand: {topic}

    Focus on:
    1. File locations and structure
       - Use Glob to find relevant files
       - Map directory organization
       - Identify entry points

    2. Key patterns and conventions
       - Naming conventions
       - Code organization patterns
       - Common abstractions used

    3. Dependencies and relationships
       - How components interact
       - Data flow patterns
       - External dependencies

    4. Test coverage
       - Existing test files
       - Test patterns used
       - Coverage gaps

    Return structured findings:
    - File map (paths and purposes)
    - Key patterns identified
    - Dependency diagram (conceptual)
    - Test coverage assessment
    - Recommended approach for modifications
```

**Example usage:**
```
User: Explore the codebase to understand authentication-system
Main agent: [Spawns agent with template above]
```

---

## Template 4: External Documentation Research

**Use when:** Learning external APIs, libraries, or frameworks you'll integrate

**Spawn configuration:**
```yaml
Runtime-native agent tool:
  agent_type: "cat-research-agent"
  description: "External docs research: {topic}"
  prompt: |
    Research external documentation for: {topic}

    Use WebSearch and WebFetch to find:
    1. Official documentation (2026 versions)
       - Getting started guides
       - API reference
       - Integration examples

    2. Best practices and patterns
       - Recommended usage patterns
       - Common pitfalls to avoid
       - Security considerations

    3. Integration requirements
       - Dependencies needed
       - Configuration required
       - Authentication/authorization setup

    4. Relevant to our use case
       - Filter for features we need
       - Identify optional vs required components
       - Estimate integration complexity

    Output:
    - Quick start summary
    - Key concepts and terminology
    - Integration checklist
    - Code examples (adapted to our stack)
    - Potential issues and solutions
```

**Example usage:**
```
User: Research external documentation for stripe-connect-api
Main agent: [Spawns agent with template above]
```

</templates>

<process>

<step name="parse_arguments">

**Parse $ARGUMENTS to determine research type and topic:**

| Format | Example | Action |
|--------|---------|--------|
| `<type> <topic>` | `stakeholder payment-processing` | Use specified template |
| `<topic>` only | `authentication-system` | Ask user which template |

If research type not specified, use Structured user-choice prompt:

```
Structured user-choice prompt:
  questions:
  - {
    question: "What type of research do you need for: {topic}?",
    header: "Research Type",
    options: [
      {
        label: "Stakeholder Research",
        description: "Multi-perspective analysis for major decisions (payment systems, cloud providers, etc.)"
      },
      {
        label: "Implementation Research",
        description: "Technical approach for specific feature implementation"
      },
      {
        label: "Codebase Exploration",
        description: "Understanding unfamiliar code areas before modification"
      },
      {
        label: "External Documentation",
        description: "Learning external APIs or libraries for integration"
      }
    ],
    multiSelect: false
  }]
})
```

</step>

<step name="select_template">

**Map user selection to template:**

| Selection | Template | Agent Type | Model |
|-----------|----------|---------------|-------|
| Stakeholder Research | Template 1 | cat-research-agent | explicit agent config |
| Implementation Research | Template 2 | cat-research-agent | explicit agent config |
| Codebase Exploration | Template 3 | cat-research-agent | explicit agent config |
| External Documentation | Template 4 | cat-research-agent | explicit agent config |

</step>

<step name="spawn_subagent">

**Run the selected template in the dedicated research agent. If this file is being read by `cat-research-agent`, execute the selected template directly and do not spawn another agent:**

Example for stakeholder research:

```
Runtime-native agent tool:
  agent_type: "cat-research-agent"
  description: "Stakeholder research: payment-processing"
  prompt: [Template 1 content with {topic} substituted]
```

Wait for agent to complete and return results.

</step>

<step name="present_results">

**Present research results to user:**

The agent will return structured research findings. Display these to the user
with any necessary context or navigation aids.

For stakeholder research specifically:
- Concerns will be presented first
- Options with detailed scorecards
- Comparison table
- Provider recommendations

For other research types:
- Findings organized by investigation area
- Recommended approaches with rationale
- Implementation guidance or next steps

</step>

<step name="offer_plan_update">

**Ask user if they want to update plan.md with research results:**

Use Structured user-choice prompt:

```
Structured user-choice prompt:
  questions:
  - {
    question: "Would you like to save these research findings to a plan.md file?",
    header: "Save Research",
    options: [
      {
        label: "Yes, update plan.md",
        description: "Save research to existing plan.md or specify path for new file"
      },
      {
        label: "No, just use the findings",
        description: "Keep research in conversation only"
      }
    ],
    multiSelect: false
  }]
})
```

If yes, ask for plan.md path or version identifier:
- If version provided: `.cat/issues/v{major}/v{major}.{minor}/plan.md`
- If path provided: Use specified path
- If neither: Ask user to specify

Update plan.md with research section containing agent's findings.

</step>

<step name="done">

**Confirm completion:**

```
Research complete: {topic}
Type: {research-type}
[If saved: "Results saved to: {plan.md path}"]

Ready to proceed with implementation planning.
```

</step>

</process>

<success_criteria>

- [ ] Research type and topic identified from arguments or user input
- [ ] Appropriate template selected based on research needs
- [ ] Agent spawned with correct configuration (model, type, prompt)
- [ ] Research results received from agent
- [ ] Results presented to user in structured format
- [ ] User offered option to save to plan.md (if desired)
- [ ] Research findings available for implementation planning

</success_criteria>
