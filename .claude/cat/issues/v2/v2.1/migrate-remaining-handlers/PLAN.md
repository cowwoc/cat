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

## Acceptance Criteria
- [ ] Each handler evaluated for migration
- [ ] Handlers with complex output migrated to preprocessing
- [ ] Handlers with simple context injection can remain
- [ ] Documentation updated for each migration
