<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plugin Distribution

CAT keeps development sources split by audience, then builds runtime-specific install artifacts. End users should install
a flattened artifact for their runtime, not the development source tree.

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

Release builds materialize one flattened plugin root per runtime and version:

| Runtime | Artifact contents |
|---------|-------------------|
| Claude Code | portable content plus Claude-specific content, flattened into Claude Code's native plugin layout |
| Codex | portable content plus Codex-specific content, flattened into Codex's native plugin layout |

Codex artifacts are first-class install artifacts, but Codex behavior is not full Claude parity. Release notes and
customer-facing summaries must keep current gaps visible, including unsupported read/search hooks, task/skill hook
differences, statusline differences, and remaining formal-runner differences.

The flattened artifact must not contain files for the other runtime. This avoids wasting context on irrelevant
instructions and avoids exposing runtime-specific implementation details to the wrong product.

Each flattened artifact also includes the jlink client image under `client/` with a `client/VERSION` file matching the
runtime manifest version. SessionStart hooks must not download client binaries from GitHub; they may only verify the
installed runtime or acquire it from the bundled flattened artifact.

Source files may carry project license headers. During flattening, release processing strips those headers from
agent-facing Markdown and TOML files under `agents/`, `concepts/`, `rules/`, and `skills/` so installed runtime context
does not spend tokens on boilerplate.

The artifact builder verifies generated runtime roots before completing. Verification fails the build if agent-facing
release files still contain source license text, if source-only skill fixtures such as `tests/`, `instruction-test/`,
or `*.bats` are present, if `agents/common/` leaks into a runtime artifact, or if an include marker remains unresolved.

## Immutable Release Commits

Publish each flattened artifact at an isolated Git commit or immutable tag. Marketplace entries should point to that
artifact commit using an exact `sha` when possible.

Acceptable shapes:

1. **Separate artifact repository:** publish `claude/vX.Y.Z` and `codex/vX.Y.Z` commits or tags that contain flattened
   plugin roots. This gives users the cleanest install source.
2. **Orphan artifact branches in this repository:** publish one flattened root per runtime on branches such as
   `artifact/claude/vX.Y.Z` and `artifact/codex/vX.Y.Z`. This avoids a second repository while keeping artifacts
   isolated from development sources.
3. **Committed `dist/{runtime}` directories:** tag the source repository and point marketplace entries at
   `dist/claude` or `dist/codex` via `git-subdir`. This is simplest, but it keeps generated artifacts in the main
   source tree and is less clean for review.

Preferred: separate artifact repository or orphan artifact branches. Both keep the source repository clean and let
runtime marketplaces install only the flattened files.

## Runtime Support

CAT publishes flattened artifacts for both Claude Code and Codex. Claude Code is the full-parity runtime. Codex is a
first-class install artifact with documented partial parity until Codex exposes equivalent hook, statusline, and
formal-runner extension points.

| Runtime | Install source | Local update path | Support tier |
|---------|----------------|-------------------|--------------|
| Claude Code | Flattened Claude Git artifact or npm package source | `cat:install` builds `client/distribution/target/runtime/claude/` and reinstalls that artifact | Full CAT runtime support |
| Codex | Flattened Codex Git artifact or local Codex marketplace source | `cat:install` builds `client/distribution/target/runtime/codex/` and reinstalls that artifact through the active Codex marketplace/cache flow | First-class artifact with documented parity gaps |

Release notes and customer-facing summaries must use the same positioning: Codex installs and updates through a native
Codex artifact, but unsupported Codex extension points remain visible instead of being described as full Claude Code
parity.

Claude Code can install from Git-backed plugin sources and npm package sources. Codex can install from Git-backed and
local marketplace sources; Codex does not currently document npm package plugin sources.

Use Git-backed artifacts for parity:

```json
{
  "name": "cat",
  "source": {
    "source": "git-subdir",
    "url": "cowwoc/cat-artifacts",
    "path": "codex",
    "ref": "v2.1.0",
    "sha": "<immutable-commit-sha>"
  }
}
```

If the artifact commit root is already the plugin root, omit `path`.

## Local Builds

Local development should use the same flattening pipeline as releases, then install the generated artifact.

Primary flow: build to `client/distribution/target/runtime/{claude,codex}/`, copy the selected runtime root into a
generated local marketplace or active runtime-local plugin root, and reinstall through the runtime's normal plugin
layer. This is the parity test for release behavior.

Fallback/debug flow: install directly from an isolated local release commit when validating the exact public-release
shape. Do not use raw source-tree paths or symlinked development installs as the normal local update path.

The local artifact must contain the same files and layout that the published artifact commit will contain, including
the bundled `client/` runtime.
