# Plan

## Goal

Review the common `cat:work*` skills for Claude-specific environment-variable and path references. Keep the skills
shared where behavior is portable by replacing Claude-specific references with runtime-neutral CAT variables such as
`CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, and `CAT_SESSION_ID`. Split files only if a `work*` skill has
a runtime-specific execution contract that cannot be expressed portably.

## Pre-conditions

(none)

## Post-conditions

- [x] Common `cat:work*` skills do not use `CLAUDE_*` variables for portable CAT paths or session ids.
- [x] Shared `work*` skill examples use portable CAT variables or existing local variables such as `WORKTREE_PATH`.
- [x] Runtime-specific wording remains only where the runtime behavior genuinely differs.
- [x] Relevant documentation or examples remain accurate for both Claude and Codex.
- [x] `mvn -f client/pom.xml verify -e` passes.
