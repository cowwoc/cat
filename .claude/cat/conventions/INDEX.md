# Conventions Index

Conventions are loaded through two mechanisms:

1. **Auto-loaded** - Files in `.claude/rules/` load at session start
2. **On-demand** - Files here are read before editing matching file types (per CLAUDE.md)

| Convention | Loading Mechanism | Trigger |
|------------|-------------------|---------|
| [common.md](../../rules/common.md) | Auto-loaded | Session start |
| [java.md](java.md) | On-demand | Before editing `*.java` files |

## Quick Reference

**Plugin logic:** Python
**CLI/Hooks:** Bash
**Config files:** JSON
**Documentation:** Markdown
