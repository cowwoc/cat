<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Preprocessor Directives

CAT source files use a small set of build-time directives to keep plugin instructions maintainable while generating
runtime-specific artifacts for Claude and Codex.

Directives are source-only. Generated plugin artifacts must not contain unresolved `cat:*` directives.

## `cat:include`

Includes another Markdown or TOML source file at the directive location.

```markdown
<!-- cat:include relative/path.md -->
```

Paths are resolved relative to the file that contains the directive. Absolute paths are rejected. Includes are intended
for always-loaded instruction fragments, not optional background references or tests.

Plugin artifact builds allow includes from runtime-visible source trees, including common instructions, runtime-specific
instructions, include fragments, rules, hooks, and concepts. The builder strips source license headers from included
agent-facing text and rejects recursive or unresolved includes.

## `cat:render-output`

Generates the correct runtime-specific instruction for invoking a deterministic CAT output command.

```markdown
<!-- cat:render-output <command> [arguments...] -->
```

For both runtimes, the directive emits the standard "render the deterministic Java output exactly" instruction. For
Claude artifacts, it also expands to a silent preprocessor command. For Codex artifacts, it also expands to a Bash block
that tells Codex to run the deterministic implementation and render the command output exactly.

Use placeholders such as `<issue-path>` or `<target-branch>` for skill arguments:

```markdown
<!-- cat:render-output get-output work-complete <completed-issue> <target-branch> -->
```

Write placeholder angle brackets literally. Do not HTML-escape them as `&lt;issue-path&gt;`; escaped placeholders are
treated as invalid directive tokens and fail the artifact build.

Claude artifacts convert placeholders to positional shell arguments (`"$1"`, `"$2"`, and so on). Codex artifacts keep
the placeholder names in the Bash block so the calling agent can replace them with the skill arguments.

## Runtime Placeholders

The artifact builder also resolves CAT runtime placeholders in source instruction files:

| Placeholder | Claude artifact | Codex artifact |
|-------------|-----------------|----------------|
| `${CAT_COMMAND_PREFIX}` | `/` | `$` |
| `${CAT_CONFIG_SETTINGS_RENDER_STEP}` | Silent config output preprocessor command | `cat:get-output` skill invocation |

These placeholders are not HTML directives, but they are part of the same source-to-runtime preprocessing pass.
