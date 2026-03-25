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

## Research Findings

The convention file `plugin/rules/tee-slow-output.md` already exists on the target branch (`v2.1`) as of commit
`0d30913a8`. It contains:

- Correct frontmatter: `mainAgent: true`, `subAgents: [all]`
- MANDATORY directive for slow commands
- Rationale explaining why tee is needed
- Concrete pattern using `mktemp` and `tee` with `mvn` example
- "When NOT to tee" section covering fast commands, small output, and background tasks
- Cleanup section with `rm -f` example

The implementation subagent should read the file, check each post-condition against the actual content, and close the
issue if all are met.

## Execution Steps

Commit type: `feature:`

1. Read `plugin/rules/tee-slow-output.md` and confirm it exists
2. Check frontmatter contains `mainAgent: true` and `subAgents: [all]`
3. Check the file explains when to tee (slow commands) and when NOT to tee (fast commands, background tasks)
4. Check the file includes cleanup expectations (`rm -f`)
5. Check the file includes a concrete pattern example using `mktemp` and `tee`
6. If all post-conditions are met, update `index.json` to set status to `closed` and progress to `100`
7. Commit with message `feature: add tee-slow-output convention to plugin rules`
