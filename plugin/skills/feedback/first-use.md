---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Feedback: File a Bug Report

File a bug report for a CAT plugin issue on GitHub. Checks for duplicates before creating a new issue.

## Step 1: Gather Context

Collect information needed for the bug report:

1. Get the CAT version:
   ```bash
   cat "${CLAUDE_PROJECT_DIR}/.claude/cat/VERSION" 2>/dev/null || echo "unknown"
   ```

2. Scan recent conversation messages for errors to report. Look for:
   - Preprocessor errors (formatted as `**Preprocessor Error**` blocks)
   - Build failures or compilation errors
   - Plugin errors or unexpected failures
   - Any error the user wants to report

3. If `/cat:feedback` was invoked with arguments, use those as additional context (e.g., a description
   of what went wrong).

## Step 2: Present Wizard

Use AskUserQuestion to present the detected issue to the user for confirmation. Show:

- **Detected issue title** (suggested from error context, or ask user to describe it)
- **Category** with options:
  - Preprocessor error
  - Build failure
  - Feature request
  - Other bug

Ask the user to confirm the title or provide a corrected one. If no error was detected in the
conversation, ask the user to describe the issue they want to report.

Example wizard prompt:
```
I found a preprocessor error in the recent conversation. Would you like to file a bug report?

Suggested title: "Preprocessor error: <brief description>"
Category: Preprocessor error

Please confirm or edit the title before I search for duplicates.
```

## Step 3: Search for Duplicates

Always search GitHub for existing issues before creating a new one. Use the `github-feedback.sh` script with keywords
from the confirmed title:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/github-feedback.sh" search "<keywords from title>"
```

The script returns JSON with an `issues` array. Each element has `number`, `title`, `url`, and `state`.

**If matching issues are found**, display them to the user and ask via AskUserQuestion:

```
AskUserQuestion:
  header: "Duplicates"
  question: "Found N existing issues that may match. How would you like to proceed?"
  options:
    - "Subscribe to #<number>" (for each matching issue, up to 3)
    - "Create new issue" (none of the matches are duplicates)
    - "Cancel"
```

If the user selects an existing issue, add a comment or reaction to subscribe and stop.

**If no matching issues are found**, proceed directly to Step 4.

## Step 4: Open Issue in Browser

Build the issue body with diagnostic context:

```markdown
## Summary

<user-provided description of the issue>

## Environment

- **CAT Version:** <version from Step 1>
- **Error:** <error message if applicable>
- **Directive:** <preprocessor directive if applicable>

## Steps to Reproduce

<any reproduction steps derived from the error context>
```

Open the GitHub issue creation page in the user's browser using the `github-feedback.sh` script:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/github-feedback.sh" open \
  "Title here" \
  "Body here" \
  "bug"
```

The script constructs a pre-filled GitHub issue URL. No authentication token is required — the user's
existing browser session handles GitHub login.

The script returns JSON with `status` and `url` fields:

- `status: "opened"` — the browser opened successfully; the URL was loaded in the user's browser.
- `status: "url_only"` — the browser was unavailable (e.g., headless environment); the user must open
  the URL manually. A `message` field explains why the browser could not be opened.

## Step 5: Confirm

After opening the browser or finding a duplicate issue, display the result to the user:

- If `status` is `"opened"`: "The issue creation page was opened in your browser. Please review the
  pre-filled details and click 'Submit new issue' to file the report."
- If `status` is `"url_only"`: "Unable to open a browser automatically. Please open this URL to file
  the report:\n\n`<url from JSON response>`"
- If duplicate found and user subscribed: "You are now subscribed to issue #N at [URL]."
- If cancelled: "Cancelled. No issue was created."
