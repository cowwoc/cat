<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Complete

Generate the Issue Complete box after a successful merge, discovering the next available issue.

**Parse the output to determine next issue status:**
- If output contains "**Next:**" followed by an issue ID → next issue found
- If output contains "Scope Complete" → no next issue

INVOKE: Skill("cat:get-output", args="work-complete $0 $1")
