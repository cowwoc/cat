# Hook Registration Locations (M406)

Two distinct hook registration systems exist. Using the wrong location causes hooks to not trigger.

| Hook Type | Registration Location | Use Case |
|-----------|----------------------|----------|
| **Project hooks** | `.claude/settings.json` | Project-specific behavior, custom validation |
| **Plugin hooks** | `plugin/hooks/hooks.json` | CAT plugin behavior, skill preprocessing |

## Project Hooks

Created via `/cat:register-hook` skill. Registered in `.claude/settings.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
      }
    ]
  }
}
```

## Plugin Hooks

Pre-registered in `plugin/hooks/hooks.json`. Loaded automatically by Claude Code plugin system.

**Do NOT attempt to register plugin hooks in settings.json** - they are already registered.

When investigating whether a plugin hook is active, check `plugin/hooks/hooks.json`, not `.claude/settings.json`.

## Approval Gate Protocol (M489)

When trust != "high", approval gates MUST use AskUserQuestion tool immediately. Do NOT ask conversational questions first.

**Wrong pattern:**
```
Agent: "Ready to merge when you are. Want to proceed with the approval gate?"
User: "yes"
Agent: *proceeds to merge* ❌
```

**Correct pattern:**
```
Agent: *immediately invokes AskUserQuestion with formal options*
User: *selects "Approve and merge" option*
Agent: *proceeds to merge* ✅
```

**Key principle:** Only explicit selection of "Approve and merge" option in AskUserQuestion constitutes approval. Conversational responses like "yes", "ok", "proceed" are NOT approval.
