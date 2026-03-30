---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to write a single file config/settings.yml. Should I use a Bash heredoc (cat > config/settings.yml << 'EOF')
or the Write tool? I want to use batch-write for this.

## Assertions

1. Agent recommends using the Write tool directly (not Bash heredoc) for a single file
