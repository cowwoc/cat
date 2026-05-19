# Plan

## Goal

Create a new Maven module `client/cli` (artifact-id `client-cli`) under `client`, and move the existing CLI modules under it as submodules:

- `client/common-cli` -> `client/cli/common`
- `client/claude-cli` -> `client/cli/claude`
- `client/codex-cli` -> `client/cli/codex`

Keep the artifact IDs unchanged:

- `client-common-cli`
- `client-claude-cli`
- `client-codex-cli`

## Parent Requirements

- `split-cli-runtime-modules`

## Pre-conditions

- [ ] `split-cli-runtime-modules` is closed

## Post-conditions

- [ ] `client/cli/pom.xml` exists with artifact-id `client-cli` and packaging `pom`
- [ ] `client/cli` declares submodules `common`, `claude`, and `codex`
- [ ] Former module paths are relocated to `client/cli/common`, `client/cli/claude`, and `client/cli/codex`
- [ ] Artifact IDs remain `client-common-cli`, `client-claude-cli`, and `client-codex-cli`
- [ ] Parent/child references and relative paths are updated so `mvn -f client/pom.xml verify -e` succeeds
- [ ] Distribution/jlink/build scripts referencing old paths are updated to the new module locations
