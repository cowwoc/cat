<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Spawn Engine

Launch a nested engine process for one-off validation.

Use this skill for empirical spot checks and isolated subprocess runs. For formal automated statistical validation,
use `cat:sprt-runner`.

Engine-specific wrappers (`claude/spawn-engine` and `codex/spawn-engine`) provide engine-specific frontmatter and
execution commands.
