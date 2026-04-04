# Plan

## Goal

Add an explicit prohibition to `plugin/concepts/rules-audience.md` against using `subAgents: [all]` as a
wildcard. The string `"all"` is treated as a literal agent type name by `RulesDiscovery.filterForSubagent()`,
not a wildcard. Omitting the `subAgents` field is the correct way to target all subagents.

This prevents the priming documented in Learning M613, where the presence of `subAgents: [all]` in a cached
file made the invalid syntax appear plausible during reconciliation.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/rules-audience.md` contains an explicit "Invalid" example showing `subAgents: [all]`
  with a note that `"all"` is a literal string, not a wildcard
- [ ] The correct alternative (omit `subAgents` field) is clearly stated adjacent to the prohibition
- [ ] All existing tests pass (`mvn -f client/pom.xml test` exits 0)
