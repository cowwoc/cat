---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
Please update instruction-builder for a transitive include chain:

- `A.md` includes `B.md`
- `B.md` includes `C.md`
- The text to edit appears in rendered `A.md` but originates from `C.md`
- The rendered token to edit is exactly `ORIGIN_TOKEN_C_ONLY` and appears only in `C.md`

Apply the change to the text source.

## Assertions
1. The Skill tool was invoked
2. The agent evaluated includes in rendered order for the transitive chain
3. The edit target was the true origin file (`C.md`), not `A.md` or `B.md`
4. No edits were applied to `A.md` or `B.md` for `ORIGIN_TOKEN_C_ONLY`
