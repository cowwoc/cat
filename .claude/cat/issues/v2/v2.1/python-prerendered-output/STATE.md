# State

- **Status:** completed
- **Progress:** 100%
- **Dependencies:** []
- **Last Updated:** 2026-01-29

## Summary

Migrated skills to direct preprocessing architecture per skill-builder guidelines:

**Terminology:** OUTPUT TEMPLATE → SCRIPT OUTPUT

**New preprocessing scripts (12 total):**
- get-help-display.sh/py, get-config-boxes.sh, get-init-boxes.sh
- get-work-boxes.sh/py, get-stakeholder-boxes.sh, get-token-report.sh
- get-cleanup-boxes.sh, get-render-diff.sh, render-add-complete.sh

**Updated skills (9 total):**
- help, config, init, work, stakeholder-review, token-report, cleanup, render-diff, add

**Architecture patterns:**
- Direct preprocessing: Script runs during skill load via `!`script.sh`` syntax
- Delegated preprocessing: LLM invokes renderer with gathered data (add skill)

Also moved SKILL.md terminology changes from 2.1-java-skill-handlers branch and reverted those changes on that branch.
