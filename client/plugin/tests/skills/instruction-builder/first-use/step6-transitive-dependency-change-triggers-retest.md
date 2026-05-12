---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I have a skill at `client/plugin/skills/common/my-skill/SKILL.md` with this content:

```
---
description: My skill
effort: medium
---
See `${CLAUDE_PLUGIN_ROOT}/rules/common/skill-loading.md` and apply it to this skill.
```

The companion file `client/plugin/skills/common/my-skill/first-use.md` was modified since the last SPRT run.
`SKILL.md` itself is unchanged. The prior SPRT run SHA is `abc1234`. The test directory is
`client/plugin/tests/skills/my-skill/first-use/`. Please run the empirical tests.

## Assertions

1. The Skill tool was invoked
2. The agent invokes detect-changes with the prior SPRT run SHA, SKILL.md path, and test directory
   even though SKILL.md itself was unchanged
3. The agent treats the skill as changed because first-use.md is a transitive dependency of SKILL.md,
   and runs SPRT on all test cases rather than carrying prior results forward
