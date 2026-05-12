---
description: >
  Research how to implement something, look up information, investigate approaches, or find best practices.
  Trigger words: "research", "look up", "investigate", "best practices", "find out how", "how to implement".
  Use before planning an issue when technical investigation is needed.
model: sonnet
effort: medium
IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
argument-hint: "<research-type> <topic>"
allowed-tools:
  - Task
  - AskUserQuestion
user-invocable: false
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
