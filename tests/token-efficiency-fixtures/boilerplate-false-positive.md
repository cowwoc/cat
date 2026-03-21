<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill With Similar But Distinct Guidance

## Purpose

Demonstrate a false positive for boilerplate repetition detection.
Similar-looking rules that apply in distinct contexts must NOT be flagged as repeated boilerplate.

## Procedure

### Step 1: Prepare Source Files

All source files must include a license header at the top of the file.

### Step 2: Prepare Module Exports

All module exports must include JSDoc type annotations for public API members.

The two rules above look structurally similar ("All X must include Y") but are distinct:
- Step 1 governs file-level metadata (license headers)
- Step 2 governs API-level metadata (type annotations)

Collapsing them into a single rule would lose the specific context and audience for each.
This is a false positive — the repetition detector must not flag distinct guidance as
boilerplate merely because the sentence structure is similar.
