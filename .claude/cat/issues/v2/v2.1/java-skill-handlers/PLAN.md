# Plan: java-skill-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 3 of 5
- **Estimated Tokens:** 35K (reduced due to silent preprocessing migration)

## Objective
Migrate remaining skill handlers (UI/output generation) to Java.

## Architecture Note (Silent Preprocessing Migration)
Some handlers have been migrated to "silent preprocessing" pattern:
- **StatusHandler**: Removed from status_handler.py → now in scripts/get-status-display.py
- **WorkHandler progress banners**: Removed → now in scripts/get-progress-banner.sh

These scripts are invoked via `!`command`` syntax in SKILL.md, not through handler classes.
The handler migration only covers handlers that still exist in skill_handlers/.

## Scope
- skill_handlers/base.py → Java base class (display utilities only)
- Remaining 10 skill handler implementations
- Box drawing, progress formatting utilities

## Dependencies
- java-core-hooks (core infrastructure must exist)

## Files to Migrate
| Python | Java | Notes |
|--------|------|-------|
| skill_handlers/base.py | src/cat/hooks/skills/DisplayUtils.java | Shared box/display utilities |
| skill_handlers/add_handler.py | src/cat/hooks/skills/AddHandler.java | |
| skill_handlers/cleanup_handler.py | src/cat/hooks/skills/CleanupHandler.java | |
| skill_handlers/config_handler.py | src/cat/hooks/skills/ConfigHandler.java | |
| skill_handlers/help_handler.py | src/cat/hooks/skills/HelpHandler.java | |
| skill_handlers/init_handler.py | src/cat/hooks/skills/InitHandler.java | |
| skill_handlers/render_diff_handler.py | src/cat/hooks/skills/RenderDiffHandler.java | |
| skill_handlers/research_handler.py | src/cat/hooks/skills/ResearchHandler.java | |
| skill_handlers/stakeholder_handler.py | src/cat/hooks/skills/StakeholderHandler.java | |
| skill_handlers/status_handler.py | N/A | Display utils only, StatusHandler removed |
| skill_handlers/token_report_handler.py | src/cat/hooks/skills/TokenReportHandler.java | |
| skill_handlers/work_handler.py | src/cat/hooks/skills/WorkHandler.java | Progress banners removed |

## Silent Preprocessing Scripts (NOT migrated here)
These are standalone scripts, not part of handler infrastructure:
- scripts/get-status-display.py → Consider Java migration separately
- scripts/get-progress-banner.sh → Shell script, no Java needed

## Execution Steps
1. Create DisplayUtils Java class with box drawing utilities
2. Migrate each remaining handler preserving exact output format
3. Ensure box drawing characters render correctly
4. Verify JSON output matches Python version

## Acceptance Criteria
- [ ] All 10 remaining handlers produce identical output
- [ ] Box characters (╭╮╰╯│─) render correctly
- [ ] Status display utilities work correctly
- [ ] Existing tests pass with Java handlers
