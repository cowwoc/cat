---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Simulate a workflow file that already has a push
trigger, but it is scoped to different branches (e.g., 'branches: [main, develop]') and does NOT include the
current branch (e.g., 'feature/my-branch'). Invoke the skill and follow Step 1. In your response, explicitly state
what the skill determines about the existing trigger and what value it sets for ADDED_TRIGGER.

## Assertions

1. skill must detect existing push trigger but recognize it does not cover current branch, then set ADDED_TRIGGER=true
to add a scoped trigger
2. skill correctly identifies that the trigger exists but does NOT cover the current branch
