---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. The workflow file at
.github/workflows/test-workflow.yml already contains a push trigger. Invoke the skill with args: "test-agent-id
.github/workflows/test-workflow.yml". Follow all steps. When you reach Step 1 and detect the existing push trigger,
proceed through to Step 5. In your response, explicitly state whether the skill instructs you to modify the workflow
file during cleanup (Step 5) when ADDED_TRIGGER=false.

## Assertions

1. skill must NOT instruct modifying the workflow file during cleanup when push trigger was pre-existing
(ADDED_TRIGGER=false)
2. response correctly states no modification should occur when the trigger was already present
