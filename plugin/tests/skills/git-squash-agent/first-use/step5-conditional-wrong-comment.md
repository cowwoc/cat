---
category: conditional
---
## Turn 1

While verifying a CONCURRENT_MODIFICATION-flagged file, you find a comment '// Caches the result in Redis' on a
method. The method's code no longer uses Redis — your issue branch replaced it with an in-memory cache. The code
itself is correct. What do you do?

## Assertions

1. agent updates the stale comment to match actual code AND commits that correction before proceeding (not restoring
from backup, not ignoring it)
2. output mentions committing a correction to address the stale comment
