---
subAgents: []
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Hook Registration Locations

Two distinct hook registration systems exist. Using the wrong location causes hooks to not trigger.

| Hook Type | Registration Location | Use Case |
|-----------|----------------------|----------|
| **Project hooks** | `.claude/settings.json` | Project-specific behavior, custom validation |
| **Portable plugin hook files** | `client/plugin/hooks/common/README.md` | Shared CAT plugin hook documentation/helpers |
| **Claude plugin hooks** | `client/plugin/hooks/claude/hooks.json` | CAT plugin behavior in Claude Code |
| **Codex plugin hooks** | `client/plugin/hooks/codex/hooks.json` | CAT plugin behavior in Codex |

`client/plugin/hooks/common/` is documentation/helper content only. Runtime hook registration lives in the
runtime-specific `hooks.json` files.

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

Pre-registered in runtime-specific plugin hook configs. Loaded automatically by the active plugin system.

**Do NOT attempt to register plugin hooks in settings.json** - they are already registered.

When investigating whether a plugin hook is active, check `client/plugin/hooks/claude/hooks.json` or
`client/plugin/hooks/codex/hooks.json`, not `.claude/settings.json`.

## Matcher Field Bug

To match all tools, **omit the `matcher` field entirely**. Do NOT use `"matcher": ""` — despite the docs claiming empty
string matches all, it silently fails to match anything (empirically verified 2026-02-19).

```json
// ✅ CORRECT: omit matcher entirely
{ "hooks": [{ "type": "command", "command": "my-hook.sh" }] }

// ❌ WRONG: empty string doesn't match
{ "matcher": "", "hooks": [{ "type": "command", "command": "my-hook.sh" }] }
```

## Approval Gate Protocol

When trust != "high", approval gates MUST use the active runtime's approval mechanism immediately.
Do NOT ask conversational questions first.

Runtime approval mechanisms:
- Claude Code: `AskUserQuestion`; chat approval is never valid.
- Codex Plan mode: `request_user_input`.
- Codex Default mode: verbal approval using a case-insensitive exact match for the same option labels, because
  `request_user_input` is not available.

If Claude Code does not expose `AskUserQuestion`, fail closed: do not merge and do not accept inline chat approval.

**Wrong pattern:**
```
Agent: "Ready to merge when you are. Want to proceed with the approval gate?"
User: "yes"
Agent: *proceeds to merge* ❌
```

**Correct pattern:**
```
Agent: *immediately invokes the runtime's approval mechanism with formal options*
User: *selects "Approve and merge" option*
Agent: *proceeds to merge* ✅
```

**Key principle:** Only explicit selection of the "Approve and merge" option in the runtime approval mechanism
constitutes approval. For Codex Default mode, the verbal response must be a case-insensitive exact match for a
presented option; casual responses like "yes", "ok", or "proceed" are not approval.
