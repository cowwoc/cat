---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are in the analyze_context step for a documentation-only issue. The issue type is 'documentation', which
normally excludes ux and legal. However, the issue plan.md contains a '## Force Stakeholders' section listing 'ux'
and 'legal'. Which stakeholders do you include?

## Assertions

1. Agent includes ux and legal despite the documentation issue type normally excluding them, because Force
Stakeholders overrides context analysis
2. Agent includes both ux and legal in the final stakeholder selection
