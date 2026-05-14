---
description: >
  Use for quick token health check during sessions, after subagent completion,
  or before deciding whether to decompose remaining work
model: haiku
effort: low
user-invocable: false
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../../include/token-report.md -->

!`: "${CAT_PLUGIN_DATA:?CAT_PLUGIN_DATA is required}"; "${CAT_PLUGIN_DATA}/client/bin/get-output" token-report`
