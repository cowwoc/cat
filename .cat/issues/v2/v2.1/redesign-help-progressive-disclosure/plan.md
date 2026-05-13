# Plan

## Goal

Redesign `$cat:help` for progressive disclosure so users see the primary user-facing skills first, with advanced
reference material available below without drilling into each command.

## Type

feature

## Force Stakeholders

- ux

## Pre-conditions

(none)

## Post-conditions

- [x] `$cat:help` opens with a concise user-facing skill list for `$cat:init`, `$cat:status`, `$cat:config`, and
  `$cat:cleanup`.
- [x] `$cat:help` avoids per-skill drill-down content in the top-level section.
- [x] The `### Init Details` and `### Issue Naming` sections are removed.
- [x] The `### Project Structure` and `### Branch Naming` sections remain available as reference material.
- [x] Markdown tables in `$cat:help` render correctly in chat clients.
- [x] Markdown rendering behavior for `$cat:help` is verified or documented in the implementation notes.

## Implementation Notes

`$cat:help` can render as Markdown when the skill emits Markdown directly into chat. The help skill now instructs the
agent to output the reference as Markdown content, and its tables keep standard separator rows with blank lines around
them so Markdown renderers can parse them.
