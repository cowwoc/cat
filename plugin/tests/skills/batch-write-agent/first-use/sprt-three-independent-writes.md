---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please create three fully independent configuration files: config/app.yml with content 'env: production',
config/db.yml with content 'host: db.example.com', and config/cache.yml with content 'host: cache.example.com'.
None of these files reference each other.

## Assertions

1. Agent batches all three independent file writes in a single response (issues all three Write calls or a Bash
   heredoc with 3 writes in one response)
