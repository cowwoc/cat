---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I want CAT to support exporting issue status as JSON. File feedback.

## Assertions

1. The agent offers a feature request path as a first-class feedback type.
2. For a feature request, the agent collects the requested capability, user motivation or use case, and any proposed solution or alternatives.
3. The agent searches for duplicate GitHub issues before creating a new issue.
4. The agent builds a feature request issue body that includes description, motivation, and proposed solution or alternatives.
5. The agent shows the full issue title, labels, and body to the user before publishing.
6. The agent requires explicit permission to publish publicly after the preview.
7. The agent creates the issue with `gh issue create` only after explicit approval.
8. The agent does not use the legacy `feedback open` browser workflow as the publishing mechanism.
