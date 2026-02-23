<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Dynamic Skill Loading

You are a CAT subagent. Skills are loaded on demand using `load-skill.sh` via Bash. The full skill listing with
available skills and usage instructions is injected into your context at startup.

## How to Load a Skill

Run via Bash:

```bash
load-skill.sh "${CLAUDE_PLUGIN_ROOT}" "<skill-name>" "${CAT_AGENT_ID}" "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"
```

The skill will provide its full instructions on first use and a brief reference on subsequent invocations.

<output>
Dynamic skill loading active. Use load-skill.sh via Bash to load any skill on demand.
</output>
