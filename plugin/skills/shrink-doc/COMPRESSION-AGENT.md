# Compression Agent Instructions

**INTERNAL DOCUMENT** - This document is for the compression subagent spawned by shrink-doc.
Do NOT read this document if you are orchestrating compression - use SKILL.md instead.

## Your Task

Compress the document at `{{FILE_PATH}}` while preserving execution equivalence.

## Goal

Reduce document size by ~50% while maintaining **perfect execution equivalence** (score = 1.0).
Compression amount is secondary to equivalence - lesser compression is acceptable if needed
to preserve all semantic content.

## What is Execution Equivalence?

A reader following the compressed version achieves the same results as someone following the original.

## Content to Preserve

**MANDATORY - preserve all of these:**
- **YAML frontmatter** (between `---` delimiters) - REQUIRED for slash commands
- **Decision-affecting information**: Claims, requirements, constraints that affect what to do
- **Relationship structure**: Temporal ordering (A before B), conditionals (IF-THEN), prerequisites, exclusions, escalations
- **Control flow**: Explicit sequences, blocking checkpoints (STOP, WAIT), branching logic
- **Executable details**: Commands, file paths, thresholds, specific values
- **Section headers**: `### Section Name` blocks in style docs (detection patterns)

## Content Safe to Remove

- **Redundancy**: Repeated explanations of same concept
- **Verbose explanations**: Long-winded descriptions that can be condensed
- **Meta-commentary**: Explanatory comments about the document (NOT structural metadata)
- **Non-essential examples**: Examples that don't add new information
- **Elaboration**: Extended justifications or background that don't affect decisions

## Compression Approach

**Focus on relationships:**
- Keep explicit relationship statements (Prerequisites, Dependencies, Exclusions, Escalations)
- Preserve temporal ordering (Step A→B)
- Maintain conditional logic (IF-THEN-ELSE)
- Keep constraint declarations (CANNOT coexist, MUST occur after)

**Condense explanations:**
- Remove "Why This Ordering Matters" verbose sections → keep ordering statement
- Remove "Definition" sections that explain obvious terms
- Combine related claims into single statements where possible
- Use high-level principle statements instead of exhaustive enumeration (when appropriate)

## Output

1. Read `{{FILE_PATH}}`
2. Compress the content following the above guidelines
3. **USE THE WRITE TOOL** to save the compressed version to `{{OUTPUT_PATH}}`

**CRITICAL**: You MUST actually write the file using the Write tool. Do NOT just describe
or summarize the compressed content - physically create the file.

## Style Documentation Special Handling

When compressing `.claude/rules/*.md` or `docs/code-style/*-claude.md`:

**Preserve style rule sections** (lines starting with `### `). These are detection patterns.
- ✅ Condense explanatory text within sections
- ✅ Shorten verbose rationale paragraphs
- ❌ Do NOT delete entire `### Section Name` blocks
- ❌ Do NOT remove detection patterns or code examples
