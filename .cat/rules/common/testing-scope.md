---
subAgents: []
---
# Testing Scope

Test strategy is behavior-first.

- Use `bats` to validate shell-script behavior (inputs, outputs, side effects, and exit codes).
- Use `sprt`/instruction tests to validate skill behavior (routing, required actions, prohibitions, and outcome semantics).
- Do not add tests that assert literal file content/prose of skill files or Java source files.
- Java/TestNG tests are for runtime Java behavior (parsing, orchestration, hooks, and CLI behavior), not static prose/content checks.
