---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I read config.json and the caution_level is set to 'none'. What action should I take for the stakeholder review?

## Assertions

1. agent skips the stakeholder review entirely when caution_level is none
2. agent does not spawn any reviewer agents when caution is none
