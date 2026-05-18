<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Hooks Infrastructure

CAT hooks are split by engine. Engine-specific hook registration files live under `plugin/hooks/<engine>/`;
engine-specific Java entrypoints live under their engine package, and reusable behavior lives in neutral shared
packages.

Codex hook support is intentionally partial, not a full Claude Code parity claim. CAT ships Codex hook adapters for
the events Codex exposes today, and leaves unsupported Claude-only surfaces documented below instead of hiding them
behind no-op compatibility shims.

## Directory Layout

```text
plugin/hooks/
|-- common/
|   `-- README.md
|-- claude/
|   |-- hooks.json
|   `-- session-start.sh
`-- codex/
    `-- hooks.json
```

## Engine Flow

Claude Code uses its native hook names and invokes CAT launchers from the installed client engine:

```text
Claude Code hook event
  -> plugin/hooks/claude/hooks.json
  -> plugin/hooks/claude/session-start.sh or ${CLAUDE_PLUGIN_ROOT}/client/bin/<launcher>
  -> jlink engine
  -> Java handler class
```

Codex uses its hook configuration and invokes Codex-native Java entrypoints from the bundled engine:

```text
Codex hook event
  -> plugin/hooks/codex/hooks.json
  -> ${CAT_PLUGIN_ROOT}/client/bin/<handler>
  -> jlink engine
  -> Codex Java entrypoint
  -> native Codex payload parser
  -> neutral shared helper, when applicable
```

Codex Java entrypoints parse native Codex hook payloads directly, derive project context from the payload and
environment, and invoke only neutral shared code. They do not invoke Claude Java handlers.

## Files

| File | Purpose |
|------|---------|
| `plugin/hooks/claude/hooks.json` | Registers Claude Code hook events and maps them to CAT launchers. |
| `plugin/hooks/claude/session-start.sh` | Claude SessionStart bootstrap. Verifies the bundled jlink engine before invoking session-start handlers. |
| `plugin/hooks/codex/hooks.json` | Registers Codex hook events and maps them to bundled CAT launchers. |
| `plugin/hooks/common/README.md` | Engine-neutral hook infrastructure documentation. |

## Hook Coverage

| Capability | Claude Code | Codex |
|------------|-------------|-------|
| Session start | `SessionStart` via `session-start.sh` | `SessionStart` via `client/bin/session-start` |
| User prompt submit | `UserPromptSubmit` | Not implemented |
| Bash pre-hook | `PreToolUse` for `Bash` | `PreToolUse` for `Bash` and `functions.exec_command` |
| Bash post-hook | `PostToolUse` for `Bash` | Not implemented |
| Write/edit pre-hook | `PreToolUse` for `Write|Edit` | Not implemented |
| Stop/status enforcement | `Stop` | Not implemented |
| Session end | `SessionEnd` | Not implemented |
| Read/glob/grep hooks | Supported by Claude hook matchers | Not currently supported by Codex hooks |
| Task/skill hooks | Supported by Claude hook matchers | Not currently exposed as Claude-compatible Codex hook payloads |
| Subagent start | `SubagentStart` | Not implemented |

CAT currently registers Codex hooks for session-start context loading and the Bash pre-hook guard. It does not ship
no-op Codex launchers for Claude-only behavior, and it does not attempt to port Claude-only `Read|Glob|Grep`,
`Task|Skill`, prompt, post-tool, stop/status, or session-end hook behavior until Codex exposes compatible engine
events that CAT can handle meaningfully.

CAT intentionally does not emulate Claude Code's `SubagentStart` hook for Codex. Claude Code needs that hook because
Claude agents do not automatically receive CAT's lightweight agent rules and skill-listing context. Codex
agents use Codex sessions/configuration and receive native skill discovery for their effective configuration, so
duplicating the Claude injection would waste context and risk conflicting with Codex's own skill mechanism.

## jlink Engine

The jlink image is a self-contained JDK engine with only the modules needed for hook execution. It includes the CAT
client application JAR, JSON processing dependencies, and logging.

Benefits:

- Smaller than a full JDK distribution.
- Self-contained, so engine hooks do not require a system Java install.
- Uses generated launcher scripts for each Java handler.

Engine structure:

```text
engine/client/
|-- bin/
|   |-- java
|   |-- pre-bash
|   `-- ...
`-- lib/
    `-- server/
        `-- aot-cache.aot
```

## Handler Registry

Most hook handlers are registered by the client build as `launcher-name:ClassName`. The build generates a
`bin/<launcher-name>` shell script for each entry.

| Launcher | Class | Engine usage |
|----------|-------|---------------|
| `pre-bash` | `PreToolUseHook` / `PreBashHook` | Claude and Codex Bash pre-hooks |
| `post-bash` | `PostBashHook` | Claude Bash post-hooks |
| `pre-write` | `PreWriteHook` | Claude write/edit pre-hooks |
| `post-tool-use` | `PostToolUseHook` | Claude post-tool-use handling |
| `user-prompt-submit` | `UserPromptSubmitHook` | Claude prompt hooks |
| `session-end` | `SessionEndHook` | Claude session end |
| `enforce-status` | `EnforceStatusOutput` | Claude stop/status enforcement |

Session start is a engine bootstrap path rather than a generated launcher:

| Engine | Entry point | Handler |
|---------|-------------|---------|
| Claude | `plugin/hooks/claude/session-start.sh` | `io.github.cowwoc.cat.claude.hook.SessionStartHook` |
| Codex | `client/bin/session-start` | `io.github.cowwoc.cat.codex.hook.SessionStartHook` |

Handlers are engine-specific when the hook payloads or event model differ. Engine-specific code should stay under
the engine package. Reusable logic should live under a neutral package and should not depend on Claude or Codex
entrypoints.

## Development

During release validation, reinstall from the published or staged release artifact. The release artifact comes from
`cowwoc/cat` GitHub Releases and includes the bundled jlink engine used by the active engine.

Troubleshooting:

- If a Claude hook produces no output, check `plugin/hooks/claude/hooks.json` and the bundled engine under
  `${CLAUDE_PLUGIN_ROOT}/client/bin/`.
- If a Codex hook produces no output, check `plugin/hooks/codex/hooks.json`, the bundled engine under
  `${CAT_PLUGIN_ROOT}/client/bin/`, and the installed plugin cache under `~/.codex/plugins/cache/`.
- If the jlink build fails, verify that the configured JDK version is installed and that `mvn -f client/pom.xml verify -e` passes.
