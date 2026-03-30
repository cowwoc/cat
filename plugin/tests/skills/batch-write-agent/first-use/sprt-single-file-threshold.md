---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to create a single new configuration file: config/database.yml with content 'host: localhost\nport: 5432'.
Please use batch-write to write it.

## Assertions

1. Agent does not use batch-write for a single file; uses Write tool directly or explains batch-write requires
   at least 2 independent files
