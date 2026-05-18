---
description: >
  Use when renaming, removing, or moving a symbol across multiple files — scan all occurrences first,
  build a complete file-to-changes plan, apply all edits without intermediate compilation, then verify
  the build once. Use this for coordinated symbol changes; batch-write behavior is governed by the engine-loaded
  CAT batch-write rule.
model: haiku
effort: low
user-invocable: false
allowed-tools: Bash, Grep, Read, Edit
argument-hint: "<symbol> [symbol] ..."
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
