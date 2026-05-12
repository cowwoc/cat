<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plugin Distribution

CAT keeps development sources split by audience, then builds runtime-specific install artifacts. End users should install
a release artifact for their runtime, not the development source tree.

## Source Layout

The development tree keeps shared and runtime-specific content separate:

| Source audience | Development path |
|-----------------|------------------|
| Portable content | `client/plugin/**/common/` and `client/plugin/skills/common/` |
| Claude Code-only content | `client/plugin/**/claude/` and `client/plugin/skills/claude/` |
| Codex-only content | `client/plugin/**/codex/` and `client/plugin/skills/codex/` |

These paths are authoring locations. Runtime loaders should not be asked to understand the source split directly.

Runtime-specific instruction files may inline always-loaded shared content with:

```markdown
<!-- cat:include path/relative/to/current/source/file.md -->
```

Use this only for content that is always loaded and is split solely because the source tree separates common and
runtime-specific files. Include paths are resolved relative to the file containing the directive. Do not inline
conditionally loaded concepts, tests, examples, or reference files.

## Install Artifacts

Release builds materialize one release artifact per runtime and version:

| Runtime | Artifact contents |
|---------|-------------------|
| Claude Code | portable content plus Claude-specific content, flattened into Claude Code's native plugin layout |
| Codex | portable content plus Codex-specific content, flattened into Codex's native plugin layout |

Codex artifacts are first-class install artifacts, but Codex behavior is not full Claude parity. Release notes and
customer-facing summaries must keep current gaps visible, including unsupported read/search hooks, task/skill hook
differences, statusline differences, and remaining formal-runner differences.

The release artifact must not contain files for the other runtime. This avoids wasting context on irrelevant
instructions and avoids exposing runtime-specific implementation details to the wrong product.

Each release artifact also includes the jlink client image under `client/` with a `client/VERSION` file matching the
runtime manifest version. SessionStart hooks must not download client binaries from GitHub; they may only verify the
installed runtime or acquire it from the bundled release artifact.

Source files may carry project license headers. During flattening, release processing strips those headers from
agent-facing Markdown and TOML files under `agents/`, `concepts/`, `rules/`, and `skills/` so installed runtime context
does not spend tokens on boilerplate.

The artifact builder verifies generated runtime roots before completing. Verification fails the build if agent-facing
release files still contain source license text, if source-only skill fixtures such as `tests/`, `instruction-test/`,
or `*.bats` are present, if `agents/common/` leaks into a runtime artifact, or if an include marker remains unresolved.

## Immutable Release Commits

The main `cowwoc/cat` repository is the release catalog. Its GitHub Releases list user-visible versions, release notes,
source tags, and links to installable artifact refs. Generated release artifacts are published to `cowwoc/cat-artifacts`
so cloning the source repository does not download generated plugin/runtime bundles.

Publish each release artifact at an isolated Git commit or immutable tag in `cowwoc/cat-artifacts`. Marketplace entries
should point to that artifact commit using an exact `sha` when possible.

Acceptable shapes:

1. **Preferred:** publish all generated artifacts to `cowwoc/cat-artifacts`, including Claude Code plugin artifacts,
   Codex installer/full-plugin artifacts, jlink runtime artifacts, and the self-contained `git-filter-repo` bundle used
   by CAT's git-rebase flow.
2. **Fallback/debug only:** publish orphan artifact branches in this repository when validating a release pipeline
   change before pushing to `cowwoc/cat-artifacts`.

Do not commit generated release artifacts under normal source-tree paths in `cowwoc/cat`.

## Runtime Support

CAT publishes release artifacts for both Claude Code and Codex. Claude Code is the full-parity runtime. Codex is a
first-class install artifact with documented partial parity until Codex exposes equivalent hook, statusline, and
formal-runner extension points.

| Runtime | Install source | Local update path | Support tier |
|---------|----------------|-------------------|--------------|
| Claude Code | Claude Code's built-in plugin mechanism | Built-in plugin upgrade/uninstall | Full CAT runtime support |
| Codex | Installer artifact, then `/cat:install` | `/cat:install` reinstalls the Codex artifact | First-class artifact with documented parity gaps |

Release notes and customer-facing summaries must use the same positioning: Codex installs and updates through a native
Codex artifact, but unsupported Codex extension points remain visible instead of being described as full Claude Code
parity.

Claude Code can install and uninstall CAT through Claude Code's built-in plugin mechanism. Codex cannot bootstrap the
full CAT plugin by invoking a CAT skill that is not installed yet, so each release publishes a small Codex installer
artifact. Users install that installer through Codex's plugin browser, run `/cat:install` to install the full CAT Codex
artifact, and run `/cat:uninstall` before removing the installer plugin.

Claude Code can install from Git-backed plugin sources and npm package sources. Codex can install from Git-backed and
local marketplace sources; Codex does not currently document npm package plugin sources.

Use Git-backed artifacts for parity:

```json
{
  "name": "cat",
  "source": {
    "source": "git-subdir",
    "url": "cowwoc/cat-artifacts",
    "path": "codex-installer",
    "ref": "v2.1.0",
    "sha": "<immutable-commit-sha>"
  }
}
```

Equivalent Codex CLI bootstrap:

```bash
codex plugin marketplace add cowwoc/cat-artifacts --ref <version> --sparse codex-installer
```

If an artifact commit root is already the plugin root, omit `path`.

## Local Builds

Local development should use the same flattening pipeline as releases, then install the generated artifact.

Primary flow: build to `client/distribution/target/runtime/{claude,codex}/`, copy the selected runtime root into a
generated local marketplace or active runtime-local plugin root, and reinstall through the runtime's normal plugin
layer. This is the parity test for release behavior.

Fallback/debug flow: install directly from an isolated local release commit when validating the exact public-release
shape. Do not use raw source-tree paths or symlinked development installs as the normal local update path.

The local artifact must contain the same files and layout that the published artifact commit will contain, including
the bundled `client/` runtime.
