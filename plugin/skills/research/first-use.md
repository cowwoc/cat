# Research

Provide prompt templates for spawning research subagents.

<objective>

Provide prompt templates for spawning research subagents. Main agent selects appropriate
template based on research needs and spawns subagent directly using Task tool.

**Observable goal:** Main agent receives template instructions, spawns research subagent
with proper configuration, and receives structured research results.

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

**Main agent only (M429):** This skill provides templates for spawning research subagents.
Subagents cannot spawn other subagents, so this skill cannot be invoked from within a subagent.
Reference: concepts/delegation-rules.md

</when_to_use>

<templates>

## Template 1: Stakeholder Research

**Use when:** Major decisions requiring multi-perspective analysis (payment systems, cloud providers, architecture
patterns)

**Spawn configuration:**
```yaml
Task tool:
  subagent_type: "Explore"
  model: "sonnet"
  description: "Stakeholder research: {topic}"
  prompt: |
    Research {topic} from 9 stakeholder perspectives: architect, security, design,
    testing, performance, ux, sales, marketing, legal.

    Load stakeholder definitions:
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-architect.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-security.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-design.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-testing.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-performance.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-ux.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-sales.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-marketing.md
    @${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-legal.md
    @${CLAUDE_PLUGIN_ROOT}/concepts/research-pitfalls.md

    For each stakeholder:
    1. Use WebSearch and WebFetch to gather 2026 information
    2. Identify 2-3 key concerns from their perspective
    3. Rate implementation options on their dimension (1-5)

    Identify 2-4 distinct implementation approaches. For each:
    - Name and description
    - Top 3 specific providers (if category)
    - Ratings for 11 dimensions: Speed, Cost, Quality, Architect, Security,
      Testing, Performance, UX, Sales, Marketing, Legal
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
User: /cat:research stakeholder-research payment-processing
Main agent: [Spawns subagent with template above, substituting {topic}]
```

---

## Template 2: Implementation Research

**Use when:** Planning how to implement a specific feature (need technical approach, not multi-stakeholder analysis)

**Spawn configuration:**
```yaml
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
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
User: /cat:research implementation rate-limiting-middleware
Main agent: [Spawns subagent with template above]
```

---

## Template 3: Codebase Exploration

**Use when:** Understanding unfamiliar code areas before making changes

**Spawn configuration:**
```yaml
Task tool:
  subagent_type: "Explore"
  model: "sonnet"
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
User: /cat:research codebase authentication-system
Main agent: [Spawns subagent with template above]
```

---

## Template 4: External Documentation Research

**Use when:** Learning external APIs, libraries, or frameworks you'll integrate

**Spawn configuration:**
```yaml
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
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
User: /cat:research external-docs stripe-connect-api
Main agent: [Spawns subagent with template above]
```

</templates>

<process>

<step name="parse_arguments">

**Parse $ARGUMENTS to determine research type and topic:**

| Format | Example | Action |
|--------|---------|--------|
| `<type> <topic>` | `stakeholder payment-processing` | Use specified template |
| `<topic>` only | `authentication-system` | Ask user which template |

If research type not specified, use AskUserQuestion:

```
AskUserQuestion({
  questions: [{
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

| Selection | Template | Subagent Type | Model |
|-----------|----------|---------------|-------|
| Stakeholder Research | Template 1 | Explore | sonnet |
| Implementation Research | Template 2 | general-purpose | sonnet |
| Codebase Exploration | Template 3 | Explore | sonnet |
| External Documentation | Template 4 | general-purpose | sonnet |

</step>

<step name="spawn_subagent">

**Spawn research subagent using Task tool with selected template:**

Example for stakeholder research:

```
Task tool:
  subagent_type: "Explore"
  model: "sonnet"
  description: "Stakeholder research: payment-processing"
  prompt: [Template 1 content with {topic} substituted]
```

Wait for subagent to complete and return results.

</step>

<step name="present_results">

**Present research results to user:**

The subagent will return structured research findings. Display these to the user
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

**Ask user if they want to update PLAN.md with research results:**

Use AskUserQuestion:

```
AskUserQuestion({
  questions: [{
    question: "Would you like to save these research findings to a PLAN.md file?",
    header: "Save Research",
    options: [
      {
        label: "Yes, update PLAN.md",
        description: "Save research to existing PLAN.md or specify path for new file"
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

If yes, ask for PLAN.md path or version identifier:
- If version provided: `.claude/cat/issues/v{major}/v{major}.{minor}/PLAN.md`
- If path provided: Use specified path
- If neither: Ask user to specify

Update PLAN.md with research section containing subagent's findings.

</step>

<step name="done">

**Confirm completion:**

```
Research complete: {topic}
Type: {research-type}
[If saved: "Results saved to: {PLAN.md path}"]

Ready to proceed with implementation planning.
```

</step>

</process>

<success_criteria>

- [ ] Research type and topic identified from arguments or user input
- [ ] Appropriate template selected based on research needs
- [ ] Subagent spawned with correct configuration (model, type, prompt)
- [ ] Research results received from subagent
- [ ] Results presented to user in structured format
- [ ] User offered option to save to PLAN.md (if desired)
- [ ] Research findings available for implementation planning

</success_criteria>
