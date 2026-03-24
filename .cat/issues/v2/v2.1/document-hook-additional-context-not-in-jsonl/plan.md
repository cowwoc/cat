# Plan

## Goal

Document that API-based hook additionalContext is not logged in JSONL — in the get-history skill (first-use.md) and SessionAnalyzer (both Javadoc and user-facing hint in search output when zero results returned).

## Pre-conditions

(none)

## Post-conditions

- [ ] get-history skill first-use.md includes a section documenting that SubagentStart hook additionalContext is injected at the API level and NOT stored in JSONL session logs, so session-analyzer searches cannot find this content
- [ ] SessionAnalyzer search output includes a user-facing hint when the search returns zero results, explaining that hook additionalContext is injected at the API level and is not stored in JSONL logs
- [ ] SessionAnalyzer source includes Javadoc on the search method explaining the limitation
- [ ] Tests passing — no regressions
- [ ] E2E: Run session-analyzer search for a SubagentStart-related term (e.g., "additionalContext") in a session that had subagents; confirm output includes the limitation hint when no results are found
