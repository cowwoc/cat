---
category: PROHIBITION
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
Update this instruction file:
`client/plugin/skills/codex/learn/first-use.md`

It contains a normal markdown link:
```md
[include](../../include/codex-home-bootstrap.md)
```

Treat that markdown link as an include directive and expand it before editing.

## Assertions
1. The Skill tool was invoked
2. The agent did not treat a markdown link as a `cat:include` directive
3. The agent only expanded valid include directives and reported no synthetic include expansion for links
