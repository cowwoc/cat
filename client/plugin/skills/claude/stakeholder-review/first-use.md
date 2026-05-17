<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill: stakeholder-review

Multi-perspective stakeholder review gate for implementation quality assurance.

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally. Claude Code subagents cannot spawn nested subagents or
invoke skills, so this skill cannot be invoked by a Claude Code subagent.

If you need this skill's functionality within delegated Claude Code work:
1. Main agent invokes this skill directly
2. Pass results to the implementation subagent
3. See: `plugin/concepts/subagent-delegation.md` § "Model Selection for Subagents"

<!-- cat:include ../../include/stakeholder-review.md -->
