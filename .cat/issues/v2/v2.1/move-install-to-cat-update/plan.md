# Plan: move-install-to-cat-update

## Goal

Separate end-user CAT updates from local development update tooling. End-users should update Codex by running the
release bootstrap prompt documented in the README, while CAT contributors should have a project-only `/cat-update`
workflow for refreshing local development builds.

## Parent Requirements

None - release/install workflow polish.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Update instructions, runtime skill registration, and release validation tests must describe one
  install path consistently.
- **Mitigation:** Update tests and documentation together, verify generated runtime artifacts, and check that end-user
  plugin bundles do not expose the local development update skill.

## Files to Modify

- `client/plugin/skills/common/install/` - Keep end-user bundles free of a second install command.
- Project-only CAT skill location - Add `/cat-update` for local development updates.
- `README.md` - Make end-user update instructions point to the Codex install prompt.
- `docs/prompts/codex-install.md` - Keep the prompt as the end-user install/update path.
- Release documentation tests - Assert the updated end-user and project-developer contracts.

## Pre-conditions

- [ ] The Codex install prompt installs the plugin files and bundled client runtime without a second command.

## Jobs

### Job 1

- Keep local-development update behavior in the project-only `/cat-update`
  workflow.
  - Files: `client/plugin/skills/common/install/`, project skill configuration files
- Update README and prompt documentation so end-users run the Codex install prompt for both first install and updates.
  - Files: `README.md`, `docs/prompts/codex-install.md`
- Add or update tests that fail if end-user bundles expose a second install command as a required update step.
  - Files: release documentation and plugin packaging tests

## Post-conditions

- [ ] End-user Codex update instructions tell users to run the README prompt.
- [ ] Shipped CAT plugin artifacts do not expose a second install command.
- [ ] A project-only `/cat-update` workflow exists for local development updates.
- [ ] Release/documentation tests cover the split between end-user updates and local development updates.
- [ ] `mvn -f client/pom.xml verify -e` passes.
