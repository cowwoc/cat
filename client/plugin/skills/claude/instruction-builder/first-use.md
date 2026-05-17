<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Instruction Builder

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally (design subagent, red-team, and blue-team).
Claude Code subagents cannot spawn nested subagents or invoke skills, so this skill cannot be invoked by a
Claude Code subagent.

## Claude Code Test Case Constraint

Claude Code test-run agents can spawn one level of subagents when the runtime supports it, but Claude Code subagents
cannot spawn further agents. Test cases must not include assertions that require:
- Sub-subagent spawning two levels below the test-run process
- Output that can only exist if sub-subagents ran

<!-- cat:include ../../include/instruction-builder.md -->
