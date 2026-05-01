# CAT Hooks Infrastructure

This directory contains the hook system that connects Claude Code events to CAT's Java-based handlers.

## Architecture

```
Claude Code Hook Event
        │
        ▼
  hooks.json            ─── Maps events to launcher scripts
        │
        ▼
  bin/<launcher>        ─── Generated shell scripts (one per handler)
        │
        ▼
  jlink runtime         ─── Self-contained JDK 25 image (~30-40MB)
        │
        ▼
  Java handler class    ─── Business logic (reads stdin JSON, writes stdout)
```

**Hook events** (PreToolUse, PostToolUse, etc.) are registered in `hooks.json`. Each hook command points to a launcher
script in the jlink image's `bin/` directory. The launcher invokes a Java handler class with optimized JVM settings
(serial GC, tiered compilation, Leyden AOT cache).

## Files

| File | Purpose |
|------|---------|
| `hooks.json` | Maps Claude Code hook events to launcher scripts |
| `session-start.sh` | SessionStart hook — bootstraps the jlink runtime |
| `README.md` | This file |

## jlink Runtime

The jlink image is a self-contained JDK 25 runtime with only the modules needed for hook execution. It includes the
hooks application JAR, Jackson 3 for JSON processing, and SLF4J/Logback for logging.

**Benefits:**
- ~30-40MB vs ~300MB for full JDK
- Sub-100ms startup with Leyden AOT cache
- Self-contained (no external Java dependency)

### Runtime Structure

```
runtime/client/
├── bin/
│   ├── java                     # JVM binary
│   ├── pre-bash                 # Generated launcher scripts
│   ├── pre-read                 #   (one per handler class))
│   ├── post-tool-use
│   └── ...
└── lib/
    └── server/
        └── aot-cache.aot        # Leyden AOT pre-linked cache
```

### Building

The build script compiles the hooks Maven project, stages dependencies, patches automatic modules for JPMS
compatibility, creates the jlink image, generates Leyden AOT caches, and writes per-handler launcher scripts.

```bash
# From hooks/ directory
./build-jlink.sh
```

Output: `client/target/jlink/`

The `session-start.sh` hook downloads the jlink image from GitHub releases to `${CLAUDE_PLUGIN_ROOT}/runtime/client/` if not already present.

**Using the `/cat-update-client` skill:** For development workflows, use the `/cat-update-client` skill to build and
install the jlink runtime into the plugin cache. This skill handles building, installation, and cache updates
automatically. Run it after modifying any Java source files under `client/src/`.

### Handler Registry

Each handler is registered in `build-jlink.sh`'s `HANDLERS` array as `launcher-name:ClassName`. The build generates a
`bin/<launcher-name>` shell script for each entry.

| Launcher | Class | Hook Event |
|----------|-------|------------|
| `pre-bash` | `PreToolUseHook` | PreToolUse (Bash) |
| `post-bash` | `PostBashHook` | PostToolUse (Bash) |
| `pre-read` | `PreReadHook` | PreToolUse (Read/Glob/Grep) |
| `post-read` | `PostReadHook` | PostToolUse (Read/Glob/Grep) |
| `post-tool-use` | `PostToolUseHook` | PostToolUse (all) |
| `user-prompt-submit` | `UserPromptSubmitHook` | UserPromptSubmit |
| `pre-ask` | `PreAskHook` | PreToolUse (AskUserQuestion) |
| `pre-write` | `PreWriteHook` | PreToolUse (Write/Edit) |
| `pre-issue` | `PreIssueHook` | PreToolUse (Task) |
| `session-end` | `SessionEndHook` | SessionEnd |
| `token-counter` | `TokenCounter` | PostToolUse (all) |
| `enforce-status` | `EnforceStatusOutput` | Stop |
| `get-status-output` | `skills.GetStatusOutput` | Skill handler (status) |
| `get-checkpoint-box` | `skills.GetCheckpointOutput` | Skill handler (checkpoint) |
| `get-issue-complete-box` | `skills.GetIssueCompleteOutput` | Skill handler (issue-complete) |
| `get-next-issue-box` | `skills.GetNextIssueOutput` | Skill handler (next-issue) |
| `get-diff-output` | `skills.GetDiffOutput` | Skill handler (get-diff) |
| `get-token-report-output` | `skills.GetTokenReportOutput` | Skill handler (token-report) |
| `get-cleanup-output` | `skills.GetCleanupOutput` | Skill handler (cleanup survey) |
| `create-issue` | `util.IssueCreator` | Issue creation utility |

### Launcher Script Format

Each generated launcher is a POSIX shell script:

```sh
#!/bin/sh
DIR=`dirname $0`
exec "$DIR/java" \
  -Xms16m -Xmx64m \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \
  -m io.github.cowwoc.cat.client.claude/io.github.cowwoc.cat.claude.hook.ClassName "$@"
```

JVM flags: 16-64MB heap, serial GC (minimal overhead for short-lived processes), tier-1 compilation only (fastest
startup), and Leyden AOT cache for pre-linked classes.

## Skill Directory Structure

Skills are organized under `plugin/skills/` as one directory per skill:

```
plugin/skills/
 {skill-name}/
  SKILL.md     — Frontmatter + preprocessor directive entrypoint
  first-use.md — Full skill instructions returned on first load
```

### Loading Behavior

Skill loading is policy-driven and documented in `plugin/concepts/skill-loading.md`.
At a high level:

1. SKILL.md runs a preprocessor directive that calls the shared loader contract.
2. On first use for a given agent, full instructions are returned from `first-use.md`.
3. On subsequent uses, a short reuse/reference message is returned.

The tracking scope is per-agent within a session (main agent and each subagent track independently).

### Built-in Variables

Inside `!` directive strings, use only supported variable forms:
- `$0`, `$1`, ... for positional arguments
- `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_SESSION_ID}`, `${CLAUDE_PROJECT_DIR}` for built-in variables
- `$ARGUMENTS` for the raw argument string

For exact substitution and quoting rules, follow `.claude/rules/skills.md` and
`plugin/concepts/skill-loading.md`.

### Handler Classes

Handler classes implement `io.github.cowwoc.cat.claude.hook.util.SkillOutput` interface:
- Constructor accepting `JvmScope` parameter
- `getOutput(String[])` method returning dynamic content
- Instantiated and invoked in-process via preprocessor directives (no subprocess spawn)

### includes.txt Format

One relative path per line, resolved from the plugin root:

```
concepts/context1.md
concepts/context2.md
```

Each included file is wrapped in XML tags:

```xml
<include path="concepts/context1.md">
... file content with variables substituted ...
</include>
```

Missing include files are silently skipped.

## Session Bootstrap

The `session-start.sh` hook runs at each Claude Code session start:

1. Checks if the jlink runtime exists at `${CLAUDE_PLUGIN_ROOT}/runtime/client/`
2. If missing, downloads from GitHub releases
3. Invokes session-start handlers for initialization tasks

## Troubleshooting

### "Java not found"

1. Ensure JDK 25 is installed: `java -version`
2. Build the jlink image: `cd hooks && ./build-jlink.sh`

### Build fails with "module not found"

Ensure you're using JDK 25 (not just JRE). jlink requires the full JDK.

### Hook produces no output

1. Check `hooks.json` maps the event to the correct launcher
2. Verify the launcher exists: `ls runtime/client/bin/<launcher-name>`
3. Test directly: `echo '{}' | runtime/client/bin/<launcher-name>`
