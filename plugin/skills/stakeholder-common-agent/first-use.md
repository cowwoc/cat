<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Working Directory

The delegation prompt MUST specify a working directory. Read and modify files ONLY within that directory. Do NOT access
files outside it.

## Mandatory Pre-Review Steps

Before analyzing any code, you MUST complete these steps in order:

1. **Analyze the diff**: Review the git diff summary provided in "What Changed" section. List every file that was
   modified, added, or deleted.
2. **Read all modified files**: For each modified file listed in the diff, read the full file content provided in
   the "Files to Review" section. Do not skip any file.
3. **Note cross-file relationships**: Identify any patterns, interfaces, or dependencies that span multiple
   modified files.

These steps must be completed before forming any review opinions.
