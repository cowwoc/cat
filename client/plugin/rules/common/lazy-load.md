<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Lazy Loading

A parent rule declares:
- `Lazy load`: the file containing the full rule body or topic body. Relative includes resolve from the parent rule file.

When a parent rule applies this convention:
1. Load each `Lazy load` file at most once per conversation context. If the conversation is compacted or resumed and
   you are no longer sure the included file is in context, load it again before touching a matching file.
2. Do not load the included file preemptively when no matching file is being inspected or modified.
3. After loading the included file, follow its instructions for all matching files.
