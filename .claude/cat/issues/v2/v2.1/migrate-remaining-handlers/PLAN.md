# Plan: migrate-remaining-handlers

## Goal
Migrate remaining handlers to silent preprocessing or mark as not applicable.

## Satisfies
- Parent: migrate-to-silent-preprocessing

## Handlers to Evaluate
- add_handler.py - Task/version creation displays
- help_handler.py - Help output
- cleanup_handler.py - Cleanup progress display
- config_handler.py - Config wizard displays
- init_handler.py - Init wizard displays
- token_report_handler.py - Token report displays
- render_diff_handler.py - Diff rendering
- stakeholder_handler.py - Stakeholder review displays

## Evaluation Results

All handlers have been evaluated and are already using silent preprocessing:

| Handler | Pattern | Status |
|---------|---------|--------|
| add_handler.py | `OUTPUT TEMPLATE ADD DISPLAY` | ✅ Migrated |
| help_handler.py | `OUTPUT TEMPLATE HELP DISPLAY` | ✅ Migrated |
| cleanup_handler.py | `OUTPUT TEMPLATE SURVEY/PLAN/VERIFY DISPLAY` | ✅ Migrated |
| config_handler.py | `OUTPUT TEMPLATE CONFIG BOXES` | ✅ Migrated |
| init_handler.py | `OUTPUT TEMPLATE INIT BOXES` | ✅ Migrated |
| token_report_handler.py | `OUTPUT TEMPLATE TOKEN REPORT` | ✅ Migrated |
| render_diff_handler.py | `OUTPUT TEMPLATE RENDER-DIFF OUTPUT` | ✅ Migrated |
| stakeholder_handler.py | `OUTPUT TEMPLATE STAKEHOLDER BOXES` | ✅ Migrated |
| research_handler.py | `RESEARCH DISPLAY TEMPLATES LOADED` | ✅ Migrated |
| status_handler.py | Utility module (no handler registration) | ✅ N/A |
| work_handler.py | `OUTPUT TEMPLATE WORK PROGRESS FORMAT` | ✅ Migrated |

**Note:** status_handler.py is now a utility module providing shared display functions
(display_width, build_header_box, etc.) used by other handlers. Status display generation
was migrated to get-status-display.sh/py in task migrate-status-displays.

## Acceptance Criteria
- [x] Each handler evaluated for migration
- [x] Handlers with complex output migrated to preprocessing
- [x] Handlers with simple context injection can remain
- [x] Documentation updated for each migration
