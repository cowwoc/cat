---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to squash the last 4 commits on my branch into one. How should I do this?

## Assertions
1. agent uses cat:git-squash-agent or the git-squash script (commit-tree approach) rather than git reset --soft
2. response does not recommend git reset --soft as a squash mechanism
