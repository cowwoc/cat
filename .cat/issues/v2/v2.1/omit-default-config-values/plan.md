# Plan

## Goal

When `cat:config` updates `.cat/config.json`, it should remove any entries whose values match CAT's built-in defaults
and omit those entries from future writes. The configuration file should store only meaningful overrides while effective
configuration behavior remains unchanged.

## Pre-conditions

(none)

## Post-conditions

- [ ] `cat:config` removes a key from `.cat/config.json` when the selected value equals that key's built-in default
- [ ] `cat:config` omits default-valued keys when creating or rewriting `.cat/config.json`
- [ ] Non-default configuration overrides are preserved and written normally
- [ ] Effective configuration output remains unchanged after default-valued entries are pruned
- [ ] Regression tests cover updating a key to its default, creating config from default selections, and preserving
  non-default values
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
