---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1
You are the instruction-builder-agent working in Step 4.1. The skill being tested is a fictional skill
"log-analyzer-agent" with one semantic unit: unit_log_1 (REQUIREMENT: always summarize findings in a
table). Generate the test case for unit_log_1 using the .md format. Show the complete file content you
would write to plugin/tests/skills/log-analyzer-agent/first-use/unit_log_1.md.
## Assertions
1. response must produce a file path like plugin/tests/skills/log-analyzer-agent/first-use/unit_log_1.md
2. response must show markdown file content with YAML frontmatter block delimited by --- markers
3. frontmatter must include a category field
4. file must include a ## Turn 1 section containing a scenario prompt
5. file must include a ## Assertions section with at least one numbered assertion
7. response must NOT include any JSON structure with test_cases array or assertion_id fields
