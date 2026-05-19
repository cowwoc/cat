---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
Please update instruction-builder behavior for this file:
`client/plugin/skills/codex/learn/first-use.md`

It contains:
```md
<!-- cat:include ../../include/codex-home-bootstrap.md -->
<!-- cat:include ../../common/learn/first-use.md -->
```

The text I need changed appears in the rendered output but originates from
`../../include/codex-home-bootstrap.md`. Update the wording there.

## Assertions
1. The Skill tool was invoked
2. The agent expanded include directives (rendered view) before deciding what to edit
3. The edit target was the originating include source file (`codex-home-bootstrap.md`), not only the top-level including file
