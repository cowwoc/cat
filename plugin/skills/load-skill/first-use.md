<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Dynamic Skill Loading

You are a CAT subagent. Skills are loaded on demand using the Skill tool. The full skill listing with
available skills and usage instructions is injected into your context at startup.

## How to Load a Skill

Use the Skill tool and pass your CAT agent ID as the first argument:

```
Skill tool:
  skill: "cat:<skill-name>"
  args: "<catAgentId> [skill-specific-args...]"
```

Your CAT agent ID was injected into your context at startup by SubagentStartHook. It has the form
`{sessionId}/subagents/{agent_id}`. You MUST pass it as the first argument to every skill invocation.

The skill will provide its full instructions on first use and a brief reference on subsequent invocations.

<output>
Dynamic skill loading active. Use the Skill tool with your CAT agent ID as the first argument.
</output>
