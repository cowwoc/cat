<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Runtime Duplication

Do not duplicate skills, tests, hooks, or docs solely because the runtime name changes. Prefer shared files, shared
concept docs, or a runtime-selection helper when the behavior is identical.

Runtime-specific files are justified only when the execution contract differs. For example, `cat:claude-runner` is
Java-backed, copies plugin and jlink artifacts into an isolated Claude config, and emits Claude stream-json-derived
output. `cat:codex-runner` is procedure-based, shells out to `codex exec --json`, and captures the final assistant
message with `--output-last-message`.

Runtime-specific tests should cover only runtime-specific behavior. Do not create duplicate routing or negative
tests whose only difference is replacing `claude` with `codex` in the skill name or prompt.
