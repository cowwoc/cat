# Plan

## Goal

Fix `$cat:help` so its output renders as Markdown in Codex/chat clients and remove the introductory/tagline
sections the user identified as noise.

This is a recurrence after the closed `redesign-help-progressive-disclosure` issue, whose post-conditions included
Markdown rendering verification for `$cat:help`.

## Pre-conditions

(none)

## Post-conditions

- [x] `$cat:help` output renders as Markdown in the active chat client instead of plain unrendered text.
- [x] The line `CAT — hierarchical project planning with multi-agent issue execution.` is removed from the help
  output.
- [x] The decorative horizontal separator immediately after that line is removed from the help output.
- [x] The natural-language examples sentence beginning with `language, such as "add an issue to fix login"` is removed
  from the help output.
- [x] The decorative horizontal separator immediately after that examples sentence is removed from the help output.
- [x] Regression coverage or an equivalent verification documents that `$cat:help` renders Markdown correctly.
