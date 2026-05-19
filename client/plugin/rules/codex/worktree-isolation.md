<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Worktree Isolation

Apply all rules from `plugin/rules/common/worktree-isolation.md` plus these Codex specifics:

- Use command-scoped working directory for Git operations (`workdir` set to `${WORKTREE_PATH}`) or `cd "${WORKTREE_PATH}" && git ...` in a single command.
- Do not rely on persistent `cd` across turns/commands.
- Build all file paths as absolute `${WORKTREE_PATH}/...`.
