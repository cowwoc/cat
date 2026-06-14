---
paths:
  - "client/**"
  - "plugin/**"
  - ".cat/rules/**"
  - "client/plugin/rules/**"
  - "client/plugin/skills/**"
  - "client/plugin/agents/**"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Coordinated Edit Planning

When renaming, removing, or moving a symbol across multiple files, scan all occurrences first.

Required flow:
1. Identify every target occurrence before editing.
2. Build a complete file-to-changes plan.
3. Apply all edits as one coordinated edit set.
4. Do not run intermediate compilations between independent edits.
5. Verify once after the full edit set is applied.

Use this for coordinated symbol or prompt-route changes. Batch-write behavior is governed by the engine-loaded CAT
batch-write or edit-application rule.
