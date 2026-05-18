# Engine Duplication

Do not duplicate skills, tests, hooks, or docs solely because the engine name changes. Prefer shared files, shared
concept docs, or a engine-selection helper when the behavior is identical.

Engine-specific files are justified only when the execution contract differs. For example, `cat:claude-runner` is
Java-backed, copies plugin and jlink artifacts into an isolated Claude config, and emits Claude stream-json-derived
output. `cat:codex-runner` is procedure-based, shells out to `codex exec --json`, and captures the final assistant
message with `--output-last-message`.

Engine-specific tests should cover only engine-specific behavior. Do not create duplicate routing or negative
tests whose only difference is replacing `claude` with `codex` in the skill name or prompt.

Keep engine-specific skill behavior in engine-specific wrappers, not common skill bodies. Place engine-only
bootstrap, environment handling, and control-flow differences under engine-specific skill paths, while keeping
`client/plugin/skills/common/**` engine-agnostic.
