# Plan: bold-rendering-fix

## Objective
fix bold rendering in all display templates (M125)

## Details
Markdown **bold** renders correctly when output directly but shows as
literal asterisks inside triple-backtick code blocks.

Changes:
- Removed code block wrappers from all display templates
- Changed UPPERCASE emphasis to **bold** markdown
- Removed header separator lines (├───┤) that misalign with emojis
- Added "do NOT wrap in ```" guidance to all templates

Files updated:
- commands/status.md
- commands/execute-task.md (task display, checkpoint, size analysis, report)
- skills/collect-results/SKILL.md
- skills/choose-approach/SKILL.md (fork display and examples)
- .claude/cat/references/display-standards.md (all examples)
- .claude/cat/references/task-resolution.md (M126 validation rules)

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
