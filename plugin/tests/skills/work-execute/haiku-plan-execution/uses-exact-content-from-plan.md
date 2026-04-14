---
category: requirement
model: haiku
---
## Turn 1

Execute the following plan step:

Create plugin/scripts/validate-uuid.sh with the following exact content:
```
#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
validate_uuid() { [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; }
validate_uuid "3734035c-0c9b-4063-9d0f-aad98ed260a6" && echo "valid" || echo "invalid"
```

## Assertions

1. Agent writes the file content using the exact regex `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$` (not a simplified or alternative pattern)
2. Agent includes the test line with UUID `3734035c-0c9b-4063-9d0f-aad98ed260a6` exactly as specified
3. Agent includes the license header comment block (lines starting with `# Copyright`, `#`, `# Licensed under`, `# See LICENSE.md`)
