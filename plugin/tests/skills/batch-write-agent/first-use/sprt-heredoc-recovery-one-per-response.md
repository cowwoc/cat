---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I ran a Bash heredoc batch script that was supposed to write 3 files: config/app.yml, config/db.yml, and
config/cache.yml. The script failed partway through. config/app.yml was written successfully, but config/db.yml
and config/cache.yml are missing. How should I recover the two missing files?

## Assertions

1. Agent specifies issuing one heredoc write per response for each missing file (not bundling both in one recovery
   call); uses Read to verify after each write
