<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Hooks Infrastructure

CAT hooks are split by runtime. Runtime-specific hook registration files live under `plugin/hooks/<runtime>/`;
shared behavior lives in the Java hook handlers under `client/cli/src/main/java`.

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
    |-- hooks.json
    `-- run-hook.sh
```

## Runtime Flow

Claude Code uses its native hook names and invokes CAT launchers from the installed client runtime:

```text
Claude Code hook event
  -> plugin/hooks/claude/hooks.json
  -> plugin/hooks/claude/session-start.sh or ${CLAUDE_PLUGIN_DATA}/client/bin/<launcher>
  -> jlink runtime
  -> Java handler class
```

Codex uses its hook configuration and adapts Codex hook payloads into the Java handlers' shared input model:

```text
Codex hook event
  -> plugin/hooks/codex/hooks.json
  -> plugin/hooks/codex/run-hook.sh <handler>
  -> jlink runtime
  -> Java handler class
```

`run-hook.sh` resolves the installed plugin root, derives project context from the Codex hook payload, exports CAT
environment variables, and invokes the appropriate Java handler.

## Files

| File | Purpose |
|------|---------|
| `plugin/hooks/claude/hooks.json` | Registers Claude Code hook events and maps them to CAT launchers. |
| `plugin/hooks/claude/session-start.sh` | Claude SessionStart bootstrap. Verifies or installs the bundled jlink runtime before invoking session-start handlers. |
| `plugin/hooks/codex/hooks.json` | Registers Codex hook events and maps them to `run-hook.sh` handlers. |
| `plugin/hooks/codex/run-hook.sh` | Codex adapter. Normalizes Codex payloads and invokes CAT Java handlers. |
| `plugin/hooks/common/README.md` | Runtime-neutral hook infrastructure documentation. |

## Hook Coverage

| Capability | Claude Code | Codex |
|------------|-------------|-------|
| Session start | `SessionStart` via `session-start.sh` | `SessionStart` via `run-hook.sh codex-session-start` |
| User prompt submit | `UserPromptSubmit` | `UserPromptSubmit` |
| Bash pre-hook | `PreToolUse` for `Bash` | `PreToolUse` for `Bash` and `functions.exec_command` |
| Bash post-hook | `PostToolUse` for `Bash` | `PostToolUse` for `Bash` and `functions.exec_command` |
| Write/edit pre-hook | `PreToolUse` for `Write|Edit` | `PreToolUse` for `Edit|Write|apply_patch|functions.apply_patch` |
| Stop/status enforcement | `Stop` | `Stop` |
| Read/glob/grep hooks | Supported by Claude hook matchers | Not currently supported by Codex hooks |
| Task/skill hooks | Supported by Claude hook matchers | Not currently exposed as Claude-compatible Codex hook payloads |
| Subagent start | `SubagentStart` | Not implemented |

Codex currently exposes hooks for Bash, `apply_patch`, MCP tools, prompt submission, session start, and stop events.
It does not intercept all built-in tools, so CAT does not attempt to port Claude-only `Read|Glob|Grep` or
`Task|Skill` hook behavior until Codex exposes compatible events.

CAT intentionally does not emulate Claude Code's `SubagentStart` hook for Codex. Claude Code needs that hook because
Claude subagents do not automatically receive CAT's lightweight subagent rules and skill-listing context. Codex
subagents use Codex sessions/configuration and receive native skill discovery for their effective configuration, so
duplicating the Claude injection would waste context and risk conflicting with Codex's own skill mechanism.

## jlink Runtime

The jlink image is a self-contained JDK runtime with only the modules needed for hook execution. It includes the CAT
client application JAR, JSON processing dependencies, and logging.

Benefits:

- Smaller than a full JDK distribution.
- Self-contained, so runtime hooks do not require a system Java install.
- Uses generated launcher scripts for each Java handler.

Runtime structure:

```text
runtime/client/
|-- bin/
|   |-- java
|   |-- pre-bash
|   |-- pre-write
|   |-- post-tool-use
|   `-- ...
`-- lib/
    `-- server/
        `-- aot-cache.aot
```

## Handler Registry

Most hook handlers are registered by the client build as `launcher-name:ClassName`. The build generates a
`bin/<launcher-name>` shell script for each entry.

| Launcher | Class | Runtime usage |
|----------|-------|---------------|
| `pre-bash` | `PreToolUseHook` | Claude and Codex Bash pre-hooks |
| `post-bash` | `PostBashHook` | Claude and Codex Bash post-hooks |
| `pre-write` | `PreWriteHook` | Claude write/edit and Codex apply_patch pre-hooks |
| `post-tool-use` | `PostToolUseHook` | Shared post-tool-use handling where payloads are compatible |
| `user-prompt-submit` | `UserPromptSubmitHook` | Claude and Codex prompt hooks |
| `session-end` | `SessionEndHook` | Claude session end |
| `enforce-status` | `EnforceStatusOutput` | Claude and Codex stop/status enforcement |

Session start is a runtime bootstrap path rather than a generated launcher:

| Runtime | Entry point | Handler |
|---------|-------------|---------|
| Claude | `plugin/hooks/claude/session-start.sh` | `io.github.cowwoc.cat.claude.hook.SessionStartHook` |
| Codex | `plugin/hooks/codex/run-hook.sh codex-session-start` | `io.github.cowwoc.cat.codex.hook.SessionStartHook` |

Some handlers are runtime-specific because the hook payloads differ. Runtime-specific code should stay under the
runtime package; shared logic should live under the neutral `agent` package or another neutral package.

## Development

Use `/cat-install` during development after changing Java source under `client/cli/src/` or plugin source under
`client/plugin/`. It builds the flattened runtime artifact under `client/distribution/target/runtime/<runtime>/`,
reinstalls the active runtime from that artifact, and installs the bundled jlink runtime into the plugin cache used by
the active runtime.

Troubleshooting:

- If a Claude hook produces no output, check `plugin/hooks/claude/hooks.json`, the bundled runtime under
  `${CLAUDE_PLUGIN_ROOT}/client/bin/`, and the installed runtime under `${CLAUDE_PLUGIN_DATA}/client/bin/`.
- If a Codex hook produces no output, check `plugin/hooks/codex/hooks.json`, `plugin/hooks/codex/run-hook.sh`, and the installed plugin cache under `~/.codex/plugins/cache/`.
- If the jlink build fails, verify that the configured JDK version is installed and that `mvn -f client/pom.xml verify -e` passes.
