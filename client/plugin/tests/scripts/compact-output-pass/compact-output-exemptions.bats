#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Wave 2, Item 3: Validate compact-output pass exemption rules.
#
# Each test artifact (test-skill-*.md) is a mini skill draft that contains:
#   - An exempted context that the compact-output pass MUST NOT modify
#   - Content outside the exempted context that is a candidate for compaction
#
# These tests assert the artifacts are structured correctly so that an agent
# applying the compact-output pass rules would know what to preserve vs. what
# to compact.

FIXTURES_DIR="$(cd "${BATS_TEST_DIRNAME}" && pwd)"

# ---------------------------------------------------------------------------
# Artifact existence
# ---------------------------------------------------------------------------

@test "test-skill-yaml.md artifact exists" {
  [ -f "${FIXTURES_DIR}/test-skill-yaml.md" ]
}

@test "test-skill-makefile.md artifact exists" {
  [ -f "${FIXTURES_DIR}/test-skill-makefile.md" ]
}

@test "test-skill-code-block.md artifact exists" {
  [ -f "${FIXTURES_DIR}/test-skill-code-block.md" ]
}

@test "test-skill-semantic-ws.md artifact exists" {
  [ -f "${FIXTURES_DIR}/test-skill-semantic-ws.md" ]
}

@test "test-skill-table.md artifact exists" {
  [ -f "${FIXTURES_DIR}/test-skill-table.md" ]
}

# ---------------------------------------------------------------------------
# YAML frontmatter exemption (test-skill-yaml.md)
# ---------------------------------------------------------------------------

@test "yaml artifact contains YAML frontmatter block" {
  # YAML frontmatter is delimited by --- lines; the artifact must have one
  grep -c "^---$" "${FIXTURES_DIR}/test-skill-yaml.md" | grep -q "^2$"
}

@test "yaml artifact YAML block contains alignment spaces (exempted content)" {
  # The YAML fields use trailing spaces for alignment — these MUST be preserved by the pass
  grep -q "requires:   value1" "${FIXTURES_DIR}/test-skill-yaml.md"
  grep -q "optional:   value2" "${FIXTURES_DIR}/test-skill-yaml.md"
}

@test "yaml artifact contains a verbose heading outside YAML block (compaction candidate)" {
  # The heading outside YAML repeats context and is a valid compaction target
  grep -q "Purpose: This Skill Demonstrates" "${FIXTURES_DIR}/test-skill-yaml.md"
}

@test "yaml artifact documents that YAML spaces MUST NOT be modified" {
  grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-yaml.md"
}

# ---------------------------------------------------------------------------
# Makefile target exemption (test-skill-makefile.md)
# ---------------------------------------------------------------------------

@test "makefile artifact contains a fenced makefile code block" {
  grep -q '```makefile' "${FIXTURES_DIR}/test-skill-makefile.md"
}

@test "makefile artifact fenced block contains a Makefile recipe target" {
  grep -q "^build:" "${FIXTURES_DIR}/test-skill-makefile.md"
}

@test "makefile artifact fenced block recipe lines use tab indentation" {
  # Recipe lines in a Makefile must start with a tab character
  # Use printf to search for a literal tab at the start of a line
  grep -qP '^\t' "${FIXTURES_DIR}/test-skill-makefile.md"
}

@test "makefile artifact documents that tab indentation MUST NOT be changed" {
  grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-makefile.md"
}

# ---------------------------------------------------------------------------
# Fenced code block indentation exemption (test-skill-code-block.md)
# ---------------------------------------------------------------------------

@test "code-block artifact contains at least two fenced code blocks" {
  local count
  count=$(grep -c '```' "${FIXTURES_DIR}/test-skill-code-block.md")
  # 4 backtick-fence lines = 2 open/close pairs
  [ "${count}" -ge 4 ]
}

@test "code-block artifact first fenced block contains nested indentation" {
  # The Python block must have indented lines inside the function body
  grep -q "    if name:" "${FIXTURES_DIR}/test-skill-code-block.md"
  grep -q "        print" "${FIXTURES_DIR}/test-skill-code-block.md"
}

@test "code-block artifact second fenced block has leading-space output lines" {
  # The output block uses leading spaces as semantic content (subprocess output)
  grep -q "    line 1 of subprocess output" "${FIXTURES_DIR}/test-skill-code-block.md"
}

@test "code-block artifact documents that indentation inside blocks MUST NOT be altered" {
  grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-code-block.md"
}

@test "code-block artifact has a verbose heading outside fenced blocks (compaction candidate)" {
  grep -q "Procedure: Demonstrate Code Block Indentation" "${FIXTURES_DIR}/test-skill-code-block.md"
}

# ---------------------------------------------------------------------------
# Semantic whitespace exemption (test-skill-semantic-ws.md)
# ---------------------------------------------------------------------------

@test "semantic-ws artifact contains a nested unordered list" {
  # Two-space indented list items encode parent/child relationships
  grep -q "^  - Child of A" "${FIXTURES_DIR}/test-skill-semantic-ws.md"
}

@test "semantic-ws artifact nested list has grandchild items" {
  grep -q "^    - Grandchild of A" "${FIXTURES_DIR}/test-skill-semantic-ws.md"
}

@test "semantic-ws artifact contains a nested ordered list" {
  grep -q "   1. Step 1.1:" "${FIXTURES_DIR}/test-skill-semantic-ws.md"
}

@test "semantic-ws artifact documents that nested list indentation MUST NOT be collapsed" {
  grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-semantic-ws.md"
}

@test "semantic-ws artifact contains duplicate boilerplate outside nested lists (compaction candidate)" {
  # The same sentence appears twice outside the nested lists — valid compaction target
  local count
  count=$(grep -c "Always validate inputs before processing" "${FIXTURES_DIR}/test-skill-semantic-ws.md")
  [ "${count}" -ge 2 ]
}

# ---------------------------------------------------------------------------
# Display table alignment exemption (test-skill-table.md)
# ---------------------------------------------------------------------------

@test "table artifact contains a markdown table with alignment padding" {
  grep -q "| Status    | Count |" "${FIXTURES_DIR}/test-skill-table.md"
}

@test "table artifact markdown table has right-aligned numeric column" {
  # The Count column uses right-padding spaces to align numbers: "    42" and "     3"
  grep -q "|    42 |" "${FIXTURES_DIR}/test-skill-table.md"
  grep -q "|     3 |" "${FIXTURES_DIR}/test-skill-table.md"
}

@test "table artifact contains a formatted report box inside a fenced block" {
  grep -q "Session Analysis Report" "${FIXTURES_DIR}/test-skill-table.md"
}

@test "table artifact formatted box uses padding spaces for alignment" {
  grep -q "Duration:      1m 23s" "${FIXTURES_DIR}/test-skill-table.md"
}

@test "table artifact documents that table and box alignment MUST NOT be compressed" {
  grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-table.md"
}

@test "table artifact contains duplicate boilerplate outside table and box (compaction candidate)" {
  # The same sentence appears twice — valid compaction target
  local count
  count=$(grep -c "Review the output carefully before proceeding" "${FIXTURES_DIR}/test-skill-table.md")
  [ "${count}" -ge 2 ]
}

# ---------------------------------------------------------------------------
# Cross-artifact: all artifacts document the exemption and include a compaction candidate
# ---------------------------------------------------------------------------

@test "all artifacts have a license header" {
  for artifact in yaml makefile code-block semantic-ws table; do
    grep -q "Copyright (c) 2026" "${FIXTURES_DIR}/test-skill-${artifact}.md" \
      || { echo "Missing license header in test-skill-${artifact}.md"; return 1; }
  done
}

@test "all artifacts document a correctness exemption with MUST NOT language" {
  for artifact in yaml makefile code-block semantic-ws table; do
    grep -q "MUST NOT" "${FIXTURES_DIR}/test-skill-${artifact}.md" \
      || { echo "Missing MUST NOT exemption language in test-skill-${artifact}.md"; return 1; }
  done
}

@test "all artifacts contain a Verification section" {
  for artifact in yaml makefile code-block semantic-ws table; do
    grep -q "^## Verification" "${FIXTURES_DIR}/test-skill-${artifact}.md" \
      || { echo "Missing Verification section in test-skill-${artifact}.md"; return 1; }
  done
}
