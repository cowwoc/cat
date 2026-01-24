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
---

<objective>

Manually trigger stakeholder research for a version.

This command runs the same stakeholder research that automatically runs during `/cat:add-major-version`
and `/cat:add-minor-version`. Use it when:
- Previous research is out-of-date
- Research wasn't done during version creation
- Deeper investigation is needed

All 9 stakeholders research the version's topic in parallel, producing domain expertise from their
respective perspectives.

</objective>

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

<step name="aggregate_research">

**Aggregate findings into PLAN.md Research section:**

Read the existing PLAN.md, then add or update the `## Research` section:

```markdown
## Research

**Topic:** [topic]
**Date:** [YYYY-MM-DD]
**Overall Confidence:** [lowest of all stakeholder confidences]

### Architect Perspective
**Stack:** [recommendation with rationale]
**Architecture:** [pattern and structure]
**Build vs Use:** [what to build, what to use]
[Sources: URLs]

### Security Perspective
**Threats:** [topic-specific vulnerabilities]
**Secure Patterns:** [how to implement securely]
**Mistakes to Avoid:** [common security errors]
[Sources: URLs]

### Quality Perspective
**Patterns:** [idiomatic approaches]
**Anti-Patterns:** [what to avoid]
**Maintainability:** [organization and conventions]
[Sources: URLs]

### Tester Perspective
**Strategy:** [testing approach for topic]
**Edge Cases:** [what to test]
**Hard to Test:** [patterns for difficult cases]
[Sources: URLs]

### Performance Perspective
**Characteristics:** [scale and metrics]
**Efficient Patterns:** [optimized approaches]
**Pitfalls:** [deceptively slow operations]
[Sources: URLs]

### UX Perspective
**Patterns:** [expected interactions]
**Usability:** [what makes it easy vs hard]
**Accessibility:** [inclusive design considerations]
[Sources: URLs]

### Sales Perspective
**Value Proposition:** [customer problems solved, quantifiable value]
**Competitive Positioning:** [differentiation, advantages]
**Objection Handling:** [common concerns and responses]
[Sources: URLs]

### Marketing Perspective
**Positioning:** [market category, how to position]
**Messaging:** [what resonates with buyers]
**Go-to-Market:** [launch strategy, channels]
[Sources: URLs]

### Legal Perspective
**Licensing:** [license requirements and compatibility]
**Compliance:** [applicable regulations and frameworks]
**IP Considerations:** [patents, trademarks, copyrights]
**Data Privacy:** [privacy requirements and obligations]
[Sources: URLs]

### Open Questions
- [Unresolved items from all stakeholders]
```

**Placement:** After `## Focus`/`## Vision`, before `## Scope`/`## Gates`.

</step>

<step name="synthesize_executive_summary">

**Synthesize findings into executive summary:**

After aggregating stakeholder perspectives, analyze them to produce an executive summary
with actionable options and tradeoffs.

**1. Check for pre-computed templates:**

Look in context for "PRE-COMPUTED RESEARCH TEMPLATES". These provide:
- Box borders (top, bottom, divider)
- Line format (width=74, "│ " prefix, " │" suffix)
- Structure guidelines

**2. Identify distinct approaches (reasoning):**

Review all stakeholder findings and identify 2-4 distinct solution approaches.
Each approach should be a coherent strategy that emerges from the findings.

Consider patterns like:
- Cloud-native vs self-hosted
- Build vs buy
- Speed-focused vs quality-focused
- Minimal dependencies vs full framework

**3. For each approach, extract:**

- **Name**: Short label (e.g., "Cloud-Native Stack", "Minimal Dependencies")
- **Description**: 1-2 sentence summary
- **Advocates**: Which stakeholders favor this
- **Tradeoffs**: Concerns from other stakeholders
- **Best when**: User preferences this suits (cost/speed/simplicity/security)

**4. Build executive summary using templates:**

Use pre-computed templates to build the output. For each line:
- Start with "│ "
- Add content left-justified to 74 characters
- End with " │"

```
{TOP_BORDER from templates}
│ 📋 Executive Summary                                                     │
{DIVIDER}
│                                                                          │
│ Approaches Identified: {count}                                           │
│                                                                          │
{DIVIDER}
│ Option 1: {Name}                                                         │
│                                                                          │
│ Description: {1-2 sentences, wrap if needed}                             │
│                                                                          │
│ Advocates: {stakeholders}                                                │
│                                                                          │
│ Tradeoffs:                                                               │
│   • {Stakeholder}: {concern}                                             │
│   • {Stakeholder}: {concern}                                             │
│                                                                          │
│ Best when: {preference fit}                                              │
│                                                                          │
{DIVIDER}
... repeat for each option ...
{DIVIDER}
│ ⚡ Quick Decision Guide                                                  │
│                                                                          │
│ If you prioritize...        Consider...                                  │
│ ─────────────────────────────────────────                                │
│ Speed to market             Option {X}                                   │
│ Long-term maintainability   Option {Y}                                   │
│ Minimal cost                Option {Z}                                   │
│ Security compliance         Option {W}                                   │
│                                                                          │
{BOTTOM_BORDER from templates}
```

**5. Add to PLAN.md:**

Insert executive summary at the START of the `## Research` section, before
individual stakeholder perspectives:

```markdown
## Research

### Executive Summary

{rendered box from above}

### Architect Perspective
...
```

</step>

<step name="done">

**Present summary:**

```
Research complete: v[version] - [topic]

Stakeholder expertise acquired:
├─ Architect: [key insight]
├─ Security: [key insight]
├─ Quality: [key insight]
├─ Tester: [key insight]
├─ Performance: [key insight]
├─ UX: [key insight]
├─ Sales: [key insight]
├─ Marketing: [key insight]
└─ Legal: [key insight]

Overall confidence: [HIGH|MEDIUM|LOW]
Saved to: [PLAN.md path]

This research will inform implementation to maximize quality,
efficiency, and stakeholder satisfaction.
```

</step>

</process>

<success_criteria>

- [ ] Version identified and PLAN.md located
- [ ] Topic extracted from version
- [ ] All 9 stakeholders spawned in parallel
- [ ] All 9 stakeholders returned results
- [ ] Results aggregated into PLAN.md Research section
- [ ] Confidence levels assigned
- [ ] Sources documented
- [ ] Executive summary synthesized with 2-4 options
- [ ] Each option has name, description, advocates, tradeoffs, best-when
- [ ] Quick decision guide maps preferences to options

</success_criteria>
