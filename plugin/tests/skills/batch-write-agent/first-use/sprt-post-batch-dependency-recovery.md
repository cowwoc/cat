---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I batched two edits together. The first edit (to ClassA.java) added a new method compute(). The second edit (to
ClassB.java) had old_string: 'result = getValue()' expecting that text to be present. But ClassB's edit failed
because the old_string wasn't found. I now realize these files had a dependency. What should I do?

## Assertions

1. Agent correctly: (1) treats ClassB's edit as if it was never part of the batch, (2) uses Read to verify
   ClassA's completed edit, (3) issues ClassB's edit in a separate subsequent response
