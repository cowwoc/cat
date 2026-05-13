# Plan: move-install-to-cat-update

## Goal

Separate end-user CAT updates from local development update tooling. End-users should update Codex by running the
release bootstrap prompt documented in the README, while CAT contributors should have a project-only `/cat-update`
workflow for refreshing local development builds.

## Parent Requirements

None - release/install workflow polish.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Removing or renaming the shipped `cat:install` skill can break existing update instructions, runtime
  skill registration, or release validation tests if references are missed.
- **Mitigation:** Update tests and documentation together, verify generated runtime artifacts, and check that end-user
  plugin bundles do not expose the local development update skill.

## Files to Modify

- `client/plugin/skills/common/install/` - Remove or stop shipping the end-user `/cat:install` skill.
- Project-only CAT skill location - Add `/cat-update` for local development updates.
- `README.md` - Make end-user update instructions point to the Codex install prompt.
- `docs/prompts/codex-install.md` - Keep the prompt as the end-user install/update path.
- Release documentation tests - Assert the updated end-user and project-developer contracts.

## Pre-conditions

- [ ] The Codex install prompt installs the plugin files and bundled client runtime without requiring `/cat:install`.

## Jobs

### Job 1

- Move local-development update behavior out of the shipped `cat:install` skill and into a project-only `/cat-update`
  workflow.
  - Files: `client/plugin/skills/common/install/`, project skill configuration files
- Update README and prompt documentation so end-users run the Codex install prompt for both first install and updates.
  - Files: `README.md`, `docs/prompts/codex-install.md`
- Add or update tests that fail if end-user bundles expose `/cat:install` as a required update step.
  - Files: release documentation and plugin packaging tests

## Post-conditions

- [ ] End-user Codex update instructions tell users to run the README prompt, not `/cat:install`.
- [ ] `/cat:install` is not exposed as an end-user update command in shipped CAT plugin artifacts.
- [ ] A project-only `/cat-update` workflow exists for local development updates.
- [ ] Release/documentation tests cover the split between end-user updates and local development updates.
- [ ] `mvn -f client/pom.xml verify -e` passes.
