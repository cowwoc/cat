<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Session History

Use the Java `session-analyzer` tool for structured history queries. It parses JSONL as structured data, so it is
safer than grep for mega-line transcript entries.

## Subcommands

| Subcommand | Arguments | Description |
|------------|-----------|-------------|
| `analyze` | `<session-id-or-thread-id>` | Full session analysis. |
| `search` | `<session-id-or-thread-id> <keyword> [--context N]` | Find entries containing keyword with N context lines. |
| `errors` | `<session-id-or-thread-id>` | List tool results with error indicators. |
| `file-history` | `<session-id-or-thread-id> <path-pattern>` | Chronological Read/Write/Edit/Bash operations for a file. |

## Entry Types

- `type: "summary"`: conversation summary.
- `type: "message"`: user or assistant message.
- `type: "tool_use"`: tool invocation.
- `type: "tool_result"`: tool output.

## Additional Context Limitation

Hook `additionalContext` is injected into the agent context window and is not stored in JSONL logs.
`session-analyzer --runtime <runtime> search` can only find logged JSONL entries.

If a session or thread ID is unavailable, report the error. Do not guess IDs.
