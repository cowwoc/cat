# Plan

## Goal

Remove CLAUDE_ENV_FILE from ClaudeTool and ClaudeHook scopes — only InjectEnv should read it via System.getenv().

Currently, `MainClaudeTool` eagerly reads `CLAUDE_ENV_FILE` at construction time (line 47), causing all ~45 CLI tools
to require this env var even though none of them actually call `getEnvFile()`. The only production consumer of
`getEnvFile()` is `InjectEnv` (the SessionStart hook handler), which writes environment variables into that file.

This removal simplifies the scope interfaces, eliminates a construction-time requirement that serves no purpose for
CLI tools, and fixes preprocessor failures where `GetOutput` (via `MainClaudeTool`) fails because `CLAUDE_ENV_FILE`
is not set outside of SessionStart context.

## Pre-conditions

(none)

## Post-conditions

- [ ] `ClaudeTool` interface and implementations no longer reference `CLAUDE_ENV_FILE`
- [ ] `ClaudeHook` interface and implementations no longer reference `CLAUDE_ENV_FILE`
- [ ] `InjectEnv` reads `CLAUDE_ENV_FILE` directly via `System.getenv()` (added to whitelist in `EnforceJvmScopeEnvAccessTest`)
- [ ] User-visible behavior unchanged — all CLI tools and hooks function identically
- [ ] Tests passing — `mvn -f client/pom.xml verify -e` exits 0
- [ ] No regressions in existing functionality
- [ ] E2E: Invoke a CLI tool (e.g., `get-status-output`) and confirm it no longer requires `CLAUDE_ENV_FILE` to be set
