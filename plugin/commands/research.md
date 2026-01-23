---
name: cat:research
description: Research how to implement a task before planning
argument-hint: "[major.minor or major]"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
  - Task
  - WebFetch
  - WebSearch
  - AskUserQuestion
---

<objective>

Run stakeholder research for a version topic, presenting concerns first, then rated options
with provider recommendations, and allowing user selection via wizard.

**Observable goal:** User selects one approach from 2-4 options; PLAN.md updated with selected
approach's context, providers, and stakeholder guidance.

</objective>

<functions>

This skill uses pre-computed utilities from research_handler.py:

| Function | Purpose |
|----------|---------|
| `rating_to_circles(n)` | Convert 1-5 rating to ●●●●○ pattern |
| `sum_ratings(dict)` | Calculate (total, max) from ratings dict |
| `build_scorecard(ratings)` | Build complete scorecard box for an option |
| `build_comparison_table(options)` | Build side-by-side comparison |
| `build_concerns_box(concerns)` | Build stakeholder concerns display |

**Rating circle patterns (do not hand-type):**
- 5 → ●●●●●
- 4 → ●●●●○
- 3 → ●●●○○
- 2 → ●●○○○
- 1 → ●○○○○

</functions>

<when_to_use>

**Use when:**
- Research from version creation is stale
- You skipped research during `/cat:add-*-version`
- The topic requires fresh investigation
- You want to refresh expertise before implementation

**Don't use when:**
- Version was just created (research already ran)
- Topic is well-understood and stable
- You need project-specific conventions (use codebase exploration instead)

</when_to_use>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/index.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/architect.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/security.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/quality.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/tester.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/performance.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/ux.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/sales.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/marketing.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/stakeholders/legal.md
@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/research-pitfalls.md

</execution_context>

<process>

<step name="identify_version">

**Parse $ARGUMENTS to identify version:**

| Format | Example | Target |
|--------|---------|--------|
| major.minor | `1.2` | `.claude/cat/issues/v1/v1.2/PLAN.md` |
| major | `1` | `.claude/cat/issues/v1/PLAN.md` |

Read the target PLAN.md to extract:
- **Topic**: From `## Focus` or version description
- **Context**: Any existing research, scope, or constraints

```bash
PLAN_PATH=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/PLAN.md"  # or v${MAJOR}/PLAN.md for major
```

Present:
```
Research target: v[version]
Topic: [extracted topic]

I'll spawn 9 stakeholders to research [topic] in parallel,
each acquiring domain expertise from their perspective.
```

</step>

<step name="spawn_stakeholders">

**Spawn all 9 stakeholder research agents in parallel:**

Use the `parallel-execute` skill or spawn 9 Task agents simultaneously:

```yaml
agents:
  - stakeholder: architect
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: security
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: quality
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: tester
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: performance
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: ux
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: sales
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: marketing
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"

  - stakeholder: legal
    mode: research
    topic: "[extracted topic]"
    context: "[version context]"
```

Each agent:
1. Receives the stakeholder definition file for their role
2. Follows the Research Mode instructions
3. Uses WebSearch and WebFetch to gather information
4. Returns structured JSON with their findings

</step>

<step name="collect_results">

**Collect and validate results from all stakeholders:**

For each stakeholder response:
- Parse the JSON output
- Verify all required fields are present
- Check confidence levels
- Note any open questions

Track completion:
```
Stakeholder research progress:
✓ architect - HIGH confidence
✓ security - MEDIUM confidence
✓ quality - HIGH confidence
✓ tester - HIGH confidence
✓ performance - MEDIUM confidence
✓ ux - HIGH confidence
✓ sales - HIGH confidence
✓ marketing - MEDIUM confidence
✓ legal - HIGH confidence
```

</step>

<step name="present_concerns">

**Present stakeholder concerns UP-FRONT:**

After collecting all results, FIRST display concerns organized by stakeholder.
This gives the user visibility into what each perspective considers important
BEFORE showing recommendations.

**ARTIFACT GATE (M192 - use handler function for box):**

Build concerns data structure:
```python
concerns = {
    "ARCHITECT": ["Key concern 1", "Key concern 2"],
    "SECURITY": ["Key concern 1", "Key concern 2"],
    "QUALITY": ["Key concern 1", "Key concern 2"],
    "TESTER": ["Key concern 1", "Key concern 2"],
    "PERFORMANCE": ["Key concern 1", "Key concern 2"],
    "UX": ["Key concern 1", "Key concern 2"],
    "SALES": ["Key concern 1", "Key concern 2"],
    "MARKETING": ["Key concern 1", "Key concern 2"],
    "LEGAL": ["Key concern 1", "Key concern 2"]
}
```

Use `build_concerns_box(concerns)` to render the display. **Do NOT hand-draw the box.**

For each stakeholder, extract the 2-3 most critical concerns from their research.
Focus on:
- Risks and pitfalls they identified
- Anti-patterns to avoid
- Mistakes commonly made
- Edge cases that cause problems

</step>

<step name="identify_options">

**Identify 2-4 distinct implementation approaches:**

Review all stakeholder findings and synthesize coherent strategies.
Each option should address the concerns differently.

For each option, prepare:
- **Name**: Short label (e.g., "Managed Platform", "Custom Build", "Hybrid")
- **Description**: 1-2 sentence summary
- **Top 3 Providers**: If option refers to a category, list top 3 specific providers
- **Ratings**: Integer 1-5 for each of 11 dimensions
- **Best when**: User priorities this suits

**11 Dimensions to rate (1-5 each):**

**Top-level metrics:**
1. **Speed** - Time to implement and deploy
2. **Cost** - Total cost of ownership (development + operational)
3. **Quality** - Code quality and maintainability

**Stakeholder dimensions:**
4. **Architect** - How well it addresses architectural concerns
5. **Security** - How well it addresses security concerns
6. **Tester** - How well it addresses testing concerns
7. **Performance** - How well it addresses performance concerns
8. **UX** - How well it addresses user experience concerns
9. **Sales** - How well it addresses sales/value concerns
10. **Marketing** - How well it addresses marketing concerns
11. **Legal** - How well it addresses legal/compliance concerns

**CALCULATION GATE (M191 - verify before display):**
```
For each option:
- [ ] Speed rating is integer 1-5
- [ ] Cost rating is integer 1-5
- [ ] Quality rating is integer 1-5
- [ ] All 8 stakeholder ratings are integers 1-5
- [ ] Total = Speed + Cost + Quality + Architect + Security + Tester + Performance + UX + Sales + Marketing + Legal
- [ ] Max possible = 55 (11 dimensions × 5)
```

**Provider Research:**
When an option refers to a category (e.g., "Payment Orchestration Platform", "Cloud Provider",
"Authentication Service"), research and list the top 3 specific providers based on:
- Market share and adoption
- Stakeholder research recommendations
- Feature completeness for the topic

Store options as structured data:
```python
options = [
    {
        "name": "Option Name",
        "description": "1-2 sentence description",
        "providers": ["Provider1", "Provider2", "Provider3"],
        "ratings": {
            "Speed": 4, "Cost": 3, "Architect": 4, "Security": 5, "Quality": 3,
            "Tester": 3, "Performance": 3, "UX": 4, "Sales": 4, "Marketing": 3, "Legal": 5
        },
        "best_when": "User priorities this suits"
    },
    ...
]
```

</step>

<step name="present_options">

**Present options AFTER concerns (recommendations at bottom):**

**ARTIFACT GATE (M192 - use handler functions, do not hand-draw):**

Use handler functions for all display elements:
- `build_scorecard(ratings)` - generates the scorecard box for an option
- `build_comparison_table(options)` - generates the side-by-side comparison

For rating circles, lookup from the <functions> section (do NOT type manually):
- rating 5 → ●●●●●
- rating 4 → ●●●●○
- rating 3 → ●●●○○
- rating 2 → ●●○○○
- rating 1 → ●○○○○

**SCORECARD STRUCTURE:** 11 dimensions in 2 sections:
- **Row 1 (top-level):** Speed, Cost, Quality
- **Row 2-4 (stakeholders):** Architect, Security, Tester, Performance, UX, Sales, Marketing, Legal

Use `build_scorecard(option["ratings"])` - this function renders the correct structure.

For each option, display:
1. Option header: "**Option N: {Name}**"
2. Description: 1-2 sentences
3. Providers: "Top Providers: {P1}, {P2}, {P3}"
4. Scorecard: use `build_scorecard(option["ratings"])` - MUST include all 11 dimensions
5. Best-fit: "Best when: {priorities}"

After all options, display comparison table using `build_comparison_table(options)`.

**VERIFICATION before output:**
- [ ] Each circle pattern has exactly 5 characters (● or ○)
- [ ] Total equals sum of all 11 integer ratings
- [ ] Circle patterns match integer ratings exactly

</step>

<step name="wizard_selection">

**Use AskUserQuestion to collect user's choice:**

After presenting options, invoke the wizard:

```
AskUserQuestion({
  questions: [{
    question: "Which approach best fits your priorities for [topic]?",
    header: "Approach",
    options: [
      {
        label: "Option 1: {Name}",
        description: "{1-sentence summary}. Best for: {priority fit}"
      },
      {
        label: "Option 2: {Name}",
        description: "{1-sentence summary}. Best for: {priority fit}"
      },
      {
        label: "Option 3: {Name}",  // if applicable
        description: "{1-sentence summary}. Best for: {priority fit}"
      }
    ],
    multiSelect: false
  }]
})
```

**IMMEDIATELY proceed to `update_plan` step when user selects an option.**
Do NOT wait for additional confirmation or ask "should I proceed?"

</step>

<step name="update_plan">

**Update PLAN.md with ONLY the selected option's context:**

Based on user's selection, write focused research to PLAN.md containing:
1. The selected approach name, description, and ratings
2. Recommended providers with rationale
3. Stakeholder guidance SPECIFIC to implementing this approach
4. Relevant sources for this approach only
5. Open questions specific to this approach

```markdown
## Research

**Topic:** [topic]
**Date:** [YYYY-MM-DD]
**Selected Approach:** [Option Name]
**Overall Confidence:** [confidence level]

### Selected Approach: [Option Name]

[Description of the selected approach]

**Why this approach:** [Brief rationale based on user's priorities]

#### Recommended Providers

| Provider | Why Recommended | Considerations |
|----------|-----------------|----------------|
| [Provider 1] | [Key strengths for this use case] | [Tradeoffs or limitations] |
| [Provider 2] | [Key strengths for this use case] | [Tradeoffs or limitations] |
| [Provider 3] | [Key strengths for this use case] | [Tradeoffs or limitations] |

#### Rating Summary

| Dimension | Rating | Notes |
|-----------|--------|-------|
| Speed | ●●●●○ | [Brief explanation] |
| Cost | ●●●○○ | [Brief explanation] |
| Architect | ●●●●○ | [Brief explanation] |
| Security | ●●●●● | [Brief explanation] |
| Quality | ●●●○○ | [Brief explanation] |
| Tester | ●●●○○ | [Brief explanation] |
| Performance | ●●●○○ | [Brief explanation] |
| UX | ●●●●○ | [Brief explanation] |
| Sales | ●●●●○ | [Brief explanation] |
| Marketing | ●●●○○ | [Brief explanation] |
| Legal | ●●●●● | [Brief explanation] |

### Implementation Guidance

**Architecture** (for selected approach):
[Stack recommendation specific to this option]
[Architecture pattern specific to this option]
[Build vs Use decisions for this approach]

**Security** (for selected approach):
[Security patterns relevant to this option]
[Specific threats to watch for with this approach]
[Secure implementation guidance]

**Quality** (for selected approach):
[Quality patterns for this option]
[Anti-patterns to avoid]
[Code organization recommendations]

**Testing** (for selected approach):
[Testing strategy for this option]
[Critical edge cases]
[Test data patterns]

**Performance** (for selected approach):
[Performance characteristics]
[Optimization strategies]
[Pitfalls to avoid]

**UX** (for selected approach):
[UX patterns for this option]
[Accessibility considerations]
[User feedback requirements]

**Sales/Marketing** (for selected approach):
[Value proposition for this approach]
[Positioning and messaging]

**Legal/Compliance** (for selected approach):
[Compliance requirements]
[Licensing considerations]
[Data privacy obligations]

### Sources

[Only sources relevant to the selected approach]

### Open Questions

[Only questions relevant to the selected approach]

### Alternative Approaches (not selected)

| Option | Providers | Total Score | When to Reconsider |
|--------|-----------|-------------|-------------------|
| [Option 2] | [P1, P2, P3] | [X/55] | [Circumstances that would favor this instead] |
| [Option 3] | [P1, P2, P3] | [X/55] | [Circumstances that would favor this instead] |
```

**Placement:** After `## Focus`/`## Vision`, before `## Scope`/`## Gates`.

</step>

<step name="done">

**Present brief confirmation (do NOT repeat detailed guidance):**

```
Research complete: v[version] - [topic]

Selected approach: [Option Name]
Implementation guidance saved to: [PLAN.md path]

Ready to proceed with implementation planning.
```

**Do NOT** output the detailed stakeholder guidance here - it's already in PLAN.md.
The user can review the file for full details.

</step>

</process>

<success_criteria>

- [ ] Version identified and PLAN.md located
- [ ] Topic extracted from version
- [ ] All 9 stakeholders spawned in parallel
- [ ] All 9 stakeholders returned results
- [ ] Stakeholder concerns presented UP-FRONT
- [ ] 2-4 options identified from research
- [ ] Each option has 5-circle ratings for all 11 dimensions (Speed, Cost, Quality + 8 stakeholders)
- [ ] Each option lists top 3 specific providers (when applicable)
- [ ] Side-by-side comparison table with totals presented
- [ ] Options presented with tradeoffs AFTER concerns
- [ ] User selected approach via wizard
- [ ] PLAN.md updated with ONLY selected option's context
- [ ] PLAN.md includes rating summary table with explanations
- [ ] PLAN.md includes provider recommendations with rationale
- [ ] Guidance filtered to selected approach
- [ ] Sources filtered to selected approach
- [ ] Alternative approaches documented with providers and scores

</success_criteria>
