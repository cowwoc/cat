# Plan

## Goal

Fix stale /cat:learn references in hooks and plugin files that should be /cat:learn-agent. Several files instruct the agent to invoke `/cat:learn` but the correct skill name is `/cat:learn-agent`.

## Pre-conditions

(none)

## Post-conditions

- [ ] `AutoLearnMistakes.java` instructs agent to invoke `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/rules/approval-gate-protocol.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/concepts/token-warning.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/concepts/agent-architecture.md` references `/cat:learn-agent` instead of `/cat:learn` (both occurrences)
- [ ] `plugin/skills/learn/first-use.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `mvn -f client/pom.xml verify -e` exits 0

## Jobs

### Job 1

Fix the Java hook that instructs the agent to invoke `/cat:learn` and run the build to confirm no regressions.

- In `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`, change line 138:
  - Old: `"**MANDATORY**: Run \`/cat:learn\` to record this mistake and prevent recurrence, then use " +`
  - New: `"**MANDATORY**: Run \`/cat:learn-agent\` to record this mistake and prevent recurrence, then use " +`
- Run `mvn -f client/pom.xml verify -e` and confirm exit code 0
- Commit with message: `bugfix: fix stale /cat:learn reference in AutoLearnMistakes.java`

### Job 2

Fix stale `/cat:learn` references in plugin concepts files. These files use `config:` commit type because they are in `plugin/concepts/`.

- In `plugin/concepts/token-warning.md`, change line 123:
  - Old: `Invoke \`/cat:learn\` with:`
  - New: `Invoke \`/cat:learn-agent\` with:`
- In `plugin/concepts/agent-architecture.md`, change line 308:
  - Old: `4. Trigger \`/cat:learn\` for each violation`
  - New: `4. Trigger \`/cat:learn-agent\` for each violation`
- In `plugin/concepts/agent-architecture.md`, change line 371:
  - Old: `2. **Record:** Invoke \`/cat:learn\` with:`
  - New: `2. **Record:** Invoke \`/cat:learn-agent\` with:`
- Commit with message: `config: fix stale /cat:learn references in plugin concepts`

Fix stale `/cat:learn` references in plugin rules and skills files. These files use `bugfix:` commit type.

- In `plugin/rules/approval-gate-protocol.md`, change line 64:
  - Old: `1. Invoke the \`/cat:learn\` workflow immediately`
  - New: `1. Invoke the \`/cat:learn-agent\` skill immediately`
- In `plugin/skills/learn/first-use.md`, change line 161:
  - Old: `2. Manual recovery: Invoke /cat:learn in foreground mode to re-run the analysis`
  - New: `2. Manual recovery: Invoke /cat:learn-agent in foreground mode to re-run the analysis`
- Update `.cat/issues/v2/v2.1/fix-stale-learn-skill-references/index.json`: set `"status": "closed"`, add `"resolution": "implemented"`
- Commit with message: `bugfix: fix stale /cat:learn references in plugin rules and skills, close 2.1-fix-stale-learn-skill-references`
