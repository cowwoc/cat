<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Qualified Names
**MANDATORY**: Always use fully-qualified names when referencing issues, skills, and files.
Bare names exist only to facilitate easier user input; qualified names are required everywhere else.

**Issues** — Format: `{major}.{minor}-{bare-name}` (e.g., `2.1-create-config-property-enums`)

**Applies to**: All free-text responses — after adding issues, when suggesting next work, when
summarizing created issues, when referencing issues in any context.

**Never use** bare issue names (e.g., `create-config-property-enums`) in agent-to-user text.

**Skills** — Format: `{prefix}:{bare-name}` (e.g., `cat:git-rebase`)

**Applies to**: All free-text responses, planning files, SKILL.md content, and agent instructions
when referring to skills by name.

**Never use** bare skill names (e.g., `git-rebase`) in responses, planning files, SKILL.md content, or agent instructions.

**Files** — Use full paths relative to the project root (e.g., `plugin/rules/common/qualified-issue-names.md`)

**Applies to**: All free-text responses, planning files, skill documentation, and any context where files are referenced by name.

**Note**: The project root refers to the repository root directory, not the worktree directory. File paths should be relative to the main repository structure regardless of whether referenced from the main workspace or a worktree.

**Never use** bare file names (e.g., `qualified-issue-names.md`) without their directory path.
