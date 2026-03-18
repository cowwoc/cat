# Plan: add-output-token-optimization-guidance

## Goal

Update `cat:optimize-execution` and `cat:instruction-builder` to surface output-token
optimization opportunities — both in post-session analysis reports and in guidance for
writing new skills. Correctness always takes priority over compactness — this includes both semantic correctness (meaning/parsing) and visual correctness (user-facing output alignment and readability).

## Scope

### cat:optimize-execution
Add a new analysis dimension to the existing optimization report: identify skill output
patterns that waste output tokens without adding value. Examples:
- Verbose section headings that repeat context already in scope
- Example blocks with identical leading spaces where a tab would suffice
- Repeated boilerplate that could be referenced rather than reproduced
- Unused output sections (e.g., examples nobody reads)

Report these as a "Token Efficiency" category in the Issues Found section, ordered by
estimated output-token savings.

### cat:instruction-builder
When creating or updating a skill, add a "compact output" pass after the draft is written:
- Prefer tabs over spaces in table alignment and indented lists where whitespace is not semantic
- Shorten verbose examples to the minimum needed to illustrate the point
- Remove output sections that produce content never referenced downstream
- Deduplicate repeated guidance that could be a single referenced rule

**Correctness exemptions** — compactness must NOT be applied when:
- Inside YAML frontmatter (whitespace is syntax)
- Inside Makefile targets (tabs are required, spaces are wrong)
- Inside fenced code blocks where indentation is part of the example
- In any context where changing whitespace would change meaning or break parsing
- In any user-facing output where compactness would degrade visual alignment or readability
- In display tables, boxes, or formatted reports where spacing is part of the visual design

## Requirements

- REQ-001: optimize-execution reports output-token waste as a named category in its Issues Found section
- REQ-002: optimize-execution estimates output-token savings for each flagged pattern
- REQ-003: instruction-builder applies a compact-output pass before finalizing a skill draft
- REQ-004: instruction-builder lists explicit correctness exemptions for semantic-whitespace contexts
- REQ-005: Both skills document that both semantic and visual correctness take priority over cost reduction

## Pre-conditions

- cat:optimize-execution and cat:instruction-builder exist and function correctly

## Post-conditions

- [ ] cat:optimize-execution first-use.md includes output-token waste analysis step and reporting
- [ ] cat:instruction-builder first-use.md includes compact-output pass with exemption rules
- [ ] Both skills explicitly state correctness-first priority
- [ ] No regressions in existing optimize-execution or instruction-builder behavior
- [ ] E2E: invoke cat:optimize-execution on a session and confirm "Token Efficiency" issues appear in the report
- [ ] E2E: invoke cat:instruction-builder on a skill and confirm compact-output recommendations are surfaced with exemptions listed

## Sub-Agent Waves

### Wave 1: E2E Acceptance Criteria Validation

**Item 1: Verify optimize-execution Token Efficiency reporting**

Run `/cat:optimize-execution` on a test session and validate that:
- A "Token Efficiency" category appears in the "Issues Found" section of the report
- Each flagged pattern includes: location (skill file + section), pattern type, sample text, estimated savings
- At least 3 of the 4 detection patterns are correctly identified: verbose headings, redundant boilerplate, unused sections, over-indented examples
- Estimated token savings are reasonable (5-200 token range, not outliers)
- No false positives: patterns flagged are genuinely wasteful and not exempt contexts

Steps:
1. Locate a recent session JSONL in `${CLAUDE_CONFIG_DIR}/projects/{project}/sessions/` with known skill invocations
2. Run `/cat:optimize-execution` with the session ID
3. Inspect the "Token Efficiency" section of the output report
4. Verify each flagged pattern meets the criteria above
5. If patterns are missing or incorrect, document which patterns were/weren't detected and why

**Item 2: Verify instruction-builder compact-output pass integration**

When instruction-builder creates a skill draft, validate that:
- Step 3 (Compact-Output Pass) is clearly documented and appears in the skill's workflow
- The pass lists all 5 correctness exemptions (YAML frontmatter, Makefile targets, fenced code blocks, semantic whitespace, visual alignment)
- A sample run of instruction-builder on a skill shows the pass being applied (or skipped if no savings)
- The final skill output does not violate any exemption rule (e.g., does not compress YAML frontmatter or Makefile tabs)

Steps:
1. Review plugin/skills/instruction-builder-agent/first-use.md to confirm Step 3 text
2. Invoke `/cat:instruction-builder` on a small test skill (or use a skill from the plugin)
3. Verify the compact-output pass runs and outputs "Compact-output pass reduced draft by ~{N}%" or similar
4. Check the final SKILL_DRAFT for violations of exemption rules (manual inspection of YAML, Makefile, code blocks)

### Wave 2: Exemption Rule Testing

**Item 3: Test compact-output pass exemption rules**

Create unit test scenarios to validate that the compact-output pass correctly exempts and does not modify:
1. YAML frontmatter (whitespace must be preserved exactly)
2. Makefile targets (tabs required, spaces forbidden)
3. Fenced code blocks (indentation part of example, preserved exactly)
4. Semantic whitespace contexts (where whitespace changes meaning)
5. Display tables, boxes, or formatted reports (visual alignment preserved)

For each exemption, create a test skill draft with a clear violation of the exemption and verify:
- The pass recognizes the exemption condition
- The pass does NOT apply compaction rules inside the exempted context
- The output is identical to the input for that context

Test artifacts (each is a mini skill draft with one exemption type):
- `test-skill-yaml.md` — YAML frontmatter with redundant spaces
- `test-skill-makefile.md` — Makefile target with spaces instead of tabs
- `test-skill-code-block.md` — Fenced code block with indentation as part of example
- `test-skill-semantic-ws.md` — Rule text where whitespace is semantic (e.g., nested list indentation)
- `test-skill-table.md` — Markdown table with alignment whitespace

For each test artifact, invoke the compact-output pass logic and assert:
- Line-by-line comparison shows no changes to exempted context
- Compaction rules ARE applied outside the exempted context (if present)

### Wave 3: False-Positive and Detection Gap Analysis

**Item 4: Validate Token Efficiency detection accuracy**

Test the 4 detection patterns for both true positives (correctly identified waste) and false positives (incorrectly flagged as waste):

**Verbose section headings:**
- True positive: heading "## Step 2: Configuration Analysis Steps" when parent context is already about configuration
- False positive: heading "## Step 2: Merge Conflict Resolution" in a skill about Git operations (different topic, not redundant)
- Test: create 5 skill excerpts with 3 true positives and 2 false positives, run Token Efficiency detection, verify 3/3 true positives flagged and 0/2 false positives flagged

**Redundant leading spaces in examples:**
- True positive: multiple code lines with identical 4-space indent that could collapse to 1 tab
- False positive: code block where indentation is intentional part of the example output
- Test: create 3 code blocks (1 redundantly indented, 1 intentionally indented, 1 mixed), run detection, verify 1/1 true positive flagged, 0/2 false positives

**Boilerplate repetition:**
- True positive: "YAML frontmatter must include `user-invocable: true`" repeated in Step 1 and Step 3
- False positive: similar but distinct guidance appearing in different contexts (e.g., "files must have headers" vs "module exports must have types")
- Test: create a skill with repeated boilerplate (50+ chars, 2+ occurrences), run detection, verify flagged and estimated savings is accurate

**Unused output sections:**
- True positive: a table that always produces zero rows because the condition never occurs
- False positive: a section with conditional content that sometimes produces no output but is still necessary
- Test: create a skill section with a genuinely unused table and one with conditional content, verify 1/2 flagged

Document any detection gaps (patterns not detected that should be) and false positives (patterns flagged that shouldn't be).
