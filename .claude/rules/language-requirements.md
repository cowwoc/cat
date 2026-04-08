---
paths: ["plugin/**", "client/**"]
---
## Language Requirements

| Component Type | Language | Rationale |
|----------------|----------|-----------|
| Complex business logic | **Java** | Type safety, testability, jlink bundling |
| CLI tools/hooks | Bash | Claude Code plugin integration, Unix tooling |
| Configuration | JSON | Standard, machine-readable |
| Documentation | Markdown | Human-readable, version-controlled |

### Plugin Code (Java)

Java is used for:
- Hook handlers (PreToolUse, PostToolUse, etc.)
- Skill handlers
- Display formatting and output
- Configuration management
- Test suites

**Java Version:** 25+

**Testing Framework:** TestNG with JsonMapper for serialization

### CLI/Hooks (Bash)

Bash scripts are appropriate for:
- Claude Code hook entry points
- Git operations
- Simple file manipulation
- Environment setup
- Complex logic when Java runtime is not yet available (e.g., bootstrap scripts)

Bash scripts should NOT contain:
- Complex business logic (use Java instead)
- State management beyond simple files

### Tool Availability

The plugin's runtime environment provides:

- **Bash** and standard POSIX utilities (`grep`, `sed`, `awk`, `sort`, `find`, `cat`, `head`, `tail`, `cut`, `tr`,
  `wc`, `xargs`, `mktemp`, `date`, etc.)
- **Git**
- **The jlink bundle** (Java tools: `session-analyzer`, `progress-banner`, `verify-audit`, etc.)

Do NOT assume or use tools outside this set. In particular, `jq`, `python`, `python3`, `node`, and `ruby` are **not
available**.

- **JSON parsing:** Use Java (via jlink tools) or Bash pattern matching, not `jq`
- **Complex data processing:** Use jlink-bundled Java tools, not Python scripts
- **Scripting:** Use Bash or Java, not Python

Existing Python scripts are tracked for migration under `migrate-python-to-java`.

## Code Organization

```
project/
├── plugin/                 # CAT plugin source
│   ├── hooks/              # Hook handlers (Java/Bash)
│   ├── skills/             # Skill definitions (Markdown)
│   ├── commands/           # Command definitions (Markdown)
│   └── scripts/            # Utility scripts
├── tests/                  # Test suites
└── docs/                   # Documentation
```

Files under `plugin/` are deployed to end-user machines. They must not reference source-only paths (`.claude/rules/`,
`.cat/rules/`, etc.) that are not shipped. See `.claude/rules/plugin-file-references.md` for the full convention.

## Enforcement

```cat-rules
- pattern: "\\bjq\\b"
  files: "*.sh"
  severity: high
  message: "jq is not available in the plugin runtime. Use Java (via jlink tools) or Bash pattern\
 matching. See .claude/rules/language-requirements.md § Tool Availability."
```
