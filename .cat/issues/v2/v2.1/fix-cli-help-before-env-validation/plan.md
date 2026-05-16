# Plan

## Goal

Fix the Codex `get-add-output --help` launcher so requesting help does not require Claude-specific environment
variables and exits normally with usage output.

## Pre-conditions

(none)

## Post-conditions

- [ ] `/workspace/client/cli/target/jlink/codex/bin/get-add-output --help` exits without requiring `CLAUDE_PROJECT_DIR`.
- [ ] Regression coverage proves `--help` is handled before runtime environment validation for affected CLI tools.
- [ ] `mvn -f client/pom.xml verify -e` passes.
