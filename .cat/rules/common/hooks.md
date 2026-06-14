---
agents: ["main"]
paths:
  - "client/plugin/hooks/**"
  - "client/*-cli/src/main/java/**/hook/**"
  - "client/*-cli/src/test/java/**/hook/**"
  - ".claude/settings.json"
  - ".codex/hooks.json"
  - ".codex/*hook*.sh"
  - ".codex/*session-start*.sh"
---
# Hook Guidance Loading Index

When creating, updating, debugging, or reviewing hook behavior:
Lazy load `../include/hooks.md`.
