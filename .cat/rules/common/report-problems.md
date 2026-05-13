---
paths: ["*"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Report Problems

If you notice broken or stale references (for example: removed symbols still referenced, references to items
that are not defined anywhere, outdated file paths, invalid tool/skill names, contradictory instructions, or
documentation drift), report them to the user immediately.

The goal is to surface problems that would otherwise degrade agent performance silently.

When reporting:
- State what appears broken or stale.
- Point to concrete locations (file and line when available).
- Describe likely impact on correctness or execution quality.
- Continue with the safest valid path, or stop and ask for direction if the issue blocks reliable progress.
