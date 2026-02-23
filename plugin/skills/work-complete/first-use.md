<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Complete

Generate the Issue Complete box after a successful merge, discovering the next available issue.

Output the skill result verbatim. Do not summarize, interpret, or modify the output.

**Parse the output to determine next issue status:**
- If output contains "**Next:**" followed by an issue ID → next issue found
- If output contains "Scope Complete" → no next issue

<output skill="work-complete">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-next-task-box" --completed-issue $0 --base-branch $1 --session-id ${CLAUDE_SESSION_ID} --project-dir ${CLAUDE_PROJECT_DIR}`
</output>
