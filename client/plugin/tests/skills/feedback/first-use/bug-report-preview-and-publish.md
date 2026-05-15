---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

The plugin just failed while running /cat:work. File feedback.

## Assertions

1. The agent asks whether the feedback is a bug report or a feature request before publishing.
2. For a bug report, the agent collects or derives a summary, reproduction steps, expected behavior, actual behavior, environment, and relevant session context.
3. The agent uses the learn workflow to collect root cause or prevention analysis when a mistake or failure context is available.
4. The agent searches for duplicate GitHub issues before creating a new issue.
5. The agent shows the full issue title, labels, and body to the user before publishing.
6. The agent warns that the issue will be public and asks the user to redact sensitive information before publishing.
7. The agent asks for explicit permission to publish publicly after the preview.
8. The agent does not run `gh issue create` until the user explicitly approves public publication.
9. After explicit approval, the agent creates the issue with `gh issue create` in the `cowwoc/cat` repository.
