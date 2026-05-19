<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Worktree Isolation

Apply all rules from `plugin/rules/common/worktree-isolation.md` plus these Claude specifics:

- Run Git commands with `cd "${WORKTREE_PATH}" && git ...` in a single call.
- Build all file paths as absolute `${WORKTREE_PATH}/...`.
- Use engine-appropriate plugin root (`${CLAUDE_PLUGIN_ROOT}` in Claude flows) when invoking plugin binaries.
