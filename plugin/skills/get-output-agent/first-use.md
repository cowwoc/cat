<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Get Output (Agent Variant)

Agent-only skill for verbatim output generation. Silently executes output sub-skills and returns their results
without printing to the user. This variant is invoked by other skills and agents — it is not user-invocable.

Use `cat:get-output-agent` when a skill needs to render a structured output block (banners, summaries, progress
displays) via the centralized output system.

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" $ARGUMENTS`
