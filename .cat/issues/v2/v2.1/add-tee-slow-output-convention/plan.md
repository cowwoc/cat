# Plan

## Goal

Add a plugin convention requiring that slow processes (builds, test suites, linters) have their output tee'd to a
temporary file so the output can be re-filtered without re-running the command.

## Pre-conditions

(none)

## Post-conditions

- [ ] A new file `plugin/rules/tee-slow-output.md` exists with the convention
- [ ] The rule includes correct frontmatter (`mainAgent: true`, `subAgents: [all]`)
- [ ] The rule explains when to tee (slow commands), when not to (fast commands, background tasks), and cleanup expectations
- [ ] The rule includes a concrete pattern example using `mktemp` and `tee`
