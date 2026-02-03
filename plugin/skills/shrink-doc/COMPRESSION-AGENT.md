# Compression Agent Instructions

**INTERNAL DOCUMENT** - This document is for the compression subagent spawned by shrink-doc.
Do NOT read this document if you are orchestrating compression - use SKILL.md instead.

## Your Task

Compress the document at `{{FILE_PATH}}` while preserving execution equivalence.

## Goal

Reduce document size by ~50% while maintaining **perfect execution equivalence**.
Compression amount is secondary to equivalence - lesser compression is acceptable if needed
to preserve all semantic content.

**Note**: The orchestrator validates equivalence via /compare-docs. Your job is to preserve
content; the validation tool determines the actual score.

## Clarity Improvement Principle

**Improve clarity, don't just preserve content.**

When compressing, actively reduce ambiguity by:
- Converting implicit temporal ordering to explicit statements
- Normalizing vague language to precise constraints
- Making relationships explicit even if "derivable" from context
- Replacing negative instructions ("don't do X") with positive actionable ones ("do Y")

This reduces extraction variance during validation and improves execution equivalence.

**Apply this principle when:**
- Text uses vague ordering ("then", "next") - clarify to explicit "Step N before Step M"
- Examples show constraints without stating them - add explicit constraint statement
- Relationships are implied but not stated - state them explicitly

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
- **Numbered step sequences**: Lists like "1. X 2. Y 3. Z" must stay as explicit chains
- **Verification checklists**: `- [ ] item` blocks - each item is a separate requirement

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

## Normalization for Clarity

When compressing, apply these normalizations to reduce ambiguity:

**Temporal Ordering:**
- BEFORE: "Run the tests, then deploy"
- AFTER: "Run tests before deployment" (explicit temporal marker)

**Implicit Constraints:**
- BEFORE: Example shows `validate(); process();` without explanation
- AFTER: "Validate before processing (required ordering)"

**Vague Quantifiers:**
- BEFORE: "Wait a bit before retrying"
- AFTER: "Wait before retrying" or remove if no constraint intended

**Conditional Clarity:**
- BEFORE: "You might want to check X"
- AFTER: "Check X if [condition]" or remove if optional

**Negative → Positive:**
- BEFORE: "Don't skip validation"
- AFTER: "Validate before proceeding" (actionable positive instruction)

- BEFORE: "Never deploy without testing"
- AFTER: "Test before deploying" (same constraint, positive framing)

**Principle:** If the original text conveys a constraint (even implicitly), the compressed
version should state that constraint MORE explicitly, not less. Prefer positive actionable
instructions over negative prohibitions when the positive form is equally clear.

## Compression Anti-Patterns

**DO NOT reduce clarity when compressing:**

| Original | Bad Compression | Good Compression |
|----------|-----------------|------------------|
| "Always run A before B" | "Run A and B" | "Run A before B" (preserved) |
| "Step 1, then Step 2, then Step 3" | "Complete all steps" | "Step 1 → Step 2 → Step 3" |
| Example: `init(); start();` | (removed) | "Initialize before starting" |
| "MUST validate OR reject" | "Handle input" | "Validate input; reject if invalid" |
| "Don't skip the tests" | "Don't skip tests" | "Run tests before proceeding" |
| "Never commit without review" | (kept as-is) | "Get review before committing" |

**Key insight:** Compression should reduce TOKENS, not CONSTRAINTS.

## Preserving Numbered Step Sequences

**CRITICAL**: Numbered lists (1, 2, 3...) represent explicit sequence chains. These MUST be
preserved with their numbering intact.

**Why this matters**: Validation extracts numbered lists as single unified sequence units
(e.g., `sequence: A → B → C`). If you reformat steps into prose or unnumbered bullets,
validation extracts them as separate units, causing NOT_EQUIVALENT status even when
semantic content is preserved.

**Preserve:**
```
1. STOP - do NOT proceed
2. Run: `script.sh`
3. Copy-paste output
```

**DO NOT compress to:**
```
Stop, then run script.sh, then copy-paste output.
```

The prose form loses the explicit numbered chain structure that validation relies on.

**Also preserve:**
- Markdown checklists (`- [ ] item`) - each checkbox is a distinct requirement
- "How it works:" numbered flows - explicit execution sequences
- Step-by-step instructions in any numbered format

## Output

1. Read `{{FILE_PATH}}`
2. Compress the content following the above guidelines
3. **USE THE WRITE TOOL** to save the compressed version to `{{OUTPUT_PATH}}`

**CRITICAL**: You MUST actually write the file using the Write tool. Do NOT just describe
or summarize the compressed content - physically create the file.

## CLAUDE.md Special Handling

When compressing `CLAUDE.md`, use **content reorganization** instead of standard compression:

**Step 1: Analyze Content Location**
Before compressing, categorize ALL content into:

| Category | Action |
|----------|--------|
| **Duplicates skills** | REMOVE - reference skill instead |
| **Main-agent-specific** | MOVE to main-agent-specific file |
| **Sub-agent-specific** | MOVE to sub-agent-specific file |
| **Universal (all agents)** | KEEP in CLAUDE.md |

**Step 2: Check for Duplication**
```bash
# Check if content already exists in skills
ls .claude/skills/

# Check if procedural content duplicates a skill
grep -l "pattern" .claude/skills/*/SKILL.md
```

**Step 3: Content Categories**

*Examples are illustrative; specific categories vary by project.*

**REMOVE (duplicates existing):**
- Procedural content that exists in skills
- Content already documented in agent-specific files

**MOVE (agent-specific):**
- Main-agent-only content (e.g., multi-agent coordination, repository structure)
- Sub-agent-only content (e.g., specific workflow steps only they perform)

**KEEP (universal guidance):**
- Tone/style, error handling, security policies
- Content that applies equally to ALL agent types

**Step 4: Result Structure**

CLAUDE.md should be a **slim reference document** (~200 lines) that:
- Contains ONLY universal guidance for ALL agents
- **Instructs agents to read their agent-specific files** (e.g., "MAIN AGENT: Read {file}.md")
- References skills for procedural content (not duplicate them)

**Hub-and-Spoke Pattern**:
```
CLAUDE.md (universal, ~200 lines)
  ├── "MAIN AGENT: Read {main-agent-file}.md"
  └── "SUB-AGENTS: Read {sub-agent-file}.md"
```

Agent-specific files contain the detailed content moved out of CLAUDE.md. Create these files if they
don't exist. CLAUDE.md becomes a routing document that directs agents to their specialized guidance.

---

## Style Documentation Special Handling

When compressing `.claude/rules/*.md` or `docs/code-style/*-claude.md`:

**Preserve style rule sections** (lines starting with `### `). These are detection patterns.
- ✅ Condense explanatory text within sections
- ✅ Shorten verbose rationale paragraphs
- ❌ Do NOT delete entire `### Section Name` blocks
- ❌ Do NOT remove detection patterns or code examples
