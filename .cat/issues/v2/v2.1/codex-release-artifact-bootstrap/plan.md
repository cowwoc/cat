# Plan

## Goal

Document and enforce the release installation model where CAT releases are listed in the main `cowwoc/cat`
repository, all generated runtime artifacts live in `cowwoc/cat-artifacts`, Claude Code uses its built-in plugin
mechanism, and Codex bootstraps through the release's installer plugin before invoking `/cat:install`.

## Pre-conditions

(none)

## Post-conditions

- [ ] README Codex installation points users from main-repository releases to the Codex installer artifact.
- [ ] Plugin distribution docs describe main release entries as the release catalog and artifact repo refs as the
      install source for all runtime artifacts.
- [ ] `/cat:install` wording makes clear Codex release users invoke the release's installer plugin first.
- [ ] Claude Code does not ship CAT-owned install or uninstall skills.
- [ ] `/cat:uninstall` wording makes clear Codex release users run it before removing the installer plugin.
- [ ] The git-filter-repo fallback downloads from `cowwoc/cat-artifacts`.
- [ ] A test guards the documented Codex release install contract.
- [ ] Tests pass.
