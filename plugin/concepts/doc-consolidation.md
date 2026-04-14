# Document Consolidation

## Consolidation vs. Shrinking

Two skills address documentation quality, with different goals:

| Concern | Skill | Goal |
|---------|-------|------|
| File is too large | `cat:instruction-builder-agent` | Reduce token count while preserving meaning |
| Content is scattered or redundant | `cat:instruction-organizer-agent` | Reorganize into coherent, non-redundant form |

**Use instruction-builder-agent** when the document says what it needs to say but takes too many tokens to say it. Size is the
problem; organization is not.

**Use instruction-organizer-agent** when the same information appears in multiple sections, or when workflow steps are
scattered across subsections, notes, and reminders rather than organized sequentially. Organization is the problem;
size may or may not also be an issue.

**Do not conflate the two.** Consolidation can increase file size (Wave 3 produced 211 lines from 198 because
horizontal rule separators were added for clarity). Shrinking does not reorganize content.

---

## Document Type Categories

The consolidation pipeline applies different reconstruction strategies depending on document type. Classification
happens before extraction (Phase 0 of the pipeline).

### Procedural / Instructional Documents

**Characteristics**: Sequential workflow steps, decision gates, iteration loops. Dominant semantic categories are
SEQUENCE and CONDITIONAL.

**Examples**: Skill first-use.md files, agent instructions with step-by-step workflows.

**Consolidation strategy**: Backward chaining — identify the document's primary execution goal, work backward
through prerequisites, reconstruct in forward order. Apply an editorial structure layer after backward chaining to
preserve content from sections like "Implementation Notes" and "CRITICAL REMINDER" that contain distinct constraints
not captured by execution order alone.

**Known limitation**: Editorial sections often contain requirements that are NOT redundant with the main workflow
steps, even when they appear to elaborate on the same topic. Dropping them causes NOT_EQUIVALENT verdicts.

### Reference / Lookup Documents

**Characteristics**: Tables, mappings, named configuration options, glossaries. Dominant semantic categories are
MAPPING and RULE. No single primary goal — readers consult the document for multiple distinct lookup purposes.

**Examples**: Commit type tables, configuration option listings, error code catalogs.

**Consolidation strategy**: Topical clustering — group units by topic, order within each topic from general to
specific. Do not apply backward chaining; it produces incomplete results by omitting sections that serve lookup
goals other than the primary chain goal.

**Validated result**: `commit-types.md` (67 units) achieved EQUIVALENT (0 lost) using topical clustering.

### Conditionally-Loaded Multi-File Sets

**Characteristics**: Multiple files where each file is loaded on-demand based on runtime logic, not eagerly loaded
together. Common patterns include `first-use.md` (loaded only on first skill invocation), agent `.md` files (loaded
only when that stakeholder/subagent type is selected), and concept files (loaded by skills on demand).

**Consolidation strategy**: Do NOT consolidate. The file separation exists to minimize context injection — only the
files needed for the current execution path are loaded. Merging them into a single file would cause all content to be
injected upfront, defeating the lazy-loading design and wasting context on content irrelevant to the current path.

### Architecture-Bounded Multi-File Sets

**Characteristics**: Multiple files separated by design, where each file serves a distinct audience. Common patterns
include orchestrator + subagent pairs, caller + implementation pairs, and public API + internal documentation pairs.

**Markers to detect**:
- `**INTERNAL DOCUMENT**` or equivalent audience restriction header
- Language like "X is intentionally not in this file" or "Do not read this if you are Y"
- A file that invokes another as a subagent (the invocation is an architectural boundary)

**Consolidation strategy**: Do NOT consolidate. These files exist as separate documents because the separation is a
design invariant, not a documentation defect. Consolidating them exposes internals to the caller, violating
encapsulation. In the `instruction-builder-agent` skill, the compression algorithm is intentionally hidden from the orchestrator
to prevent manual bypass — consolidating would defeat this design.

---

## Known Failure Modes

### FM-1: Editorial Sections Removed

Backward chaining reconstructs execution ORDER but does not account for editorial GROUPING. Sections like
"Implementation Notes" and "CRITICAL REMINDER" contain constraints unique to those sections, not duplicated in the
main workflow steps. Consolidating by execution order alone dissolves these sections and loses their content.

**Evidence**: Wave 1 lost 6/56 units (11%) because "Implementation Notes" and "Validation Context" subsections were
treated as elaborations of already-captured content.

**Prevention**: After backward chaining, apply an editorial structure layer. Verify that named sections are preserved
as subsections in the consolidated document, not dissolved into adjacent steps.

### FM-2: Architectural Encapsulation Violated

Files with explicit audience separation (orchestrator, subagent, public caller, private implementation) cannot be
merged without violating the architectural invariant that motivated the separation.

**Evidence**: Wave 2 — `first-use.md` and `compression-protocol.md` are separated specifically because the orchestrator
should not know compression internals. Merging them would allow the orchestrator to bypass the skill and compress
manually.

**Prevention**: Architectural Boundary Check hard gate in Phase 0 before any extraction.

### FM-3: Volume-Induced Loss

As the combined unit count grows, consolidation pressure increases and individual precision requirements are dropped.
Wave 1 (56 units): 6 lost. Wave 2 File A (59 units in combined 101): 3 lost from File A despite correct strategy.

**Evidence**: The requirement "report the ACTUAL score from the validation protocol — do not summarize or interpret" was dropped
when condensing the combined wave 2 content.

**Prevention**: Consolidate one file at a time unless files have the same audience and topic. The binary equivalence
gate catches volume-induced loss after the fact.

### FM-4: Cross-File References Made Dangling

Inlining the content of a referenced file eliminates the cross-file reference. The reference is itself a semantic
unit — an architectural boundary marker — and must be preserved. After consolidation, references must still point
to valid targets.

**Prevention**: Preserve cross-file references as semantic units. Do not inline referenced file content unless the
referenced file is also being removed (and the consolidation accounts for this).

### FM-5: Backward Chaining Incomplete for Multi-Goal Docs

Reference documents serve multiple lookup goals simultaneously. Backward chaining from a single primary goal omits
sections that serve other goals. The omitted sections do not appear in the chain and are silently dropped from the
reconstruction.

**Evidence**: Wave 3 analysis — if backward-chaining from "select correct commit type," the "discovery procedures"
branch (u59–u67, covering `git log` queries for finding commits by issue) would be dropped as not required for the
primary goal.

**Prevention**: Classify document type in Phase 0. Reject backward chaining for reference docs; use topical
clustering.

---

## Binary Execution-Equivalence Gate

All consolidation attempts must pass a binary equivalence check using the validation protocol (see `instruction-builder-agent/validation-protocol.md`) before changes are applied.

**EQUIVALENT**: All semantic units preserved. Apply the consolidated version.

**NOT_EQUIVALENT**: One or more units lost. Do NOT apply. Examine the LOST section, identify which units were
dropped and which failure mode caused the loss, revise the reconstruction, and re-verify.

The gate is non-negotiable. "Close enough" is not acceptable for execution-relevant documentation. A consolidated
document that loses a single precision requirement ("report ACTUAL score, not summary") produces materially different
agent behavior.

For multi-file consolidation: the gate must pass against EACH original file independently. An EQUIVALENT verdict for
File B does not compensate for NOT_EQUIVALENT on File A.

---

## See Also

- `cat:instruction-builder-agent` — reduce token count while preserving meaning
- `instruction-builder-agent/validation-protocol.md` — validate semantic equivalence; provides the binary verdict used
  as the consolidation gate
- `plugin/skills/instruction-organizer-agent/first-use.md` — full methodology, phase-by-phase instructions, and
  validation evidence from Waves 1–3
