# Plan: add-output-token-optimization-guidance

## Goal

Update `cat:optimize-execution` and `cat:instruction-builder` to surface output-token
optimization opportunities — both in post-session analysis reports and in guidance for
writing new skills. Correctness always takes priority over compactness.

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

## Requirements

- REQ-001: optimize-execution reports output-token waste as a named category in its Issues Found section
- REQ-002: optimize-execution estimates output-token savings for each flagged pattern
- REQ-003: instruction-builder applies a compact-output pass before finalizing a skill draft
- REQ-004: instruction-builder lists explicit correctness exemptions for semantic-whitespace contexts
- REQ-005: Both skills document that correctness takes priority over cost reduction

## Pre-conditions

- cat:optimize-execution and cat:instruction-builder exist and function correctly

## Post-conditions

- [ ] cat:optimize-execution first-use.md includes output-token waste analysis step and reporting
- [ ] cat:instruction-builder first-use.md includes compact-output pass with exemption rules
- [ ] Both skills explicitly state correctness-first priority
- [ ] No regressions in existing optimize-execution or instruction-builder behavior
- [ ] E2E: invoke cat:optimize-execution on a session and confirm "Token Efficiency" issues appear in the report
- [ ] E2E: invoke cat:instruction-builder on a skill and confirm compact-output recommendations are surfaced with exemptions listed
