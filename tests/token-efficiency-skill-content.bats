#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests verifying that optimize-execution and instruction-builder skills contain the required
# token-efficiency guidance, exemption rules, and report structure as specified in PLAN.md.
#
# Wave 1: E2E Acceptance Criteria Validation (items 1 and 2)
# Wave 2: Exemption Rule Testing (item 3)
# Wave 3: False-Positive and Detection Gap Analysis (item 4)

# In bats, BATS_TEST_DIRNAME is the directory of the test file.
PROJECT_ROOT="$(cd "${BATS_TEST_DIRNAME}/.." && pwd)"
OPTIMIZE_SKILL="${PROJECT_ROOT}/plugin/skills/optimize-execution/first-use.md"
INSTRUCTION_SKILL="${PROJECT_ROOT}/plugin/skills/instruction-builder-agent/first-use.md"

# ---------------------------------------------------------------------------
# Wave 1, Item 1: Verify optimize-execution Token Efficiency reporting
# ---------------------------------------------------------------------------

@test "optimize-execution skill file exists" {
  [ -f "${OPTIMIZE_SKILL}" ]
}

@test "optimize-execution reports Token Efficiency as a named category in Issues Found" {
  # REQ-001: Token Efficiency must appear as a named category in the report structure
  grep -q "Token Efficiency" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency section appears in report output template" {
  # The report template (Step 7) must include a Token Efficiency subsection under Issues Found
  grep -q "#### Token Efficiency" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency report table has required columns" {
  # The report table must have: Skill, Pattern, Location, Est. Savings/Invocation columns
  grep -q "Skill.*Pattern.*Location.*Est. Savings" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution documents all 4 detection patterns" {
  # REQ-002: All four detection patterns must be present for token savings estimation
  grep -q "Verbose section headings" "${OPTIMIZE_SKILL}"
  grep -q "Redundant leading spaces" "${OPTIMIZE_SKILL}"
  grep -q "Boilerplate repeated" "${OPTIMIZE_SKILL}"
  grep -q "Unused output sections" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency detection patterns table has estimated savings ranges" {
  # REQ-002: Estimated token savings must appear for each pattern
  grep -q "10-30 tokens" "${OPTIMIZE_SKILL}"
  grep -q "5-20 tokens" "${OPTIMIZE_SKILL}"
  grep -q "50-200 tokens" "${OPTIMIZE_SKILL}"
  grep -q "20-100 tokens" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency reporting format includes required fields" {
  # Each flagged pattern must include: location, pattern type, sample text, estimated savings
  grep -q "Location" "${OPTIMIZE_SKILL}"
  grep -q "Pattern type" "${OPTIMIZE_SKILL}"
  grep -q "Sample text" "${OPTIMIZE_SKILL}"
  grep -q "Estimated savings" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency analysis step is in Step 6 recommendations" {
  # Token efficiency analysis must be integrated into Step 6 as item 11
  grep -q "Token efficiency analysis" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution states correctness takes priority over compactness" {
  # REQ-005: Correctness-first priority must be explicitly stated
  grep -q "Correctness.*takes priority" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution example output includes Token Efficiency recommendation entry" {
  # The example output (worked example) must demonstrate token efficiency reporting
  grep -q "repeated boilerplate" "${OPTIMIZE_SKILL}"
}

# ---------------------------------------------------------------------------
# Wave 1, Item 2: Verify instruction-builder compact-output pass integration
# ---------------------------------------------------------------------------

@test "instruction-builder skill file exists" {
  [ -f "${INSTRUCTION_SKILL}" ]
}

@test "instruction-builder documents compact-output pass" {
  # REQ-003: A compact-output pass must be documented in instruction-builder
  grep -q -i "compact.output" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder compact-output pass lists YAML frontmatter exemption" {
  # REQ-004: YAML frontmatter must be listed as a correctness exemption
  grep -q "YAML frontmatter" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder compact-output pass lists Makefile targets exemption" {
  # REQ-004: Makefile targets must be listed as a correctness exemption
  grep -q "Makefile" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder compact-output pass lists fenced code blocks exemption" {
  # REQ-004: Fenced code blocks must be listed as a correctness exemption
  grep -q "fenced code block" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder compact-output pass lists semantic whitespace exemption" {
  # REQ-004: Semantic whitespace contexts must be listed as a correctness exemption
  grep -q "semantic" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder compact-output pass lists visual alignment exemption" {
  # REQ-004: Visual alignment / display tables must be listed as a correctness exemption
  grep -q "visual" "${INSTRUCTION_SKILL}"
}

@test "instruction-builder states correctness takes priority over compactness" {
  # REQ-005: Correctness-first priority must be explicitly stated in instruction-builder
  grep -q -i "correctness.*priority\|priority.*correctness\|correctness.*takes priority\|correctness always" \
    "${INSTRUCTION_SKILL}"
}

# ---------------------------------------------------------------------------
# Wave 2, Item 3: Test compact-output pass exemption rules via skill content
# ---------------------------------------------------------------------------

@test "optimize-execution YAML exemption documented in correctness exemptions" {
  # YAML frontmatter exemption must appear in the correctness exemptions list
  grep -q "YAML frontmatter" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Makefile exemption documented in correctness exemptions" {
  # Makefile exemption must appear in the correctness exemptions list
  grep -q "Makefile" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution fenced code block exemption documented" {
  # Fenced code block exemption must appear in the correctness exemptions list
  grep -q "fenced code block" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution semantic whitespace exemption documented" {
  # Semantic whitespace exemption must appear in the correctness exemptions list
  grep -q "semantic" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution display table exemption documented" {
  # Display tables / boxes must be listed as exempt from compaction
  grep -q "display table\|formatted report\|visual design" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution lists all 5 correctness exemption contexts" {
  # All 5 exemption contexts must be present as a set
  local exemption_count
  exemption_count=0

  grep -q "YAML frontmatter" "${OPTIMIZE_SKILL}"       && exemption_count=$((exemption_count + 1))
  grep -q "Makefile" "${OPTIMIZE_SKILL}"               && exemption_count=$((exemption_count + 1))
  grep -q "fenced code block" "${OPTIMIZE_SKILL}"      && exemption_count=$((exemption_count + 1))
  grep -q "semantic" "${OPTIMIZE_SKILL}"               && exemption_count=$((exemption_count + 1))
  grep -q "display table\|visual design" "${OPTIMIZE_SKILL}" && exemption_count=$((exemption_count + 1))

  [ "${exemption_count}" -eq 5 ]
}

@test "instruction-builder lists all 5 correctness exemption contexts" {
  # All 5 exemption contexts must be present in instruction-builder as well
  local exemption_count
  exemption_count=0

  grep -q "YAML frontmatter" "${INSTRUCTION_SKILL}"       && exemption_count=$((exemption_count + 1))
  grep -q "Makefile" "${INSTRUCTION_SKILL}"               && exemption_count=$((exemption_count + 1))
  grep -q "fenced code block" "${INSTRUCTION_SKILL}"      && exemption_count=$((exemption_count + 1))
  grep -q "semantic" "${INSTRUCTION_SKILL}"               && exemption_count=$((exemption_count + 1))
  grep -q "visual" "${INSTRUCTION_SKILL}"                 && exemption_count=$((exemption_count + 1))

  [ "${exemption_count}" -eq 5 ]
}

# ---------------------------------------------------------------------------
# Wave 3, Item 4: Validate Token Efficiency detection pattern documentation
# ---------------------------------------------------------------------------

@test "optimize-execution documents verbose section heading detection pattern" {
  # True positive: heading that repeats context already present in scope
  grep -q "Verbose section heading" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution documents redundant leading spaces detection pattern" {
  # True positive: multiple example lines with identical leading spaces that could be one tab
  grep -q "Redundant leading spaces" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution documents boilerplate repetition detection pattern" {
  # True positive: identical guidance block appearing 2+ times across steps
  grep -q "[Bb]oilerplate" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution documents unused output section detection pattern" {
  # True positive: section that always produces empty table, list, or block
  grep -q "[Uu]nused output" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency section uses conditional include directive" {
  # The Token Efficiency report section must only appear when waste patterns are detected
  # The skill must document that this subsection is conditional on findings
  grep -q "only if\|only when\|Include.*only" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution detection patterns do not flag exempt contexts as wasteful" {
  # Correctness exemptions must be explicitly documented to prevent false positives
  # This test verifies the do-NOT-flag instruction is present
  grep -q "do NOT flag\|not flag\|Never flag\|must NOT" "${OPTIMIZE_SKILL}"
}

@test "optimize-execution Token Efficiency section ordered by estimated savings" {
  # REQ-001: Flagged patterns must be ordered by estimated token savings
  grep -q "ordered by estimated savings\|ordered by.*savings" "${OPTIMIZE_SKILL}"
}
