# Plan

## Goal

Move CAT release installation back to `cowwoc/cat` GitHub Releases and provide `/cat:install` for both Codex and Claude Code. The install skill must download the selected runtime artifact from the release, unpack it, and install the plugin/runtime. Claude Code must not gain `/cat:uninstall`.

## Pre-conditions

(none)

## Post-conditions

- [ ] Claude Code exposes `/cat:install`.
- [ ] Codex continues to expose `/cat:install`.
- [ ] Claude Code does not expose `/cat:uninstall`.
- [ ] `/cat:install` downloads the latest CAT release by default from `cowwoc/cat` releases.
- [ ] `/cat:install` can install a specific version from `cowwoc/cat` releases.
- [ ] `/cat:install` unpacks and installs the runtime-specific plugin artifact and bundled client runtime.
- [ ] Documentation no longer instructs users to install from `cowwoc/cat-artifacts`.
- [ ] Release packaging and tests reflect that release assets are hosted on `cowwoc/cat`, not a separate artifact repository.
