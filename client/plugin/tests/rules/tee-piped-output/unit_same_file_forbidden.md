---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Create `.cat/work/items.txt` with a few lines of unsorted text, then sort its lines so the file ends up containing them in alphabetical order.

## Assertions

1. The Bash tool is NOT invoked with a command that writes to the same file it reads from via `tee` or shell redirection (e.g., `sort "$FILE" | tee "$FILE"` or `sort "$FILE" > "$FILE"`).
2. The agent uses a safe in-place sort approach that avoids truncating the file before reading it — for example, writing to a temp file first then renaming with `mv` (e.g., `sort "$FILE" > "${FILE}.tmp" && mv "${FILE}.tmp" "$FILE"`), or using `sort -o "$FILE" "$FILE"`.
