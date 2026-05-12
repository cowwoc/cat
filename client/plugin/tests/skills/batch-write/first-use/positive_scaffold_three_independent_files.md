---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
I'm adding a new cat:hello-world skill to the plugin. Please create the following 3 independent files:

1. client/plugin/skills/common/hello-world/SKILL.md with this content:
```
---
name: hello-world
description: WHEN the user wants to greet the world - outputs Hello, World! to the terminal
---
```

2. client/plugin/skills/common/hello-world/first-use.md with this content:
```
# Hello World Skill

## Purpose
Output a greeting to the terminal.

## Procedure
Output the text: Hello, World!

## Verification
- [ ] Output contains Hello, World!
```

3. client/plugin/tests/skills/hello-world/first-use/basic.md with this content:
```
---
category: REQUIREMENT
---
## Turn 1
Say hello to the world.
## Assertions
1. The Skill tool was invoked
```

These files are all new and do not depend on each other.

## Assertions
1. The Skill tool was invoked
2. Three Write tool calls were issued in a single LLM response
