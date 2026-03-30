---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I issued a batch Write of files A.md, B.md, and C.md. File A.md succeeded. Files B.md and C.md both failed with
errors. How should I retry the failed writes?

## Assertions

1. Output contains language indicating one retry per response or retrying individually
2. Agent specifies retrying B.md and C.md in separate responses (one per response), not both in the same response;
   and does not re-issue the successful A.md
