---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Configure CAT Statusline

Install and configure Claude Code's statusline to display CAT context information including git worktree, model name,
session duration, session ID, and a color-coded context usage bar.

<objective>

Configure the user's Claude Code statusline to use CAT's custom statusline script. The script displays:
- Git worktree/branch name (or "N/A" if not in a git repo)
- Model display name
- Session duration (formatted as human-readable time)
- Session ID (first 8 characters)
- Color-coded context usage bar (green < 50%, yellow 50-80%, red > 80%)

</objective>

---

<process>

<step name="check-existing">

**Check for existing statusline configuration:**

Read the status from inside the latest `<output skill="statusline">` tag. Parse the JSON and determine the next step:

| Status | Meaning | Action |
|--------|---------|--------|
| `NONE` | No existing statusLine config | Continue to step: install |
| `EXISTING` | statusLine already configured | Continue to step: ask-overwrite |
| `ERROR` | Check failed | Display the error message from JSON output and STOP |

</step>

<step name="ask-overwrite">

**Ask user for permission to overwrite:**

If a `statusLine` configuration was found in the previous step, use AskUserQuestion:

- header: "Existing Statusline Configuration"
- question: "A statusline configuration already exists. Do you want to overwrite it with CAT's statusline?"
- options:
  - label: "Yes, overwrite"
    description: "Replace with CAT statusline"
  - label: "No, keep existing"
    description: "Keep current configuration"

If user selects "No, keep existing":

```
Keeping existing statusline configuration. No changes made.
```

Then STOP. This is a user choice, not an error.

If user selects "Yes, overwrite", continue to step: install

</step>

<step name="install">

**Install statusline:**

Run the install command:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/statusline-install.sh" --install "${CLAUDE_PROJECT_DIR}" "${CLAUDE_PLUGIN_ROOT}"
```

Parse the JSON output and determine the result:

| Status | Meaning | Action |
|--------|---------|--------|
| `OK` | Installation successful | Continue to step: confirm |
| `ERROR` | Installation failed | Display the error message from JSON output and STOP |

</step>

<step name="confirm">

**Confirm success:**

Output the following message:

```
✅ CAT statusline configured successfully!

The statusline will display:
- 🌿 Git worktree/branch name
- 🤖 Model name
- ⏱️  Session duration
- 📋 Session ID (first 8 chars)
- 📊 Context usage bar (color-coded)

The statusline will appear at the bottom of your Claude Code window in the next session.
Restart Claude Code or start a new session to see it in action.
```

</step>

</process>

<success_criteria>

- [ ] `.claude/` directory exists in project root
- [ ] `statusline-command.sh` copied to `.claude/statusline-command.sh` with mode 755
- [ ] `.claude/settings.json` contains `statusLine` configuration with correct `type` and `command` values
- [ ] If existing `statusLine` configuration was found, user was asked before overwriting
- [ ] If user declined overwrite, skill exited gracefully without error
- [ ] Success message displayed to user

</success_criteria>
<output skill="statusline">
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-statusline-output"`
</output>
