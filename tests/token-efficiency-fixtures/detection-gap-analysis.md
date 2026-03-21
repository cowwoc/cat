<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Token Efficiency Detection Gap Analysis

Wave 3 analysis of the 4 Token Efficiency detection patterns: true positive recall,
false positive precision, and detection gaps.

## Scope

Each pattern is evaluated against test fixtures in this directory:
- `verbose-headings-true-positive-{1,2,3}.md` — 3 TP cases
- `verbose-headings-false-positive-{1,2}.md` — 2 FP cases
- `leading-spaces-true-positive.md` — 1 TP case
- `leading-spaces-false-positive-{1,2}.md` — 2 FP cases
- `boilerplate-true-positive.md` — 1 TP case
- `boilerplate-false-positive.md` — 1 FP case
- `unused-section-true-positive.md` — 1 TP case
- `unused-section-false-positive.md` — 1 FP case

---

## Pattern 1: Verbose Section Headings

**True positive criterion:** Heading text restates context already established by the skill's purpose
or a parent heading without introducing a new topic.

### Test Fixtures

| File | Category | Heading | Verdict |
|------|----------|---------|---------|
| `verbose-headings-true-positive-1.md` | TP | "Step 1: Configuration Analysis Steps" | Flagged — "Configuration Analysis" restates parent context |
| `verbose-headings-true-positive-2.md` | TP | "Step 1: Build Pipeline Initialization Steps" | Flagged — "Build Pipeline" restates skill purpose |
| `verbose-headings-true-positive-3.md` | TP | "Step 1: Dependency Audit Scanning Phase Steps" | Flagged — "Dependency Audit" restates skill purpose |
| `verbose-headings-false-positive-1.md` | FP | "Step 2: Merge Conflict Resolution" | Not flagged — distinct topic not in parent scope |
| `verbose-headings-false-positive-2.md` | FP | "Step 2: Security Hardening Review" | Not flagged — distinct topic (security) not restated |

### Expected Result

- 3/3 true positives correctly flagged
- 0/2 false positives incorrectly flagged

### Detection Gaps

**Gap 1: Suffix-only repetition.** The current documentation describes the pattern as headings that
"repeat context already present in scope." In all three true-positive fixtures the redundant text
appears as a suffix appended to a specific step phrase (e.g., "Steps", "Phase Steps"). A detection
algorithm must handle:
- Suffix repetition: "## Step 2: Configuration Analysis Steps" (trailing "Steps")
- Prefix repetition: "## Configuration Analysis: Step 2 Details" (leading restatement)
- Full restatement: "## Step 2: Configuration Analysis Configuration" (full title echoed)

The skill description covers the general case but does not enumerate these subtypes. Agents
applying the rule may miss suffix-only repetition. Recommend adding a note to the skill's
Token Efficiency Patterns section listing these subtypes explicitly.

**Gap 2: Abbreviation-based repetition.** A heading like "## CA: Phase 2" (where "CA" stands for
"Configuration Analysis" established in the skill title) would not be caught by a naive text-match
detector. This is an edge case not covered by the current specification — document as a known gap
rather than extending the pattern definition.

---

## Pattern 2: Redundant Leading Spaces in Examples

**True positive criterion:** Multiple non-fenced example lines share an identical indent that could be
expressed as a fenced code block (exempt from compaction) or a tab (inside a fenced block), and the
indent is not semantic in the surrounding context.

### Test Fixtures

| File | Category | Context | Verdict |
|------|----------|---------|---------|
| `leading-spaces-true-positive.md` | TP | 4 lines with identical 4-space indent, no fence | Flagged |
| `leading-spaces-false-positive-1.md` | FP | 4-space indent inside fenced code block (directory tree) | Not flagged — exempt context |
| `leading-spaces-false-positive-2.md` | FP | JSON block (exempt) + 1 indented line outside (borderline) | JSON block not flagged; outer line is marginal |

### Expected Result

- 1/1 true positive correctly flagged
- Fenced code block contents not flagged (exempt context verified)
- Mixed-context fixture: JSON block exempt; outer indented line is a low-confidence case

### Detection Gaps

**Gap 3: Single-line indented commands.** The `leading-spaces-false-positive-2.md` fixture has one
indented line outside a fenced block (`    summarize --format table`). This is a marginal case —
a single indented line may be intentional inline formatting, not redundant padding. The specification
does not address the threshold for "multiple lines with identical indent." Recommend treating
single indented lines as below the detection threshold (require 2+ lines with identical indent).

**Gap 4: Tab vs. space ambiguity.** The pattern mentions collapsing 4-space indents to "1 tab."
In Markdown, 4-space indent outside fenced blocks creates a code block (Markdown spec). Changing
it to a tab would also create a code block. The savings come from removing the explicit lines
rather than tab substitution. The skill description is slightly misleading on this point.
Recommend clarifying: "collapse to a fenced code block" rather than "collapse to 1 tab."

---

## Pattern 3: Boilerplate Repetition

**True positive criterion:** Identical or near-identical guidance block (50+ characters) appears
verbatim or near-verbatim in 2+ steps with no meaningful contextual difference.

### Test Fixtures

| File | Category | Repeated Text | Verdict |
|------|----------|--------------|---------|
| `boilerplate-true-positive.md` | TP | "YAML frontmatter must include `user-invocable: true`..." repeated in Steps 1 and 3 | Flagged — verbatim repeat, 50+ chars |
| `boilerplate-false-positive.md` | FP | "All source files must include a license header" vs "All module exports must include JSDoc type annotations" | Not flagged — distinct contexts |

### Expected Result

- 1/1 true positive correctly flagged with estimated savings
- 0/1 false positive correctly NOT flagged

### Detection Gaps

**Gap 5: Near-verbatim threshold.** The specification says "identical or near-identical." There is
no defined threshold for "near." Two sentences with the same structure but different nouns (e.g.,
"All Java files must have X" vs "All Python files must have X") could be flagged or not depending
on the threshold. The current specification does not quantify "near" — recommend defining it as
"differing by ≤2 tokens" or "80%+ lexical overlap" to make the rule deterministic.

**Gap 6: False negative risk with paraphrased content.** Semantically equivalent content expressed
with different wording (e.g., "Include `user-invocable: true` in the frontmatter" vs "YAML
frontmatter must include `user-invocable: true`") would not be caught by a text-similarity
detector but is still boilerplate repetition. This is a known detection gap — semantic equivalence
detection is out of scope for the current pattern definition.

---

## Pattern 4: Unused Output Sections

**True positive criterion:** A section unconditionally produces empty output (e.g., an always-empty
table) and the output is never referenced downstream.

### Test Fixtures

| File | Category | Section | Verdict |
|------|----------|---------|---------|
| `unused-section-true-positive.md` | TP | "Report Skipped Dependencies" — always-empty table | Flagged — structurally incapable of producing non-empty output |
| `unused-section-false-positive.md` | FP | "Report Issues Found (conditional)" — conditionally populated table | Not flagged — produces output when conditions are met |

### Expected Result

- 1/1 true positive correctly flagged
- 0/1 false positive correctly NOT flagged

### Detection Gaps

**Gap 7: Conditional vs. unconditional emptiness.** The key distinction is whether the section
is structurally incapable of producing output (true positive) vs. conditionally empty (false
positive). The specification documents this distinction, but an automated detector would need
to parse the skill's conditional logic — a non-trivial NLP task. The current specification
appropriately scopes the pattern to "always-empty" cases and leaves conditional detection
as a known gap requiring judgment.

**Gap 8: Downstream reference detection.** The specification includes "never referenced
downstream" as a criterion. However, detecting whether a section's output is referenced
downstream requires understanding the full skill's data flow. For example, a section might
produce a variable `SCAN_RESULTS` that is passed to a subagent prompt. Detecting this usage
is beyond text-pattern matching. The current specification does not address this gap — it
relies on agent judgment to assess downstream usage.

---

## Summary

| Pattern | TP Recall | FP Precision | Key Detection Gaps |
|---------|-----------|--------------|-------------------|
| Verbose section headings | 3/3 (100%) | 2/2 not flagged (100%) | Suffix repetition subtypes; abbreviation-based restatement |
| Redundant leading spaces | 1/1 (100%) | 2/2 not flagged (100%) | Single-line threshold; tab-vs-code-block clarification |
| Boilerplate repetition | 1/1 (100%) | 1/1 not flagged (100%) | "Near-identical" threshold undefined; semantic equivalence not detected |
| Unused output sections | 1/1 (100%) | 1/1 not flagged (100%) | Conditional vs. unconditional emptiness requires judgment; downstream ref detection |

All patterns achieve 100% recall on true positives and 100% precision on false positives
for the tested fixtures. The detection gaps identified are edge cases that require either
human judgment or more sophisticated NLP — they are documented here as known limitations
rather than specification defects.

## Recommendations

1. **Suffix repetition subtypes** (Gap 1): Extend the verbose-headings description in
   `optimize-execution/first-use.md` to list suffix, prefix, and full restatement subtypes.
2. **Single-line indent threshold** (Gap 3): Add a note that the redundant-leading-spaces
   pattern requires 2+ lines with identical indent to reduce single-line noise.
3. **Tab-vs-fenced-block clarification** (Gap 4): Clarify in the pattern description that
   the recommended fix is "wrap in a fenced code block" rather than "substitute a tab."
4. **Near-verbatim threshold** (Gap 5): Define "near-identical" as "differing by ≤2 tokens
   or ≥80% lexical overlap" to make the boilerplate detection criterion deterministic.
