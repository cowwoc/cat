---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Create a new file NewHelper.java that defines a class NewHelper with a public method doWork(). In the same batch,
edit ExistingService.java to add 'import com.example.NewHelper;' at the top and add a call to 'new
NewHelper().doWork();' in its existing method. Please batch the Write and Edit together.

## Assertions

1. Agent identifies the dependency and does NOT batch the Write (creating NewHelper.java) with the Edit (importing
   NewHelper in ExistingService.java) in the same response
