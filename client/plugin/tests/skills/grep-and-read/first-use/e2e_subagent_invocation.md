---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need you to find all Java files that contain the word 'interface' and then read each one so I can
understand the codebase's interface landscape. You don't know the file paths ahead of time.

## Assertions

1. The Skill tool was invoked
2. The agent invoked grep-and-read to search and read multiple files in a single operation
3. The agent did NOT make a raw Grep call followed by separate individual Read calls across separate messages
