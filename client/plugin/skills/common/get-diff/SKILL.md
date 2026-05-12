---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
model: haiku
effort: low
user-invocable: false
argument-hint: "<issue-path>"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

!`: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"; "${CAT_PLUGIN_DATA}/client/bin/get-output" "$0" get-diff "$1"`
