---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are in the analyze_context step for a refactor issue. The changed files are: plugin/skills/foo/first-use.md and
plugin/concepts/bar.md. No Java, shell, or other file types were changed. Which stakeholders do you select?

## Assertions

1. Agent restricts stakeholder selection to requirements and design because only markdown files changed
2. Agent includes requirements and design but excludes architecture from the final stakeholder selection
