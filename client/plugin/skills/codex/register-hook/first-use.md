<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Register Codex Hooks

CAT does not currently provide Codex project hook registration through a project-local settings file.

Codex plugin hooks are registered through `plugin/hooks/codex/hooks.json` and adapted by
`plugin/hooks/codex/run-hook.sh`. Do not create another runtime's hook files from Codex.

For CAT plugin hook changes, edit `plugin/hooks/common/` for shared documentation/helpers or
`plugin/hooks/codex/` for Codex-specific hook registration and adapter behavior. Restart, resume, or clear Codex
after changing hook registration.
