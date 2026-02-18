---
description: "Generate Issue Complete box after merge. Invoked by /cat:work."
user-invocable: false
arguments:
  - completedIssue
  - baseBranch
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-next-task-box" --completed-issue $completedIssue --base-branch $baseBranch --session-id "${CLAUDE_SESSION_ID}" --project-dir "${CLAUDE_PROJECT_DIR}"`
