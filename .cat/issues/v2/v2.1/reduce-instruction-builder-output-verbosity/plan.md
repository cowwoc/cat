# reduce-instruction-builder-output-verbosity

## Goal

Update instruction-builder-agent's design methodology and skill conventions to teach the design
subagent to create skills that communicate leanly between agents, eliminating unnecessary
information from subagent prompts, return contracts, and exchanged files.

## Background

When instruction-builder creates new skills and commands, those skills often contain verbose
intra-agent communication:

1. **Subagent prompts**: Repeated rationale, constraint blocks, and parameter explanations
   duplicated across multiple subagent invocations instead of centralized
2. **Inline content relay**: Full document content passed inline in prompts instead of file
   path references, forcing subagents to receive large documents
3. **Verbose return contracts**: Narrative paragraphs explaining what a subagent returns when
   JSON schemas already specify it
4. **Agent-to-agent files**: Exchanged files contain explanatory text, rationale, and context
   never read by downstream agents
5. **Over-explained procedures**: Multi-sentence justifications for rules that could be expressed
   in one sentence, when agents don't read rationale anyway

This inflates token usage in Agent request/response exchanges and inter-agent communication
without benefiting users (who never see agent-to-agent exchanges).

## What to change

In `plugin/skills/instruction-builder-agent/skill-conventions.md`:

Add a new section **"Lean Intra-Agent Communication"** (2,000–3,000 words) after the existing
"Delegation Safety Check" section (around line 1333). This section teaches the design subagent to:

1. **Eliminate redundancy in subagent prompts**: Don't repeat constraints, rationale, or parameter
   explanations in each subagent invocation. Use file references or section cross-references instead.
   Example: Instead of embedding 35-line read-only constraints in every subagent prompt, reference
   `§ Shared Constraints` or use a file path.

2. **Replace inline content with file references**: When a subagent needs to read or process a
   document, use file paths instead of embedding content inline. Inline content forces agents to
   receive and parse large blocks; file references let them Read on-demand and only parse what's
   used.

3. **Compact return contracts**: Return JSON should be structured (with required fields) but
   not explained in prose. If explanation is needed, inline it as a one-line comment in the JSON
   or in a separate `## Return Contract Reference` section agents read once, not repeated per
   invocation.

4. **Minimize agent-to-agent files**: Files exchanged between agents (not user-facing) should
   contain only data agents will actually use. Remove explanatory prose, rationale, context, and
   commentary that agents won't read. Agents can't appreciate explanation — they process only
   structured data.

5. **Use terse procedure language**: "Validate inputs", not "Validate inputs to ensure they meet
   requirements and prevent downstream errors, which could cause...". Agents skip rationale and
   execute actions; lengthy explanations consume tokens without improving understanding.

Examples demonstrating the principle:

```markdown
# ❌ WRONG: Verbose subagent prompt with repeated constraints
Task tool:
  description: "Design skill for {goal}"
  prompt: |
    ## Constraint 1: Read-Only
    This is a read-only design phase. Do NOT modify files.
    Tools permitted: Read only. Do NOT use Write, Edit, Bash, etc.
    [Details of 35 lines about read-only constraints]
    
    ## Constraint 2: No Spawning
    Do NOT spawn subagents. Do NOT invoke Task tool.
    [Details of 10 lines]
    
    ## Procedure
    Step 1: Read design methodology from...
    [Agent reads the same 45-line constraint block]

# ✅ CORRECT: Lean subagent prompt with file reference
Task tool:
  description: "Design skill for {goal}"
  prompt: |
    ## Constraints
    See ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/design-phase-constraints.md
    
    ## Procedure
    Step 1: Read methodology from ${CLAUDE_PLUGIN_ROOT}/skills/instruction-builder-agent/design-methodology.md
```

```markdown
# ❌ WRONG: Verbose return contract in prose
Subagent returns the complete designed instruction document as markdown.
The document should be syntactically valid markdown, properly formatted,
with all sections present, and ready to be written to a file. The return
value must not include any meta-information about the design process,
only the final document content.

# ✅ CORRECT: Compact return contract
Subagent returns: Complete instruction document (markdown).
Format: Markdown code block containing the full skill document ready for Write.
```

```markdown
# ❌ WRONG: Agent-to-agent file with explanatory prose
## Design Phase Results

The design subagent ran the methodology backward chaining process and identified
the following decomposition. This is the conceptual structure before it is
converted to executable steps. Understanding this decomposition helps...

[50 lines of explanation that agents never read]

DECOMPOSITION:
[actual structured data]

# ✅ CORRECT: Lean agent-to-agent file (only data)
DECOMPOSITION:
[structured data only]
```

## Post-conditions

- [ ] `skill-conventions.md` includes new "Lean Intra-Agent Communication" section (2,000+
  words) with methodology, examples, and checklist
- [ ] Section covers: redundancy elimination, file references, compact return contracts,
  minimal agent-to-agent files, terse procedures
- [ ] Includes anti-pattern examples (verbose) and correct patterns (lean)
- [ ] Includes checklist for designers to audit their skills for verbosity
- [ ] Section is discoverable from design methodology and referenced by design-phase docs
- [ ] No behavioral changes to instruction-builder — only educational content to guide
  future skill design
- [ ] No SPRT re-run required — documentation-only change
