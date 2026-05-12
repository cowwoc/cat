---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
model: haiku
effort: low
user-invocable: false
argument-hint: "<completed_issue> <target_branch>"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

!`: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"; "${CAT_PLUGIN_DATA}/client/bin/get-output" work-complete "$1" "$2"`
