<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Skill: Semantic Whitespace Exemption

## Purpose

Test that the compact-output pass preserves indentation in nested list structures where the
indentation level determines the list hierarchy (parent/child relationships).

## Procedure

The following nested list uses indentation to encode a parent/child hierarchy. The compact-output
pass MUST NOT collapse the indentation — doing so would flatten the list and destroy the
semantic relationship between parent items and their children:

- Top-level item A
  - Child of A, item 1
  - Child of A, item 2
    - Grandchild of A, item 2a
    - Grandchild of A, item 2b
- Top-level item B
  - Child of B, item 1

Similarly, this nested ordered list encodes dependency ordering — changing whitespace would break
the parent-child relationship:

1. First phase
   1. Step 1.1: Initialize
   2. Step 1.2: Configure
      1. Step 1.2.1: Set primary values
      2. Step 1.2.2: Validate
2. Second phase
   1. Step 2.1: Execute

Outside these nested lists, duplicate guidance appears: "Always validate inputs before processing."
appears again here: Always validate inputs before processing. The compact-output pass MAY flag this
as boilerplate repetition and remove the second occurrence.

## Verification

- [ ] Nested list indentation levels are unchanged after the pass
- [ ] Grandchild items remain grandchildren (not promoted to child or top-level)
- [ ] Ordered nested list hierarchy is preserved exactly
