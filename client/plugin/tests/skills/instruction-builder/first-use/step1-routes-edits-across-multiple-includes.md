---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
Please update instruction-builder for this instruction file:
`client/plugin/skills/codex/learn/first-use.md`

It includes:
```md
<!-- cat:include ../../include/codex-home-bootstrap.md -->
<!-- cat:include ../../common/learn/first-use.md -->
```

I need one wording change in text originating from `codex-home-bootstrap.md`
and a separate wording change in text originating from `common/learn/first-use.md`.

## Assertions
1. The Skill tool was invoked
2. The agent used a rendered view with include expansion before edit targeting
3. The agent targeted `codex-home-bootstrap.md` for the first change and `common/learn/first-use.md` for the second change
4. The top-level including file (`client/plugin/skills/codex/learn/first-use.md`) was not directly edited
5. Only the intended include source files were edited (one targeted change per intended source)
