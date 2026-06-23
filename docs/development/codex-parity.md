<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex Parity Notes

This note captures Codex hook-porting and runner-parity decisions for future work. CAT supports
the latest Codex version only. The current work is runtime-specific packaging plus partial Codex parity, not a claim that CAT has
equivalent runtime behavior across Claude Code and Codex. Codex should be positioned as a first-class CAT install
artifact with explicit parity gaps, not as a full substitute for Claude Code runtime behavior.

## Codex Diagnostics

Use Codex-native diagnostics first when CAT behavior is unexpected:

```bash
codex doctor
codex doctor --json
```

CAT docs and support workflows assume current Codex diagnostics behavior. CAT does not provide backward-compatibility
workarounds for older Codex diagnostic surfaces.

## Hooks Ported To Codex

- `SessionStart`: Codex receives portable `.cat/rules/common/*.md` files plus `.cat/rules/codex/` main-agent
  rules and shared critical-thinking context. CAT's matcher includes `startup|resume|clear`, including
  SessionStart `/clear` flows. When Codex provides subagent identity on SessionStart payloads, CAT requires
  top-level `agent_type` (Codex 0.134.0+) to load agent-targeted rules.
- `SubagentStart`: Codex 0.134.0 exposes subagent-scoped lifecycle hooks with top-level `agent_id` and
  `agent_type`. CAT registers `SubagentStart` to inject agent-targeted rules without running SessionStart-only
  migration or main-agent context handlers.
- `UserPromptSubmit`: Codex payloads are adapted into the existing prompt hook.
- `PreToolUse` / `PostToolUse` for Bash: Codex `Bash` and `functions.exec_command` are adapted into
  Claude-compatible Bash payloads.
- `PostToolUse`: the general post-tool handler runs for Codex tool results.
- `PostToolUseFailure`: Codex has no separate failure event, so `PostToolUse` invokes the failure handler only when
  the adapted result indicates an error or non-zero exit.
- `Stop`: Codex `Stop` invokes the existing status enforcement hook.
- `Write|Edit`: Codex `apply_patch` is parsed into per-file write/edit checks and routed through the existing Java
  `pre-write` handler.

## Hooks Not Ported Yet

- `Read|Glob|Grep`: these hooks mainly inject path-restricted rules/skills when Claude reads matching files, plus
  read-side worktree isolation. Codex hooks currently do not cover broad read/search built-ins, and Codex already has
  native skill discovery. Write-side path guidance now covers file modifications.
- `Task|Skill`: this hook is workflow safety, not generic task execution. It blocks `cat:work-execute` agent spawns
  when the issue worktree has uncommitted changes, blocks `cat:work-merge` when cwd is inside the worktree that will
  be deleted, and enforces explicit approval before merge. Codex does not expose Claude-compatible `Task|Skill` hook
  payloads.

## Approval Gate Parity

The merge approval gate is runtime-specific:
- Claude Code uses `AskUserQuestion`.
- Codex uses `request_user_input` when that tool is available in the current context. Codex agents cannot switch
  collaboration modes themselves; `request_user_input` is available only in Plan mode.
- Codex Default mode falls back to verbal approval with the same option labels, matched case-insensitively.

Claude Code remains structured-only: if trust is not `high` and `AskUserQuestion` is unavailable, CAT fails closed and
must not merge. Codex Default mode may ask verbally, but only a case-insensitive exact response matching a presented
option such as `Approve and merge` writes the approval marker; casual responses like `yes`, `ok`, or `proceed` are
rejected.

## Codex `apply_patch` Limitations

The Codex write/edit adapter extracts changed paths from `apply_patch` text and blocks or injects context per file.
Add-file hunks include content, so add-file content validators can run.
Update/delete/move hunks currently run path-level safety checks only; full post-edit content validation would require a
dedicated patch applier/parser instead of pretending Codex patch text is Claude `old_string`/`new_string`.

Future work should revisit these decisions if Codex exposes read/search hook coverage or richer structured
`apply_patch` metadata.

## Claude Runner Parity Notes

`cat:codex-runner` exists as a procedure-based Codex equivalent for isolated ad-hoc runs. It invokes
`codex exec --json`, captures the final assistant message with `--output-last-message`, and keeps Codex JSONL
separate from Claude Code stream-json output because the formats are not compatible.

The empirical runner now supports explicit runtime selection. `EmpiricalTestRunner` can invoke either
`ClaudeRunner` or `CodexRunner` with `--runtime claude|codex`.

CAT now ships Codex formal-runner entrypoints alongside the ad-hoc runner. The packaged Codex runtime includes
`sprt-runner`, and prompt-builder self-run flows can continue formal SPRT validation on Codex-backed runtimes.
Remaining parity work is about making the shared Java instruction-test/SPRT internals fully runtime-neutral rather
than claiming Codex lacks formal-runner support.

Future work for full Codex parity:
- Introduce a runtime runner interface so `instruction-test-runner` and `sprt-runner` select `ClaudeRunner` or
  `CodexRunner` based on the active runtime.
- Define model alias resolution separately for Claude and Codex instead of routing all aliases through
  `claude-runner resolve-model`.
- Keep `cat:claude-runner` for Claude Code and `cat:codex-runner` for Codex in ad-hoc validation workflows.

## Remaining Runtime Parity Gaps

- **Read/search hooks:** Claude Code supports `Read|Glob|Grep` hooks. Codex currently does not expose equivalent
  broad built-in read/search hooks, so CAT cannot provide the same read-side worktree isolation or path-triggered
  rule/skill context injection for Codex.
- **Task/Skill hooks:** Claude Code supports `Task|Skill` hooks. CAT uses them for workflow safety gates, including
  blocking `cat:work-execute` with dirty worktrees, blocking `cat:work-merge` when the current directory will be
  deleted, and enforcing approval before merge. Codex does not expose compatible task/skill hook payloads.
- **SubagentStop:** not ported. CAT does not currently need an action at subagent completion time under Codex. Revisit
  if CAT adds Codex-side subagent result collection or cleanup that benefits from a lifecycle hook.
- **Plugin-bundled custom agents:** not supported by Codex plugin install as of `0.131.0`. Open requests:
  `openai/codex#18308` and `openai/codex#18988`. CAT must keep distributing Codex agent definitions through CAT's
  current runtime packaging/skill strategy rather than assuming plugin install registers agent TOML files.
- **Write/Edit content validation:** Codex `apply_patch` support is path-safe, and add-file hunks include content
  for validators. Update/delete/move hunks currently get path-level checks only. Full edited-content validation
  requires a dedicated patch applier/parser.
- **Formal validation runners:** `cat:codex-runner` covers isolated ad-hoc Codex runs, `empirical-test-runner
  --runtime codex` supports empirical Codex trials, and the packaged Codex runtime also ships `sprt-runner` for
  formal Codex-backed SPRT continuation. Remaining work here is broader runtime-neutrality in shared runner internals
  and documentation, not absence of Codex formal-runner support.
- **Statusline:** CAT statusline installation is Claude Code specific. Codex does not currently provide an
  equivalent custom statusline script mechanism for CAT to target.
- **Environment injection:** Claude Code provides `CLAUDE_ENV_FILE`, which lets CAT inject variables into future Bash
  shells. Codex has no equivalent global future-shell injection mechanism. CAT exposes runtime-neutral `CAT_*`
  variables in hook-controlled execution paths, but Codex skill shell commands cannot rely on global injection in the
  same way Claude Code can. Codex commands that need CAT paths must use the bootstrap block from
  `client/plugin/rules/codex/cat-environment.md`.
- **Plugin uninstall cleanup:** Codex does not expose a plugin uninstall hook for CAT-owned project agent files. CAT
  provides `cat:uninstall` to remove generated Codex agent copies before delegating to Codex's built-in plugin
  uninstaller.
- **Runtime-neutral skill cleanup:** common skills should use `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`,
  `CAT_PROJECT_DIR`, `CAT_RUNTIME`, and `CAT_SESSION_ID` for CAT infrastructure paths only when the active runtime has
  made those variables available. Claude Code gets them through CAT's SessionStart environment-file injection. Codex
  hook subprocesses get them from CAT's hook wrapper, while ordinary Codex Bash commands must initialize them with
  `client/plugin/rules/codex/cat-environment.md`. Skills whose behavior depends on Claude-specific or Codex-specific
  session formats, runners, hooks, agents, or environment bootstrapping should live under
  `client/plugin/skills/claude/` or `client/plugin/skills/codex/` and link to shared guidance in
  `client/plugin/concepts/`.

## Common Skill Audit Notes

The common skill directory may mention runtime names only when the skill is explicitly coordinating both runtimes or
describing the runtime directory layout. Examples:

- `cat:init` creates portable and runtime-specific project rule directories, so it intentionally names both runtime
  locations.
- `cat:empirical-test` compares behavior across runtimes and requires explicit `--runtime` selection, so its runtime
  table is part of the shared feature.
- `cat:instruction-builder` may mention `client/plugin/skills/{common,claude,codex}/` as repository layout, but
  runtime behavior should be routed through current-runtime runner skills or runtime-neutral `CAT_*` variables.

Skills moved out of `common` because the implementation is runtime-specific:

- `cat:get-history`: split into Claude and Codex skills with shared analyzer semantics in
  `client/plugin/concepts/session-history.md`.
- `cat:register-hook`: Claude keeps `.claude/settings.json` project hook registration; Codex documents that CAT
  Codex hooks are plugin-config based.
- `cat:sprt-runner`: both runtimes now ship formal SPRT runner surfaces, but documentation and shared abstractions
  still need cleanup where they imply Claude-only ownership.

Other runtime-specific surfaces found while adding `cat:codex-runner`:
- `session-analyzer` requires an explicit runtime (`--runtime claude` or `--runtime codex`) and supports Codex
  session logs when invoked with `--runtime codex`, including agent rollout discovery from parent-thread
  metadata.
- Common skills that only need CAT infrastructure should avoid runtime-specific variable names. Claude Code and Codex
  hook paths both expose `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_PROJECT_DIR`, `CAT_RUNTIME`, and `CAT_SESSION_ID`
  where CAT controls the execution environment. Codex ordinary shell commands must initialize those variables with the
  runtime-specific rule before using them.
