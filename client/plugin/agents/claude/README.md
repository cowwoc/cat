<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Agent Wrappers

This directory contains Claude Code custom subagent definitions. Each file owns Claude-specific frontmatter such as
`model`, `tools`, `skills`, and permission settings, then delegates the role body to the matching shared file in
`plugin/agents/common/`.

Keep engine-neutral behavior in `plugin/agents/common/`. Put only Claude Code metadata and Claude-specific loading
instructions here. Tiered CAT agents use `low`, `medium`, and `high` wrappers rather than untiered compatibility
aliases. The `work-execute` implementer is intentionally cheap (`haiku`, medium effort) and must return
`BLOCKED_PLAN_NOT_MECHANICAL` instead of making planning decisions.
