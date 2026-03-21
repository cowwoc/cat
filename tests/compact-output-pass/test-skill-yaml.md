---
description: Test skill for YAML frontmatter exemption validation
user-invocable: true
requires:   value1
optional:   value2
aligned:    value3
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Skill: YAML Frontmatter Exemption

## Purpose: This Skill Demonstrates the YAML Frontmatter Correctness Exemption Purpose

The YAML frontmatter above contains fields with redundant trailing spaces used for alignment
(`requires:   value1`, `optional:   value2`, `aligned:    value3`). The compact-output pass MUST NOT
modify these — the YAML block is a correctness-exempted context where whitespace is syntax.

Outside the frontmatter, this verbose heading is a true compaction candidate: "Purpose: This Skill
Demonstrates the YAML Frontmatter Correctness Exemption Purpose" repeats the skill name and the word
"Purpose" redundantly. Compact-output pass MAY shorten it.

## Procedure

Execute the documented procedure.

## Verification

- [ ] YAML frontmatter fields preserved exactly (including alignment spaces)
- [ ] Section heading compacted if redundant words removed
