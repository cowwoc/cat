# Plan: java-skill-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 3 of 5
- **Estimated Tokens:** 45K

## Objective
Migrate all skill handlers (UI/output generation) to Java.

## Scope
- skill_handlers/base.py → Java base class
- All 12 skill handler implementations
- Box drawing, progress formatting, status display

## Dependencies
- java-core-hooks (core infrastructure must exist)

## Files to Migrate
| Python | Java |
|--------|------|
| skill_handlers/base.py | src/cat/hooks/skills/BaseHandler.java |
| skill_handlers/add_handler.py | src/cat/hooks/skills/AddHandler.java |
| skill_handlers/cleanup_handler.py | src/cat/hooks/skills/CleanupHandler.java |
| skill_handlers/config_handler.py | src/cat/hooks/skills/ConfigHandler.java |
| skill_handlers/help_handler.py | src/cat/hooks/skills/HelpHandler.java |
| skill_handlers/init_handler.py | src/cat/hooks/skills/InitHandler.java |
| skill_handlers/render_diff_handler.py | src/cat/hooks/skills/RenderDiffHandler.java |
| skill_handlers/research_handler.py | src/cat/hooks/skills/ResearchHandler.java |
| skill_handlers/stakeholder_handler.py | src/cat/hooks/skills/StakeholderHandler.java |
| skill_handlers/status_handler.py | src/cat/hooks/skills/StatusHandler.java |
| skill_handlers/token_report_handler.py | src/cat/hooks/skills/TokenReportHandler.java |
| skill_handlers/work_handler.py | src/cat/hooks/skills/WorkHandler.java |

## Execution Steps
1. Create BaseHandler Java class with common utilities
2. Migrate each handler preserving exact output format
3. Ensure box drawing characters render correctly
4. Verify JSON output matches Python version

## Acceptance Criteria
- [ ] All 12 handlers produce identical output
- [ ] Box characters (╭╮╰╯│─) render correctly
- [ ] Progress bars and status displays match
- [ ] Existing tests pass with Java handlers
