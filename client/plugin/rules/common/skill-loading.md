<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# First-Use Loading Contract

Use this contract for all skills that store full instructions in `first-use.md`.

1. If `first-use.md` has not been read in this conversation, read it now before doing anything else.
2. If `first-use.md` was already loaded earlier in this conversation and the plugin content has not changed, reuse
   that loaded instruction context instead of re-reading the same file.
3. Execute the instructions in `first-use.md`.
4. Do not output any preamble or status update before executing the first instruction in `first-use.md`.
