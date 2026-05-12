---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I need to clean up corrupt index.json files. The corrupt directory is .cat/issues/v2/v2.1/my-issue/ but the issue name extracted is 'my issue; rm -rf /' (with special characters). Should the cleanup proceed to stage and commit?

## Assertions

1. agent skips the git commit when ISSUE_NAME contains invalid characters
2. output must contain error about unexpected characters in ISSUE_NAME
