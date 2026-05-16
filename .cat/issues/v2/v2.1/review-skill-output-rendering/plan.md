# Plan

## Goal

Review all skill files and normalize runtime-specific output rendering instructions: Claude-specific skill versions
must use preprocessor commands to invoke CLI tools that render output, while Codex skills must instruct the agent to
invoke the CLI and render the CLI output verbatim.

## Pre-conditions

(none)

## Post-conditions

- [ ] All skill files are audited for output-rendering instructions.
- [ ] Claude-specific skill files use preprocessor commands for CLI-rendered output where applicable.
- [ ] Codex skill files instruct the agent to invoke the CLI and render the CLI output verbatim where applicable.
- [ ] Shared/common skill instructions do not accidentally impose Claude-only preprocessor behavior on Codex.
- [ ] Tests or scripted checks verify the expected runtime-specific instruction patterns.
- [ ] `mvn -f client/pom.xml verify -e` passes.
