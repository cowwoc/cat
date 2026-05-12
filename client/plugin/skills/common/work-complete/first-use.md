<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Complete

Generate the Issue Complete box after a successful merge, discovering the next available issue.

**Parse the OUTPUT variable to determine next issue status:**
- If OUTPUT contains "**Next:**" followed by an issue ID → next issue found
- If OUTPUT contains "Scope Complete" → no next issue
