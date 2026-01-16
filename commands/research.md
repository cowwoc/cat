---
name: cat:research
description: Research how to implement a task before planning
argument-hint: "[major.minor/task-name or topic]"
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
  - Write
  - WebFetch
  - WebSearch
---

<objective>

Comprehensive research on HOW to implement something before planning.

Use this for niche/complex domains where Claude's training data is sparse or outdated. Research
discovers:
- What libraries exist for this problem
- What architecture patterns experts use
- What the standard stack looks like
- What problems people commonly hit
- What NOT to hand-roll (use existing solutions)

</objective>

<when_to_use>

**This command is for domains where Claude fails without research:**
- 3D graphics (Three.js, Babylon.js, procedural generation)
- Game development (physics engines, collision, AI, ECS patterns)
- Audio/music (Web Audio, DSP, synthesis, MIDI)
- Shaders (GLSL, Metal, compute shaders)
- ML/AI integration (model serving, inference, vector DBs)
- Real-time systems (WebSockets, WebRTC, CRDT sync)
- Specialized frameworks with active ecosystems Claude may not know

**Skip this for commodity domains:**
- Standard auth (JWT, OAuth)
- CRUD APIs
- Forms and validation
- Well-documented integrations (Stripe, SendGrid)

</when_to_use>

<key_insight>

The question isn't "which library should I use?"

The question is "What do I not know that I don't know?"

For niche domains:
- What's the established architecture pattern?
- What libraries form the standard stack?
- What problems do people commonly hit?
- What's SOTA vs what Claude thinks is SOTA?
- What should NOT be hand-rolled?

</key_insight>

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/.claude/cat/references/research-pitfalls.md

</execution_context>

<process>

<step name="identify_scope">

**Determine research scope:**

If $ARGUMENTS is a task path (major.minor/task-name):
- Load the task's PLAN.md for context
- Extract the domain/technology to research

If $ARGUMENTS is a topic:
- Use directly as research focus

Present scope:
```
Research scope: [topic/technology]

I'll investigate:
1. Core technology - current state, setup, toolchain
2. Ecosystem - standard libraries and stack
3. Architecture - recommended patterns
4. Pitfalls - common mistakes to avoid
5. Don't hand-roll - existing solutions
```

</step>

<step name="research_domains">

**Identify what needs researching:**

| Category | Questions to Answer |
|----------|---------------------|
| Core Technology | What version is current? What's the standard setup? |
| Ecosystem/Stack | What libraries do experts pair with this? |
| Architecture | How do experts structure this type of project? |
| Common Pitfalls | What do beginners get wrong? |
| Don't Hand-Roll | What problems have existing solutions? |
| SOTA Updates | What's changed recently? |

</step>

<step name="execute_research">

**Execute research systematically:**

**1. Official Documentation First:**

Use WebFetch for official docs:
- Getting started guides
- API references
- Best practices sections

**2. WebSearch for Ecosystem Discovery:**

Use current year in queries:
```
- "[technology] best practices {current_year}"
- "[technology] recommended libraries {current_year}"
- "[technology] common mistakes"
- "[technology] vs [alternative] {current_year}"
- "how to build [type of thing] with [technology]"
```

**3. Cross-Verification (MANDATORY):**

Every WebSearch finding MUST be verified:
- Check official docs to confirm
- Mark confidence level (HIGH if verified, MEDIUM if partially verified, LOW if WebSearch only)
- Flag contradictions between sources

</step>

<step name="quality_check">

**Before finalizing, run through pitfalls checklist:**

From research-pitfalls.md:

- [ ] All enumerated items investigated (not just some)
- [ ] Negative claims verified with official docs
- [ ] Multiple sources cross-referenced for critical claims
- [ ] URLs provided for authoritative sources
- [ ] Publication dates checked (prefer recent/current)
- [ ] Tool/environment-specific variations documented
- [ ] Confidence levels assigned honestly
- [ ] Assumptions distinguished from verified facts
- [ ] "What might I have missed?" review completed

</step>

<step name="write_research">

**Create RESEARCH.md:**

If researching for a task, write to task directory:
`.claude/cat/v{major}/v{major}.{minor}/task/{task-name}/RESEARCH.md`

Otherwise, write to project root:
`.claude/cat/RESEARCH-{topic}.md`

```markdown
# Research: [Topic]

**Date:** [YYYY-MM-DD]
**Confidence:** [HIGH|MEDIUM|LOW]

## Standard Stack

| Library | Purpose | Version | Why Standard |
|---------|---------|---------|--------------|
| [lib1] | [purpose] | [ver] | [reason] |

## Architecture Patterns

**Recommended approach:**
[Description with rationale]

**Project structure:**
```
[recommended organization]
```

## Don't Hand-Roll

| Problem | Use Instead | Why |
|---------|-------------|-----|
| [problem] | [library] | [reason] |

## Common Pitfalls

1. **[Pitfall]**: [What goes wrong]
   - Prevention: [How to avoid]

## Code Examples

```[language]
// [Verified pattern from official docs]
```

## Sources

- [URL 1] - [what it covers]
- [URL 2] - [what it covers]

## Open Questions

- [Anything unresolved]
```

</step>

<step name="done">

**Present summary:**

```
Research complete: [topic]

**Key findings:**
- Standard stack: [libraries]
- Pattern: [architecture approach]
- Don't hand-roll: [list]
- Top pitfall: [most important warning]

**Confidence:** [HIGH|MEDIUM|LOW]

Saved to: [path to RESEARCH.md]

---

## Next Steps

This research will be loaded automatically when executing related tasks.

---
```

</step>

</process>

<success_criteria>

- [ ] Research scope identified
- [ ] Official documentation consulted
- [ ] WebSearch findings cross-verified
- [ ] Quality checklist completed
- [ ] RESEARCH.md created with:
  - [ ] Standard stack documented
  - [ ] Architecture patterns documented
  - [ ] Don't hand-roll list
  - [ ] Common pitfalls catalogued
  - [ ] Confidence levels assigned
  - [ ] Sources with URLs

</success_criteria>
