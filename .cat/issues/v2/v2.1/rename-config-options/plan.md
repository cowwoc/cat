# Plan: 2.1-rename-config-options

## Goal

Rename three config options to use personality/style-oriented names that better describe user working style:
- `verify` → `caution`
- `effort` → `curiosity`
- `patience` → `perfection` (scale inverted: high=act immediately, low=defer)

## Background

The config options are used throughout the plugin to control CAT behavior. The new names are being introduced
as part of a broader UX redesign that models user personality/style. A companion issue
(`2.1-add-personality-questionnaire`) adds a questionnaire that derives these values during `/cat:init`.

## Scope

All files under `plugin/` that read or document `effort`, `verify`, or `patience` config keys, plus the
config template and migration infrastructure.

## Post-conditions

- `plugin/templates/config.json` uses new key names (`caution`, `curiosity`, `perfection`)
- All skill files that read `effort` now read `curiosity`
- All skill files that read `verify` now read `caution`
- All skill files that read `patience` now read `perfection`
- A migration script in `plugin/migrations/` converts existing `config.json` files from old to new key names
- Migration is idempotent (safe to run twice)
- The `perfection` scale is documented: high=act immediately, low=defer (inverted from former `patience`)
- All tests pass with no regressions
- `plugin/skills/config/first-use.md` updated to use new names in all menus and descriptions
- `plugin/skills/init/first-use.md` updated to use new names
- Config documentation reflects new names and their meanings
