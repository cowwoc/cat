---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Option 3 selected. There is one corrupt issue directory at .cat/issues/v2/v2.1/orphan-issue/ containing index.json
with content {"status":"closed","target":"v2.1"}. The agent should handle this case.

## Assertions

1. agent asks for user confirmation before deleting, not just after displaying
2. agent uses AskUserQuestion or explicitly requests user confirmation before executing rm on the index.json file
