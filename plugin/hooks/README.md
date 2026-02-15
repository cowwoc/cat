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
runtime/cat-jdk-25/
├── bin/
│   ├── java                     # JVM binary
│   ├── get-bash-output          # Generated launcher scripts
│   ├── get-read-output          #   (one per handler class)
│   ├── get-post-output
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

Output: `hooks/target/jlink/`

The `session-start.sh` hook copies the built image to `${CLAUDE_PLUGIN_ROOT}/runtime/cat-jdk-25/` at session start.

**Using the `/cat-update-hooks` skill:** For development workflows, use the `/cat-update-hooks` skill to build and
install the hooks into your current Claude Code project. This skill handles building, installation, and plugin cache
updates automatically.

### Handler Registry

Each handler is registered in `build-jlink.sh`'s `HANDLERS` array as `launcher-name:ClassName`. The build generates a
`bin/<launcher-name>` shell script for each entry.

| Launcher | Class | Hook Event |
|----------|-------|------------|
| `get-bash-output` | `GetBashOutput` | PreToolUse (Bash) |
| `get-bash-post-output` | `GetBashPostOutput` | PostToolUse (Bash) |
| `get-read-output` | `GetReadOutput` | PreToolUse (Read/Glob/Grep) |
| `get-read-post-output` | `GetReadPostOutput` | PostToolUse (Read/Glob/Grep) |
| `get-post-output` | `GetPostOutput` | PostToolUse (all) |
| `get-skill-output` | `GetSkillOutput` | UserPromptSubmit |
| `get-ask-output` | `GetAskOutput` | PreToolUse (AskUserQuestion) |
| `get-edit-output` | `GetEditOutput` | PreToolUse (Edit) |
| `get-write-edit-output` | `GetWriteEditOutput` | PreToolUse (Write/Edit) |
| `get-task-output` | `GetTaskOutput` | PreToolUse (Task) |
| `get-session-end-output` | `GetSessionEndOutput` | SessionEnd |
| `token-counter` | `TokenCounter` | PostToolUse (all) |
| `enforce-status` | `EnforceStatusOutput` | Stop |
| `get-status-output` | `skills.RunGetStatusOutput` | Skill handler (status) |
| `get-checkpoint-box` | `skills.GetCheckpointOutput` | Skill handler (checkpoint) |
| `get-issue-complete-box` | `skills.GetIssueCompleteOutput` | Skill handler (issue-complete) |
| `get-next-task-box` | `skills.GetNextTaskOutput` | Skill handler (next-task) |
| `get-render-diff-output` | `skills.GetRenderDiffOutput` | Skill handler (render-diff) |

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
  -m io.github.cowwoc.cat.hooks/io.github.cowwoc.cat.hooks.ClassName "$@"
```

JVM flags: 16-64MB heap, serial GC (minimal overhead for short-lived processes), tier-1 compilation only (fastest
startup), and Leyden AOT cache for pre-linked classes.

## Skill Directory Structure

Skills are loaded by `SkillLoader` from the plugin's `skills/` directory. Each skill is a subdirectory containing:

```
plugin-root/
  skills/
    reference.md              — Reload text returned on 2nd+ invocations of any skill
    {skill-name}/
      first-use.md            — Main skill content (loaded on first invocation)
      includes.txt            — Optional; one relative path per line listing files to include
      bindings.json           — Optional; maps variable names to SkillOutput class names
```

### Loading Behavior

**First invocation** of a skill within a session:
1. Load files listed in `includes.txt`, each wrapped in `<include path="...">...</include>` XML tags
2. Load `first-use.md` (with license header stripped if present)
3. Substitute variables (built-in and bindings)

**Subsequent invocations** of the same skill within the same session:
1. Load `reference.md` instead of content/includes
2. Substitute variables (built-in and bindings)

Session tracking uses a temp file (`/tmp/cat-skills-loaded-{session-id}`) to record which skills have been loaded.

### Variable Bindings

Skills can define custom variables in `bindings.json` that map to `SkillOutput` classes. When a variable is referenced in content (e.g., `${CAT_SKILL_OUTPUT}`), the class is instantiated and invoked to generate the substitution value.

**bindings.json format:**
```json
{
  "CAT_SKILL_OUTPUT": "io.github.cowwoc.cat.hooks.skills.GetStatusOutput"
}
```

**Built-in variables** (always available):
- `${CLAUDE_PLUGIN_ROOT}` - plugin root directory path
- `${CLAUDE_SESSION_ID}` - current session identifier
- `${CLAUDE_PROJECT_DIR}` - project directory path

**Variable resolution behavior:**

When the SkillLoader encounters a `${...}` variable reference in skill content, it resolves it using this precedence:
1. **Built-in variables** - resolved to their runtime values
2. **Binding variables** - resolved by invoking the corresponding SkillOutput class
3. **Unknown variables** - passed through as literal text (e.g., `${UNKNOWN}` remains `${UNKNOWN}`)

This pass-through behavior matches Claude Code's native variable handling, allowing skills to include variables that
will be processed downstream by Claude Code itself.

**Binding requirements:**
- SkillOutput class must have a constructor accepting `JvmScope` parameter
- Binding variable names must not collide with built-in variables

**Example first-use.md using bindings:**
```markdown
SKILL OUTPUT STATUS DISPLAY:
${CAT_SKILL_OUTPUT}

# Status

The user wants you to respond with the content from "SKILL OUTPUT STATUS DISPLAY" above, verbatim.
```

### Handler Classes

Handler classes must implement `io.github.cowwoc.cat.hooks.util.SkillOutput` interface:
- Constructor accepting `JvmScope` parameter
- `getOutput()` method returning dynamic content
- Handler is instantiated and invoked in-process (no subprocess spawn)
- Handlers are invoked lazily (only when their variable is referenced)

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

1. Checks if the jlink runtime exists at `${CLAUDE_PLUGIN_ROOT}/runtime/cat-jdk-25/`
2. If missing, copies from `hooks/target/jlink/` (local build)
3. Invokes session-start handlers for initialization tasks

## Troubleshooting

### "Java not found"

1. Ensure JDK 25 is installed: `java -version`
2. Build the jlink image: `cd hooks && ./build-jlink.sh`

### Build fails with "module not found"

Ensure you're using JDK 25 (not just JRE). jlink requires the full JDK.

### Hook produces no output

1. Check `hooks.json` maps the event to the correct launcher
2. Verify the launcher exists: `ls runtime/cat-jdk-25/bin/<launcher-name>`
3. Test directly: `echo '{}' | runtime/cat-jdk-25/bin/<launcher-name>`
