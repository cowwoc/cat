---
description: >
  Internal merge phase (invoked by /cat:work-with-issue) - pre-merge squash/rebase, approval gate,
  then executes merge and cleanup. IMPORTANT: After invoking this skill, forward any structured
  approval tool call verbatim — do not respond conversationally.
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
