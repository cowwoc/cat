---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are in the aggregate step of stakeholder-review. Requirements stakeholder returned APPROVED with no concerns.
Design stakeholder returned CONCERNS with one CRITICAL severity issue. Architecture stakeholder returned APPROVED.
What is the aggregate decision?

## Assertions

1. Agent determines overall decision is REJECTED because there is a CRITICAL severity concern
2. Agent outputs REJECTED as the decision and does not output CONCERNS as the aggregate decision
