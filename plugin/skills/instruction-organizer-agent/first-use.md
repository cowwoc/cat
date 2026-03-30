<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Instruction Organization via Extract-Backward-Chain-Verify

## Overview

The instruction-organizer-agent skill reorganizes scattered documentation into a coherent, non-redundant form using a
four-phase pipeline: **Phase 0 (Classify)** → **Phase 1 (Extract)** → **Phase 2 (Reconstruct)** → **Phase 3
(Verify)**. The pipeline was validated across three document types; results determined the scope and strategy
selection rules documented here.

### When to Use

Use this skill when a document (or set of documents) has:
- Content that appears in multiple sections but expresses the same constraint once
- Workflow steps scattered across subsections, headers, and notes rather than organized sequentially
- Redundant restatements of the same requirement in "Notes", "CRITICAL", and "Reminder" sections

**Do NOT use to reduce file size.** Use the `instruction-builder-agent` compression protocol for size reduction; use this skill only when content
organization is the problem.

### When NOT to Use

Do NOT consolidate when any of the following applies:

1. **Architectural encapsulation boundaries exist**: Files separated by design to hide implementation details from
   callers (e.g., orchestrator + subagent pairs). Look for markers like `**INTERNAL DOCUMENT**`, audience restrictions
   ("Do NOT read if you are X"), or explicit intent statements ("X is intentionally not here"). Consolidating these
   files violates the design invariant and can create security-relevant information leaks.

2. **Volume exceeds safe threshold**: Each additional file increases consolidation loss risk. A single 56-unit file
   lost 6 units (11%); two files totaling 101 units lost 3 units from File A alone. The binary equivalence gate
   catches this, but only after the consolidation attempt.

3. **Conditionally-loaded content across files**: Files that are loaded on-demand based on runtime logic (e.g.,
   `first-use.md` loaded only on first skill invocation, agent `.md` files loaded only when that stakeholder is
   selected, concept files loaded by skills on demand). Merging these into a single file defeats the lazy-loading
   design — all content would be injected into context upfront instead of conditionally. The separation exists to
   minimize token usage, not because of disorganization.

4. **Document is reference material that is already well-organized**: Well-maintained reference docs with lookup
   tables and no procedural flow often have no genuine redundancy. The consolidation attempt will succeed (EQUIVALENT)
   but deliver no organizational benefit.

---

## Document Type Classification (Phase 0)

Before extracting semantic units, classify the document to select the correct reconstruction strategy.

### Classification Method

Extract 10–15 representative semantic units. Count the dominant category:

| Dominant Category | Document Type | Reconstruction Strategy |
|-------------------|---------------|------------------------|
| SEQUENCE / CONDITIONAL (≥40%) | Procedural/instructional | Backward chaining |
| MAPPING / RULE (≥40%) | Reference/lookup tables | Topical clustering |
| Mixed (no dominant category) | Hybrid | Classify sections; apply per-section strategy |

### Architectural Boundary Check (Hard Gate)

Before proceeding to Phase 1, check for consolidation blockers:

- Does any file contain `**INTERNAL DOCUMENT**` or equivalent audience restriction?
- Does any file contain language like "X is intentionally not in this file"?
- Are the files an orchestrator + subagent pair where one invokes the other?
- Are the files conditionally loaded based on runtime logic (e.g., `first-use.md` loaded on first invocation only,
  agent `.md` files loaded when a specific subagent type is selected, concept files loaded by skills on demand)?
  Merging conditionally-loaded files defeats lazy-loading and injects all content upfront regardless of execution path.

If any check is positive: **STOP. Do not consolidate.** These files are separated by design, not by accident.
Document the finding and return to the caller.

---

## Phase 1: Semantic Extraction

Extract all execution-relevant semantic units from the source document(s). Tag each unit with:
- **Category**: REQUIREMENT, PROHIBITION, SEQUENCE, CONDITIONAL, CONSEQUENCE, DEPENDENCY, MAPPING, RULE, PRINCIPLE,
  REFERENCE, REASON, FORMAT, PROCEDURE, EXAMPLE-CORRECT, EXAMPLE-WRONG
- **Location**: file + section heading path
- **Quote**: verbatim or near-verbatim text
- **Cross-references**: other units this unit depends on or contradicts

For multi-file consolidation: extract independently per file (use separate unit ID prefixes, e.g., A1–An, B1–Bn),
then merge into a combined extraction set. Preserve file-of-origin for each unit through the full pipeline.

**Cross-reference preservation rule**: Cross-file references (e.g., "subagent reads compression-protocol.md") are
semantic units in their own right. Do not merge a reference with the content it references — the reference is an
architectural boundary marker.

---

## Phase 2: Reconstruction

Apply the strategy selected in Phase 0.

### Strategy A: Backward Chaining (Procedural Docs)

1. Identify the document's single primary goal (what must the reader be able to DO after reading?)
2. Work backward: what must the reader know to achieve the goal? What must they know to understand each prerequisite?
3. Continue until reaching atomic, self-evident concepts.
4. Reverse into forward execution order.
5. Place each semantic unit at its natural point in the reconstructed flow.

**Editorial structure layer (mandatory)**: After ordering by backward chain, apply a second pass for editorial
grouping. Sections labeled "Implementation Notes", "CRITICAL", "REMINDER", and "Validation Context" often contain
DISTINCT constraints not captured by execution order alone. These must be preserved as named subsections, not
dissolved into the main flow.

**Warning**: Backward chaining produces correct execution ORDER but is incomplete for editorial GROUPING. A
requirement appearing only in "Implementation Notes" is not redundant — it serves a different reader need
(reference lookup vs. sequential execution). Dropping these sections is the primary failure mode for procedural
documents (Wave 1: 6/56 units lost).

### Strategy B: Topical Clustering (Reference Docs)

1. Identify the distinct lookup goals the document serves (e.g., "select a commit type", "plan a squash",
   "find a past commit").
2. Group all semantic units by topic cluster, independent of their original section order.
3. Within each cluster, order by specificity: general principles → specific rules → examples → exceptions.
4. Move cross-cutting principles (format rules, naming conventions) to a dedicated section at the top.

**Why backward chaining fails for reference docs**: Reference docs serve MULTIPLE lookup goals simultaneously.
Backward chaining from any single goal omits sections that serve other goals (Wave 3 finding: the "discovery
procedures" branch would be dropped if backward-chaining from "select correct commit type").

---

## Phase 3: Verification (Binary Equivalence Gate)

Validate the consolidated document against the original using the semantic extraction algorithm from `../instruction-builder-agent/validation-protocol.md`. The binary verdict MUST be EQUIVALENT.

- **EQUIVALENT**: Consolidation preserved all semantic units. Apply changes.
- **NOT_EQUIVALENT**: Consolidation lost content. Do NOT apply. Examine the LOST section to identify which units
  were dropped and why. Revise the reconstruction to restore them, then re-verify.

**Hard rule**: A NOT_EQUIVALENT verdict means the consolidation FAILED. There is no "close enough." The binary gate
is non-negotiable. If repeated revision cycles cannot achieve EQUIVALENT, document the failure mode and return the
original unchanged.

For multi-file consolidation: validate against EACH original file independently using the validation protocol. All files must return
EQUIVALENT.

---

## Phase 4: Quality Verification (SPRT Compliance Gate)

After Phase 3 returns EQUIVALENT and changes are applied, verify that the reorganized document produces compliant
agent behavior using the SPRT-based instruction-test from `../instruction-builder-agent/validation-protocol.md`.

**When to run Phase 4:**
- Only when the reorganized document is an instruction file (skill first-use.md, agent instructions)
- Skip Phase 4 for reference/lookup documents (commit type tables, configuration option listings) — these do not
  produce agent behavior and SPRT instruction-testing does not apply

**SPRT parameters:**
- H₀ (null hypothesis): compliance rate ≥ 0.95
- H₁ (alternative hypothesis): compliance rate ≤ 0.85
- α (false positive rate): 0.05
- β (false negative rate): 0.05

**Acceptance criterion:** ALL test cases must reach SPRT Accept (log_ratio ≥ A, where A ≈ 2.944).

**Procedure:**

1. Run the SPRT instruction-test per the protocol in `../instruction-builder-agent/validation-protocol.md` (Section 1),
   using the same test cases used before reorganization (or generate new ones if none exist).
2. If ALL test cases Accept → reorganization is complete. Commit the reorganized file.
3. If ANY test case Rejects → the reorganization altered observable behavior despite passing Phase 3. Revert to
   the original, examine which assertions failed, identify what semantic change caused the failure, and revise
   the reconstruction.

**Relationship to Phase 3:** Phase 3 (Binary Equivalence Gate) checks that all semantic units are preserved.
Phase 4 checks that those semantic units produce the intended agent behavior when followed. Both gates are required:
a document can pass Phase 3 (no units lost) but still fail Phase 4 if the reorganization changes the emphasis or
ordering such that agents misinterpret the instructions.

---

## Failure Mode Catalog

Evidence from Waves 1–3.

### FM-1: Editorial Sections Removed (Wave 1)

**Trigger**: Consolidation by execution order dissolves sections labeled "Implementation Notes", "CRITICAL REMINDER",
"Validation Context", or similar.

**Result**: Requirements unique to those sections are lost. Wave 1 lost 6/56 units (11%) for this reason.

**Prevention**: Apply the editorial structure layer in Phase 2 Strategy A. Treat named sections as semantic
containers, not just organizational convenience.

### FM-2: Architectural Encapsulation Violated (Wave 2)

**Trigger**: Consolidating an orchestrator + subagent pair where the separation is intentional.

**Result**: Internals exposed to the caller, defeating encapsulation. The subagent's implementation details become
visible to the orchestrator, which may then bypass the skill. Wave 2 found this by identifying the architectural
boundary during backward chaining.

**Prevention**: Apply the Architectural Boundary Check hard gate in Phase 0. Never consolidate files with explicit
audience restrictions or "intentionally not here" language.

### FM-3: Volume-Induced Loss (Wave 2)

**Trigger**: Large combined unit count (101+ units) creates consolidation pressure that drops individual precision
requirements.

**Result**: Specific requirements squeezed out by volume. Wave 2 lost the "report ACTUAL score, not summary"
requirement from File A when condensing the combined 101-unit set.

**Prevention**: Limit single consolidation runs to one file at a time unless files are truly same-audience,
same-topic. The binary gate will catch this, but prevention reduces rework.

### FM-4: Cross-File References Made Dangling (Wave 2)

**Trigger**: Inlining the content of a referenced file eliminates the reference that pointed to it, leaving the
consolidated document inconsistent.

**Result**: Either the reference persists (pointing to a file that no longer exists) or the reference is removed
(losing an architectural boundary marker).

**Prevention**: Preserve cross-file references as semantic units. Do not inline referenced file content unless the
referenced file is also being removed.

### FM-5: Backward Chaining Incomplete for Multi-Goal Docs (Wave 3)

**Trigger**: Applying backward chaining to a reference document that serves multiple lookup goals.

**Result**: Sections that serve goals other than the primary backward-chain goal are dropped as "not required."
Wave 3 analysis: the "discovery procedures" branch (u59–u67) would have been lost if backward-chaining from
"select correct commit type."

**Prevention**: Classify the document in Phase 0. Reference docs must use topical clustering, not backward chaining.

---

## Validation Evidence Summary

| Wave | Document | Type | Strategy | Units | Lost | Verdict |
|------|----------|------|----------|-------|------|---------|
| 1 | instruction-builder-agent/first-use.md | Procedural | Backward chaining | 56 | 6 | NOT_EQUIVALENT |
| 2 | instruction-builder-agent/first-use.md (File A) | Procedural orchestrator | Backward chaining | 59 | 3 | NOT_EQUIVALENT |
| 2 | instruction-builder-agent/compression-protocol.md (File B) | Procedural subagent | Backward chaining | 42 | 0 | EQUIVALENT |
| 3 | commit-types.md | Reference | Topical clustering | 67 | 0 | EQUIVALENT |

**Overall pipeline status**: Partially validated. Reference docs consolidate cleanly with topical clustering.
Procedural docs require the editorial structure layer to achieve EQUIVALENT. Architectural boundary detection
prevents the most harmful consolidation failures.

---

## Wave 1 Validation: Single-File Document Consolidation

### Document Selected

**Target**: `plugin/skills/instruction-builder-agent/first-use.md` (582 lines)

**Rationale**: Large skill first-use.md with identified scattered content:
- "CRITICAL: Always Use This Skill" section duplicates what Step 6 (Iteration) handles procedurally
- "MANDATORY: Validation Gate" subsection within Step 4 duplicates Step 5 (Decision Logic) and contains procedural decision rules
- "Implementation Notes" section duplicates tool specifications and iteration state management already covered in Steps 2-4

Expected consolidation: Move scattered validation/iteration logic into cohesive execution flow
by eliminating redundant sections and integrating their unique content into the main workflow
steps.

---

### Phase 1: Extraction Results

Extracted **56 semantic units** from original document, categorized as:

| Category | Count | Examples |
|----------|-------|----------|
| REQUIREMENT | 18 | Main agent only, report validation status, spawn parallel subagents |
| PROHIBITION | 8 | Never manually compress, cannot invoke from subagent |
| SEQUENCE | 10 | Check baseline, save original, validate, iterate |
| CONDITIONAL | 8 | If EQUIVALENT then approve, if NOT_EQUIVALENT then iterate |
| CONSEQUENCE | 2 | Manual compression bypasses iteration loop |
| DEPENDENCY | 3 | Baseline prerequisite for subsequent validations |
| REFERENCE | 4 | Links to compression-protocol.md, validation-protocol.md |
| CONJUNCTION | 2 | All requirements for validation gate |
| EXCLUSION | 1 | Mutual exclusivity in handling EQUIVALENT vs NOT_EQUIVALENT |

**Key Scattered Units Identified**:
- "Validation Context" requirement (explain original vs compressed comparison)
- "REUSE baseline on subsequent invocations" requirement
- "Exact decision logic algorithm" (if Status contains EQUIVALENT...)
- "Do not ask user for approval if DECISION=ITERATE" requirement
- "File Operations detail" (which tool for each operation)
- "Rollback capability" explanation

---

### Phase 2: Backward Chaining Reconstruction

**Document Goal**: Provide an orchestration workflow for compressing documentation while
maintaining semantic equivalence through validation.

**Backward Chain Analysis**:

```
GOAL: Complete compression with validation and optional iteration
  ←  Report results to user
      ←  Reach EQUIVALENT status (final gate)
          ←  If NOT_EQUIVALENT: iterate up to 3 times
          ←  If EQUIVALENT: apply changes, ask user for more iterations
              ←  Validate each version
                  ←  Have baseline and versioned compressions saved
                      ←  Invoke compression subagent
                          ←  Know document type is Claude-facing
                              ←  Precondition: Verify document type
                              ←  Precondition: Check baseline exists/save it
  Meta-constraint: Main agent only (guarding entire flow)
  Meta-constraint: Never bypass the skill (guarding manual compression)
```

**Reconstructed Execution Order**:

1. **Meta Guards** (u1, u2, u3, u4): Main agent only, skill is mandatory, report requirements
2. **Step 1: Document Type Validation** (u5-u10): Verify Claude-facing, handle special cases
3. **Step 2: Baseline Check** (u11, u12): Check or save baseline
4. **Step 3: Invoke Compression** (u13-u14, u41, u45): Subagent invocation, agent type, algorithm
5. **Step 4: Save & Validate** (u15-u23, u42, u43): Save baseline, version, then validate
6. **Step 5: Decision Logic** (u24-u31, **u51-u54**): Apply validation gate, decide APPROVE vs ITERATE
   - **Missing from consolidated**: Validation Context explanation, exact decision logic algorithm,
     "don't ask user if ITERATE" requirement
7. **Step 6: Iteration Loop** (u32-u37, u49-u50): Re-invoke with feedback, self-check, max 3 attempts
8. **Step 7: Multiple Files** (u38-u40): Batch processing via parallel subagents
9. **Supporting Details**: References (u44-u46), **u55-u56 missing** (File Operations, Rollback)

---

### Phase 3: Consolidation & Verification

**Consolidation Strategy**: Eliminated "Implementation Notes" and integrated its content
into the step-by-step workflow. Removed the "MANDATORY: Validation Gate" subsection from
Step 4 and merged its logic into Step 5 as explicit conditional branches.

**Result**: Created consolidated version (499 lines, 14% reduction)

---

### Binary Validation Verdict

**Status: NOT_EQUIVALENT (50/56 preserved, 6 lost)**

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: NOT_EQUIVALENT (50/56 preserved, 6 lost)

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in consolidated)
───────────────────────────────────────────────────────────────────────────────
- [REQUIREMENT] When reporting to the user, explicitly state: Status:
  {EQUIVALENT|NOT_EQUIVALENT} compares the consolidated version against the
  ORIGINAL document state from before consolidation was invoked.
- [REQUIREMENT] On second, third, etc. invocations: Always compare against original baseline
  (not intermediate versions); The baseline is set ONCE on first invocation and REUSED for all
  subsequent invocations
- [CONDITIONAL] if Status contains "EQUIVALENT" and NOT "NOT_EQUIVALENT":
  DECISION = APPROVE; else: DECISION = ITERATE
- [SEQUENCE] If DECISION=ITERATE: STOP - do not ask user for approval; Extract the
  LOST section from the report; Proceed directly to Step 6 (Iteration Loop) with
  that feedback
- [REQUIREMENT] File Operations: Read original; Save original baseline (once per session);
  Save versioned consolidated output; Overwrite original (only after approval); Cleanup after approval
- [REQUIREMENT] If latest version unsatisfactory, previous versions available for rollback;
  Versions automatically cleaned up after successful approval

───────────────────────────────────────────────────────────────────────────────
ADDED (in consolidated, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

---

## Wave 1 Key Findings

### What Worked

1. **Backward chaining successfully reconstructed execution flow**: Identifying the goal
   (achieve EQUIVALENT status) and working backward led to a natural sequence: document
   type check → baseline → compress → validate → decide → iterate.

2. **Semantic extraction identified non-obvious duplicates**: The "CRITICAL: Always Use
   This Skill" guard and "MANDATORY: Validation Gate" decision rule were in different
   sections but expressed the same semantic constraints. Extraction revealed this.

3. **Lost units trace to specific sections**: The 6 lost units all came from three
   sections I identified as "implementation detail":
   - "Validation Context" subsection
   - "CRITICAL REMINDER" about baseline reuse
   - "Implementation Notes" (File Operations and Rollback)

### What Failed

1. **"Redundant" sections contained non-duplicated content**: My assumption that
   "Implementation Notes" was purely procedural detail was wrong. It contained:
   - Explicit tool specifications (Read, Write, rm) missing from steps
   - Rollback capability explanation (informational but important)
   - Exact decision logic algorithm with string matching rule

2. **Consolidation lost precision**: The consolidated version has "If status = EQUIVALENT:
   APPROVE" but the original has detailed logic: "if Status contains 'EQUIVALENT' and NOT
   'NOT_EQUIVALENT': DECISION = APPROVE". The string matching rule is semantically distinct.

3. **Human intent signals mattered more than duplicates**: Sections labeled "Implementation
   Notes" and "MANDATORY: Validation Gate" seemed like elaborations, but they contained
   DISTINCT execution constraints not captured elsewhere.

### Key Observations

1. **Scattered content != redundant content**: Just because information appears in multiple
   sections doesn't mean it's duplicated. The "Validation Context" requirement appears
   ONLY in Step 4, not in Step 5. The "File Operations" detail appears ONLY in
   "Implementation Notes".

2. **Backward chaining revealed execution order but not completeness**: The reconstructed
   flow correctly identified WHICH steps must happen in sequence, but consolidating by
   execution order ALONE lost content that was intentionally separated for different
   purposes:
   - Decision logic (Step 5): What to do with validation result
   - Implementation notes: HOW to actually do it (which tools, what to clean up)
   - Guards: When NOT to do it (subagent restriction, manual compression prohibition)

3. **Consolidation destroyed editorial structure**: The original organizes by concern
   (Guard → Procedure → Decision → Iteration → Details). Consolidation by execution order
   buried procedural details inside steps, making them harder to find.

4. **Compare-docs verdict is binary**: NOT_EQUIVALENT was correct. The consolidated version
   CANNOT preserve semantic equivalence because it actually lost execution-relevant
   constraints.

---

## Limitations Discovered

### Approach Limitations

1. **Backward chaining works for procedural content**: The compression workflow is a procedural skill
   (step-by-step workflow with decision gates). Backward chaining naturally reconstructs
   the execution sequence.

2. **Backward chaining does NOT work for editorial structure**: Sections like "Implementation
   Notes" and "Validation Context" serve editorial purposes (grouping related details,
   highlighting critical warnings) distinct from execution order. Backward chaining ignores
   these groupings.

3. **"Scattered" vs "Redundant" not determinable by positioning**: A requirement appearing
   in "Implementation Notes" is not redundant just because there's also a step-by-step
   section. It serves a different reader need.

### Document Type Limitations

**Observation**: Shrink-doc is a **procedural/instructional skill** with clear goal-driven
execution order. This is ideal for backward chaining.

**Prediction for non-procedural content**:
- **Reference documentation** (configuration tables, glossaries): Backward chaining would
  fail because there's no single goal or prerequisite chain. Topical clustering would be
  better.
- **Conceptual documentation** (design rationale, architecture): Backward chaining might work
  if concepts have clear prerequisites, but concepts are often bidirectional (A depends on
  understanding B, and vice versa).
- **Mixed content** (procedures + reference): Would require identifying which sections are
  procedural and applying different reconstruction strategies to each.

---

## Next Steps for Waves 2-4

### Wave 2 Recommendation

**Test on multi-file document**: Select a skill + its first-use.md that have overlapping
content (e.g., `instruction-builder-agent/SKILL.md` and `instruction-builder-agent/first-use.md`).
This will test whether cross-file extraction and merging preserves inter-document context.

**Expected risk**: The consolidated version might inadvertently merge distinct purposes
(what a skill DOES vs HOW TO USE IT) into a single unified flow, losing the separation
that made each file valuable independently.

### Wave 3 Recommendation

**Test on non-procedural content**: Select a reference document like
`plugin/concepts/doc-consolidation.md` (when created). If a reference document exists
without clear execution prerequisites, test whether backward chaining produces coherent
output or requires a different approach.

### Wave 4 Implementation

Based on these findings:
- **Consolidation skill**: Implement the three-phase pipeline with explicit handling for
  procedural vs non-procedural content
- **Scope limitation**: Mark consolidation as recommended for procedural documents only,
  with warning for conceptual/reference documents
- **Validation requirement**: Make validation-protocol EQUIVALENT status a hard gate (as originally
  planned)
- **Editorial structure preservation**: Consider optional "editorial clustering" phase before
  consolidation to group related content intentionally

---

## Evidence Summary

| Metric | Value | Assessment |
|--------|-------|-----------|
| Lines reduced | 582 → 499 (14%) | Modest compression |
| Units extracted | 56 | Comprehensive coverage |
| Units preserved | 50/56 | 89% (NOT_EQUIVALENT) |
| Binary verdict | NOT_EQUIVALENT | Consolidation FAILED validation |
| Lost categories | REQUIREMENT (3), SEQUENCE (1), CONDITIONAL (1), REFERENCE (0) | Loss concentrated in requirements & sequences |
| Cause analysis | Editorial sections removed | Human-intent structure ignored |

---

## Conclusion

**Wave 1 Status**: NOT_EQUIVALENT - consolidation approach FAILED this validation round

The three-phase pipeline (Extract → Backward Chain → Verify) is **operationally sound** but
revealed that **"scattered documentation" requires distinguishing between**:
1. **Repeated information** (true redundancy - can consolidate)
2. **Separated-for-editorial-reasons information** (distinct purposes - should NOT consolidate)
3. **Execution-prerequisite information** (found via backward chaining - properly ordered)

For procedural documents like instruction-builder, the approach works IF the consolidation also
respects editorial concerns alongside execution order. Simply following backward chaining
order loses content that was intentionally grouped separately.

Future waves should test whether this limitation is specific to procedural documents or
applies more broadly.

---

## Wave 2 Validation: Multi-File Document Consolidation

### Documents Selected

**Candidate pair**: `plugin/skills/instruction-builder-agent/first-use.md` (orchestrator, 582 lines) and
`plugin/skills/instruction-builder-agent/compression-protocol.md` (compression subagent, 199 lines).

**Rationale**: Both describe document compression but serve DIFFERENT audiences:
- `first-use.md`: Main agent orchestrating the compression workflow
- `compression-protocol.md`: Compression subagent performing the actual compression

Both files share overlapping concepts (what to preserve, what to remove, CLAUDE.md special
handling) while containing audience-specific content (orchestrator workflow vs. subagent
execution). This is the ideal multi-file test: overlapping concepts with audience separation.

---

### Phase 1: Independent Extraction

**File A (first-use.md)**: Extracted **59 semantic units**

| Category | Count | Examples |
|----------|-------|---------|
| REQUIREMENT | 28 | Report status, commit gate, EQUIVALENT required, file operations |
| PROHIBITION | 5 | Never compress manually, no manual batch Tasks, subagents not validate own work |
| SEQUENCE | 10 | Type check → baseline → compress → version → validate → decide → iterate |
| CONDITIONAL | 8 | If EQUIVALENT: APPROVE; if NOT_EQUIVALENT: ITERATE; if YAML missing: warn |
| CONSEQUENCE | 2 | Manual compression bypasses iteration loop |
| REASON | 2 | Historical context (2025-12-19), encapsulation rationale |
| REFERENCE | 2 | compression-protocol.md (algorithm), delegate skill |
| PRINCIPLE | 2 | No "close enough", report actual score |

**File B (compression-protocol.md)**: Extracted **42 semantic units**

| Category | Count | Examples |
|----------|-------|---------|
| REQUIREMENT | 31 | Preserve YAML, preserve relationships, normalize temporal ordering, write file |
| PROHIBITION | 3 | Do not delete ### blocks, do not reduce clarity, do not remove detection patterns |
| SEQUENCE | 3 | Read → compress → write; CLAUDE.md steps 1-4 |
| CONDITIONAL | 3 | Apply clarity principle when 3 conditions; vague quantifiers; optional conditionals |
| PRINCIPLE | 2 | Compress tokens not constraints; explicit constraints → MORE explicit in compressed |

---

### Phase 2: Merge Analysis

**Overlapping content between files** (same concept expressed in both):

| Concept | In first-use.md | In compression-protocol.md |
|---------|-----------------|------------------------|
| What to preserve | Step 1 special handling references | Full preserve list (B9-B14) |
| What to remove | Implied by "subagent handles it" | Full remove list (B15-B19) |
| CLAUDE.md special | Step 1: reorganization algorithm in compression-protocol.md | Full algorithm (B35-B39) |
| Style docs special | Step 1: follow rules in compression-protocol.md | Full rules (B40-B42) |
| Execution equivalence | Goal statement | Definition (B8) |

**Key structural observation**: The overlap is NOT redundant. `first-use.md` says "compression-protocol.md
has the algorithm" — it uses cross-references as encapsulation boundaries. The overlapping concepts
are at DIFFERENT abstraction levels:
- Orchestrator: knows WHAT exists (references)
- Subagent: knows HOW to do it (details)

This is **architectural encapsulation**, not accidental duplication.

---

### Phase 3: Backward Chaining on Merged Set

**Attempted goal**: "Reader can compress a document with validation"

**Backward chain result**:

```
GOAL: Document compressed with EQUIVALENT validation
  ← Orchestrator: iterate if NOT_EQUIVALENT (A40-A45)
  ← Orchestrator: run validation via validation-protocol (A24-A27)
    ← Orchestrator: save baseline + versioned compression (A18-A22)
      ← Subagent: write compressed file (B33-B34)
        ← Subagent: apply normalization (B25-B30)
          ← Subagent: compress while preserving (B9-B24)
            ← Subagent: know what equivalence means (B8)
  ← Orchestrator: multiple files via delegate (A46-A49)
  ← Orchestrator: validate document type (A6-A14)
  Meta: orchestrator only (A1), never manual (A3)
  Meta-subagent: write file (B34), improve clarity (B5)
```

**Critical observation**: The backward chain produces TWO separate chains that are SEQUENTIAL,
not parallel. The orchestrator chain invokes the subagent chain — they cannot be merged into
a single unified flow without destroying the invocation boundary.

---

### Phase 4: Consolidated Version

**Consolidation attempt**: Combined into Part 1 (orchestrator) + Part 2 (subagent) in temporary file.

---

### Phase 5: Compare-Docs Verification (Manual)

**Compare against first-use.md (59 units)**:

Lost units (3/59):
- [REASON] Historical context: "session from 2025-12-19 had documentation update remove
  intentionally-added style rule section" — editorial provenance, removed as meta-commentary
- [REASON] Encapsulation rationale: "If you can see HOW to compress, you might bypass the
  skill and do it manually" — removed as elaboration
- [REQUIREMENT] "Report the ACTUAL score from validation. Do not summarize or interpret" —
  dropped when condensing the version comparison table section

**Status: NOT_EQUIVALENT (56/59 preserved, 3 lost)**

The third lost unit (do not summarize validation score) IS execution-critical: an agent
that summarizes rather than reports verbatim produces different outcomes.

**Compare against compression-protocol.md (42 units)**:

All 42 units preserved.

**Status: EQUIVALENT (42/42 preserved)**

---

### Wave 2 Key Findings

#### What Worked

1. **Extraction correctly identified the audience boundary**: The merged extraction set clearly
   split into orchestrator units (A) and subagent units (B). Backward chaining naturally kept
   them separate.

2. **Cross-file references are NOT redundant**: `first-use.md` referencing compression-protocol.md
   is architectural encapsulation. The reference itself is a semantic unit (A16, A9, A10) that
   must be preserved, not merged with the referenced content.

3. **Subagent content consolidated cleanly (42/42)**: compression-protocol.md content has no
   scattered structure — it follows a clear hierarchy. Full preservation achieved for File B.

#### What Failed

1. **Audience-specific content cannot be consolidated without loss**: The 3 lost units from
   first-use.md were all dropped during condensation caused by the combined volume of merged
   content. When two large files are merged, individual requirements get crowded out.

2. **Volume-induced loss**: With 59+42=101 units to fit into one document, consolidation
   pressure dropped a specific precision requirement (A31: report ACTUAL score, not summary). This didn't happen in Wave 1 with one 56-unit file.

3. **Structural encapsulation creates a consolidation paradox**: The files exist as separate
   documents BECAUSE the orchestrator should NOT know compression internals (prevents manual
   bypass). Consolidating them into one file exposes the internals to the orchestrator,
   defeating the architectural design.

---

### Multi-File Failure Modes Discovered

**Failure Mode 1: Architectural Encapsulation Boundaries**
- Files separated by design to hide implementation details from callers
- Consolidation would expose internals the caller is NOT supposed to see
- Detection: file has `**INTERNAL DOCUMENT**` or "Do NOT read if you are X, use Y instead"
- Rule: Do NOT consolidate files with explicit audience separation

**Failure Mode 2: Volume-Induced Loss**
- More units → more consolidation pressure → individual requirements dropped
- Wave 1 (56 units): 6 lost. Wave 2 (101 units combined): 3 lost from File A, 0 from File B
- The orchestrator's file lost precision specifically where it intersected with condensed content
- Rule: Each additional file increases loss risk quadratically, not linearly

**Failure Mode 3: Reference-as-Architecture**
- Cross-file references (A16: "subagent reads compression-protocol.md") are semantic units
- Consolidating the referenced content makes the reference dangling/obsolete
- Merged document must choose: keep reference OR inline content, not both
- Rule: Verify cross-file references remain valid after consolidation

**Failure Mode 4: Implicit Role Separation**
- Even without explicit "INTERNAL" markers, files may have implicit role separation
- first-use.md: "Why separate documents: the compression algorithm is intentionally NOT in
  this file. If you can see HOW to compress, you might bypass the skill."
- This is a design invariant, not just editorial preference
- Rule: Check for sentences like "X is intentionally not here" as consolidation blockers

---

### Comparison: Wave 1 vs Wave 2

| Metric | Wave 1 (Single-file) | Wave 2 (Multi-file) |
|--------|---------------------|---------------------|
| Files | 1 | 2 |
| Total units | 56 | 101 (59+42) |
| Units lost | 6 (all from editorial sections) | 3 (from File A only, 0 from File B) |
| Binary verdict File A | NOT_EQUIVALENT | NOT_EQUIVALENT |
| Binary verdict File B | N/A | EQUIVALENT |
| Loss type | Editorial structure removed | Volume-induced + architectural violation |
| Root cause | Assumed "Implementation Notes" was redundant | Assumed overlap meant consolidation was valid |

---

### Wave 2 Conclusion

**Wave 2 Status**: NOT_EQUIVALENT for File A (first-use.md), EQUIVALENT for File B (compression-protocol.md)

The three-phase pipeline correctly identified the audience boundary during extraction and
backward chaining — but the consolidation step STILL lost 3 units from File A due to
volume-induced compression pressure.

**New rule discovered**: Files with architectural encapsulation (audience separation,
explicit boundary markers, intentional structural separation) MUST NOT be consolidated. The separation
is a design invariant, not a documentation defect to be fixed.

**When multi-file consolidation CAN work**: Two files covering the same topic for the same
audience (e.g., two concept docs both explaining the same domain to the same reader type),
where there's genuine redundancy rather than role separation.

---

## Wave 3 Validation: Reference/Non-Procedural Document Consolidation

### Document Selected

**Target**: `plugin/concepts/commit-types.md` (198 lines)

**Rationale**: Primarily a reference document with 9 tables covering commit type rules,
file-location-to-type mappings, squash categories, and convention routing. Contains minimal
procedural content (5-step workflow for convention updates). Ideal for testing whether backward
chaining produces meaningful results for non-procedural content.

---

### Phase 1: Extraction Results

Extracted **67 semantic units** from original document, categorized as:

| Category | Count | Examples |
|----------|-------|----------|
| MAPPING | 26 | File locations → commit types, change types → override types, resolution → search method |
| RULE | 14 | index.json same commit, test: only standalone, commits tracked via file history |
| PRINCIPLE | 4 | Commit outcomes not process, WHAT changed not WHERE, default overridable |
| REQUIREMENT | 1 | Use ONLY standard types |
| PROHIBITION | 1 | NOT VALID: feat, fix, chore, build, ci, perf |
| SEQUENCE | 1 | 5-step workflow for convention updates |
| FORMAT | 1 | `{type}: {description}` |
| CATEGORY | 2 | Implementation squash, infrastructure squash |
| EXAMPLE-CORRECT | 2 | ONE commit (feature+index.json), single commit (bugfix+test) |
| EXAMPLE-WRONG | 2 | index.json in separate commit, separate test+bugfix commits |
| PROCEDURE | 3 | git log commands to find commits by issue |
| REASON | 1 | Why index.json file history tracking works |
| REFERENCE | 1 | See issue-resolution.md for resolution handling |

**Key Observation**: The dominant category is MAPPING (26/67 = 39%) — lookup tables with no
prerequisite ordering. This is the signature of reference documentation. Compare to Wave 1's
procedural doc where SEQUENCE and CONDITIONAL dominated.

---

### Phase 2: Backward Chaining Analysis for Reference Content

**Document Goal**: "Agent can correctly select a commit type for any given change."

**Backward chain result**:

```
GOAL: Select correct commit type
  ← Know branch routing for convention updates (u49-u54)
  ← Know squash category assignment (u38-u48)
    ← Know test/bugfix bundling rule (u55-u58)
  ← Apply override rules: WHAT changed > WHERE (u25-u29)
    ← Look up file location default (u18-u24, u30-u34)
      ← Know valid type names (u8-u17, u37)
      ← Know format requirement (u36)
  ← Know what to commit and when (u1-u7)
  ← Know how to find commits after the fact (u59-u67)
```

**Critical Finding**: The backward chain for this reference document produces a PARALLEL lookup
tree, not a linear prerequisite chain. Multiple branches can be traversed independently:
- Type selection branch (u8-u35): consulted when choosing a type
- Squash branch (u38-u58): consulted at merge time
- Discovery branch (u59-u67): consulted after the fact

Unlike procedural docs (Wave 1), there is no single goal that requires all elements in strict
sequence. The agent consults the document as a lookup table — different scenarios access
different branches. Backward chaining from "select correct commit type" would OMIT the discovery
branch (u59-u67) because finding past commits is NOT a prerequisite for selecting a type.

**Consequence**: Backward chaining reconstruction would be incomplete for this document —
it would drop the discovery branch as "not required" for the main goal.

---

### Phase 3: Consolidation & Verification

**Consolidation Strategy**: Applied TOPICAL CLUSTERING instead of backward chaining:
1. Group all type-selection content together (principles + table + overrides)
2. Group when-to-commit rules separately
3. Group squash/bundling rules together
4. Group discovery procedures together
5. Move Format section to top for quick reference
6. Move CRITICAL principle from "Standard Types" section to "Core Principle" where it belongs

**Result**: Created `/tmp/consolidated-commit-types-wave3.md` (211 lines — slightly longer due
to horizontal rule separators added for clarity, NOT because content was added)

**Compare-Docs Binary Verdict (Manual)**: **EQUIVALENT (67/67 preserved, 0 lost)**

```
═══════════════════════════════════════════════════════════════════════════════
                              COMPARISON RESULT
═══════════════════════════════════════════════════════════════════════════════

Status: EQUIVALENT (67/67 preserved, 0 lost)

───────────────────────────────────────────────────────────────────────────────
LOST (in original, missing in consolidated)
───────────────────────────────────────────────────────────────────────────────
- (none)

───────────────────────────────────────────────────────────────────────────────
ADDED (in consolidated, not in original)
───────────────────────────────────────────────────────────────────────────────
- (none)

═══════════════════════════════════════════════════════════════════════════════
```

---

### Wave 3 Key Findings

#### Why Reference Docs Behaved Differently

1. **No scattered content to find**: The original `commit-types.md` had NO genuine redundancy.
   The "CRITICAL" principle statement in "Standard Types" section was thematically misplaced
   but not duplicated elsewhere. Moving it to "Core Principle" was editorial cleanup, not
   consolidation of redundancy.

2. **Backward chaining fails for multi-goal documents**: Reference docs serve MULTIPLE lookup
   goals simultaneously (type selection, squash planning, commit discovery). Backward chaining
   from any single goal would drop sections that serve other lookup goals. The discovery
   procedures (u59-u67) would be lost if backward-chaining from "select correct type."

3. **Topical clustering succeeds where backward chaining would fail**: Grouping by topic
   (what-to-commit rules, type-selection rules, squash rules, discovery procedures) preserves
   all content because it respects the multi-goal structure of reference documentation.

4. **Reference docs are already "consolidated"**: Well-maintained reference docs don't have
   scattered content because they're organized by lookup key, not execution order. The
   consolidation produced an EQUIVALENT result precisely because there was nothing to consolidate.

#### What This Means for the Pipeline

The three-phase pipeline (Extract → Backward Chain → Verify) needs a decision gate BEFORE
Phase 2:

**Document type assessment** (before backward chaining):
- Is the dominant unit category SEQUENCE/CONDITIONAL? → Procedural → use backward chaining
- Is the dominant unit category MAPPING/RULE? → Reference → use topical clustering
- Is it mixed? → Identify procedural sections and apply backward chaining only to those

---

### Comparison: Wave 1, Wave 2, Wave 3

| Metric | Wave 1 (Procedural) | Wave 2 (Multi-file) | Wave 3 (Reference) |
|--------|---------------------|---------------------|---------------------|
| Document type | Procedural skill | Procedural orchestrator + subagent | Reference tables |
| Units extracted | 56 | 101 (59+42) | 67 |
| Dominant category | SEQUENCE/CONDITIONAL | REQUIREMENT/SEQUENCE | MAPPING/RULE |
| Units lost | 6 | 3 (File A) + 0 (File B) | 0 |
| Binary verdict | NOT_EQUIVALENT | NOT_EQUIVALENT (File A) | EQUIVALENT |
| Backward chaining | Produces useful order | Reveals role boundary | Incomplete (misses multi-goal) |
| Better strategy | Editorial structure + backward chain | Do NOT consolidate (role separation) | Topical clustering |
| Root cause of loss | Editorial sections removed | Volume-induced + architectural violation | N/A (no loss) |

---

### Wave 3 Conclusions

**Wave 3 Status**: EQUIVALENT — consolidation SUCCEEDED for reference content using topical
clustering

**Core Finding**: The three-phase pipeline requires a **content-type classification step** before
applying backward chaining:

| Content Type | Backward Chaining Result | Recommended Strategy |
|--------------|--------------------------|---------------------|
| Procedural/instructional | Partially useful (misses editorial sections) | Backward chain + editorial layer |
| Multi-file with role separation | Reveals boundary (do NOT consolidate) | Keep files separate |
| Reference/lookup tables | Incomplete (misses multi-goal sections) | Topical clustering |
| Mixed content | Depends on section type | Classify sections, apply per-section strategy |

**EQUIVALENT verdict achieved** for the first time across three waves — but only by abandoning
backward chaining in favor of topical clustering for reference content.

**Pipeline Recommendation for Wave 4**:
1. Add document type classification step (Phase 0) before extraction
2. Route to backward chaining (procedural) OR topical clustering (reference) based on classification
3. For mixed documents, identify section types and route each section to the appropriate strategy
4. Keep architectural encapsulation detection as a hard gate (from Wave 2) before any consolidation
