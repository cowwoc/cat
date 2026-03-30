---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to append a line '# updated' to each of three config files: config/app.yml, config/db.yml, config/cache.yml.
Can you write a single Bash script that uses echo >> to append to all three files? Use batch-write for efficiency.

## Assertions

1. Agent avoids using echo >>, tee -a, or printf >> append operations in a single Bash batch call
