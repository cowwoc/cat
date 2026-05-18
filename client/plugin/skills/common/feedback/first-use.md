<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Feedback: File CAT Feedback Publicly

File a CAT plugin bug report or feature request at `https://github.com/cowwoc/cat/issues`.

Do not publish anything until the user has seen the complete issue content and explicitly approves
public publication.

## Step 1: Classify Feedback

Ask the user what kind of feedback they want to file:

- Bug report
- Feature request

If the user already made the type clear, confirm the inferred type before proceeding. If
`/cat:feedback` was invoked with arguments, use them as the initial description but still run the
collection, preview, and approval steps.

## Step 2: Gather Context

Get the CAT version:

```bash
cat "${CAT_PROJECT_DIR}/.cat/VERSION" 2>/dev/null || echo "unknown"
```

For bug reports, collect or derive:

- Summary
- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment, including CAT version and relevant operating context
- Relevant error output, command output, preprocessor block, issue id, branch, or session context

If the bug relates to an agent mistake, workflow failure, verification failure, stale work, lock
mistake, or other process defect, invoke or follow the `/cat:learn` workflow before drafting the issue.
Use the learning output to capture root cause, prevention, and any M-record analysis that applies.

For feature requests, collect:

- Requested capability
- Motivation or use case
- Proposed solution, if the user has one
- Alternatives or constraints, if known

Ask only for missing information that materially changes the public issue. Do not invent facts.

## Step 3: Search Duplicates

Search GitHub issues before creating anything:

```bash
gh issue list \
  --repo cowwoc/cat \
  --state all \
  --search "<keywords from title and summary>" \
  --limit 10
```

If likely duplicates are found, show the matching issue numbers, titles, states, and URLs. Ask whether
to use an existing issue, continue with a new issue, or cancel. Stop if the user chooses an existing
issue or cancels.

If `gh` is unavailable or unauthenticated, tell the user what failed and stop before the preview. Do
not fall back to browser-prefilled issue creation.

## Step 4: Draft Issue

Build the title, labels, and body.

Bug report labels:

```text
bug
```

Bug report body:

```markdown
## Summary

<short description>

## Steps to Reproduce

<ordered steps, or "Unknown" if not available>

## Expected Behavior

<expected result>

## Actual Behavior

<actual result, including concise error snippets when relevant>

## Environment

- CAT Version: <version>
- Engine/Agent: <engine if known>
- Project/Issue Context: <issue id, branch, command, or "Unknown">

## Analysis

<root cause, prevention, and M-record analysis from /cat:learn when applicable>
```

Feature request labels:

```text
enhancement
```

Feature request body:

```markdown
## Description

<requested capability>

## Motivation

<user goal or use case>

## Proposed Solution

<proposal, or "No specific solution proposed">

## Alternatives and Constraints

<alternatives, constraints, or "Unknown">
```

## Step 5: Preview and Redaction

Show the complete issue exactly as it will be submitted:

- Repository: `cowwoc/cat`
- Title
- Labels
- Full body

Warn that GitHub issues are public. Ask the user to review the content and redact secrets, internal
URLs, tokens, customer data, private paths, or any other sensitive information before publication.

If the user asks to redact or revise anything, update the draft and show the full preview again.

## Step 6: Explicit Public Approval

After the final preview, ask for explicit permission to publish publicly. The approval option must
include the phrase:

```text
Yes, publish publicly
```

Treat cancellation, refusal, silence, ambiguous approval, or any response other than explicit
public-publish approval as a stop condition. If stopped, report that no issue was created.

## Step 7: Publish

Only after explicit public approval, create the issue with `gh issue create`:

```bash
body_file="$(mktemp)"
cat > "${body_file}" <<'EOF'
<final approved issue body>
EOF
gh issue create \
  --repo cowwoc/cat \
  --title "<final approved title>" \
  --body-file "${body_file}" \
  --label "<label>"
rm -f "${body_file}"
```

Report the created issue URL from `gh issue create`.
