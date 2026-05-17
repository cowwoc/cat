# Plan

## Goal

Bundle git-filter-repo into the jlink runtime artifact so installed CAT clients use the bundled executable and fail fast if it is missing instead of downloading it at first use.

## Pre-conditions

(none)

## Post-conditions

- [ ] The jlink runtime artifact includes a platform-appropriate git-filter-repo executable under the CAT plugin root, such as lib/git-filter-repo or bin/git-filter-repo.
- [ ] CAT's git-rewrite-history flow resolves and uses the bundled git-filter-repo executable.
- [ ] CAT fails fast with a clear error if the bundled git-filter-repo executable is missing or not executable.
- [ ] First-use download behavior is removed or bypassed for runtime installs.
- [ ] Obsolete Bats tests for download/cache/curl fallback behavior are removed or rewritten to cover the bundled fail-fast behavior.
- [ ] The Bats suite and Maven verification pass.
