# Plan

## Goal

Document that API-based hook additionalContext is not logged in JSONL — in the get-history skill (first-use.md) and
SessionAnalyzer (both Javadoc and user-facing hint in search output when zero results returned).

## Pre-conditions

(none)

## Post-conditions

- [ ] get-history skill first-use.md includes a section documenting that SubagentStart hook additionalContext is
  injected at the API level and NOT stored in JSONL session logs, so session-analyzer searches cannot find this content
- [ ] SessionAnalyzer search output includes a user-facing hint when the search returns zero results, explaining that
  hook additionalContext is injected at the API level and is not stored in JSONL logs
- [ ] SessionAnalyzer source includes Javadoc on the search method explaining the limitation
- [ ] Tests passing — no regressions
- [ ] E2E: Run session-analyzer search for a SubagentStart-related term (e.g., "additionalContext") in a session that
  had subagents; confirm output includes the limitation hint when no results are found

## Commit Type

`docs:` for `plugin/skills/get-history-agent/first-use.md`
`feature:` for `client/` changes (new hint output in SessionAnalyzer search)

Use a single commit per logical group (one for plugin docs, one for Java changes + tests).

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/get-history-agent/first-use.md`: add a new section "## Hook additionalContext Limitation"
  before "## Error Handling", explaining that SubagentStart hook additionalContext fields are injected at the API
  level and are NOT stored in JSONL session logs; session-analyzer searches cannot find this content. Example text:
  ```
  ## Hook additionalContext Limitation

  The `additionalContext` field in hook events (e.g., `SubagentStart`) is injected at the API level directly into
  the subagent's context window. This content is **not** stored in JSONL session logs. As a result,
  `session-analyzer search` cannot find text that was provided via `additionalContext` — it only scans JSONL log
  entries. If a search returns zero results, the content may have been injected via `additionalContext` rather than
  logged.
  ```
  Commit as: `docs: document additionalContext hook limitation in get-history skill`

### Wave 2

- Modify `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`:

  **Change 1 — Javadoc on `search(Path, String, int, boolean)` method (around line 1872):**
  Add a `<p>` paragraph to the existing Javadoc of the `search(Path filePath, String pattern, int contextLines,
  boolean useRegex)` method explaining that `additionalContext` from hook events is injected at the API level and
  is NOT stored in JSONL logs, so searches will not find this content. Place the paragraph after the existing
  paragraphs, before the `@param` tags. Example:
  ```
   * <p>
   * Note: {@code additionalContext} fields from hook events (such as {@code SubagentStart}) are injected at the
   * API level and are not stored in JSONL session logs. Searches against session files will not return matches
   * for content delivered via {@code additionalContext}.
  ```

  **Change 2 — Zero-results hint in the public `search(Path, String, int, boolean)` method (around line 1896):**
  The `search()` method already builds and returns an `ObjectNode result`. After the `result.put("pattern", pattern)`
  and `result.put("total_entries_scanned", entries.size())` lines (before the final `return result;`), add:
  ```java
  if (matches.isEmpty())
    result.put("hint",
      "No matches found. Note: additionalContext from hook events (e.g., SubagentStart) is injected at the API " +
      "level and is NOT stored in JSONL session logs — session-analyzer searches cannot find this content.");
  ```
  The hint is a plain string field named "hint" on the top-level result JSON object, present only when the
  "matches" array is empty. Do NOT add the hint in `runSearchCommand` — placing it in the public `search()`
  method makes it testable via the public API without reflection.

- Modify `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java`:
  Add TWO new test methods:
  1. `searchWithNoMatches_includesAdditionalContextHint()` — Creates a minimal JSONL session file with entries
     that don't contain the search pattern. Calls `analyzer.search(filePath, "additionalContext_not_present",
     0, false)`. Verifies the returned JSON has a "hint" field that contains "additionalContext".
  2. `searchWithMatches_doesNotIncludeHint()` — Creates a minimal JSONL session file with an entry containing
     the search pattern. Calls `analyzer.search(filePath, pattern, 0, false)`.
     Verifies the returned JSON does NOT have a "hint" field (hint is only added when zero results).

  **IMPORTANT**: Per the CLAUDE.md Testing Requirements, use `cat:tdd-implementation` pattern: write the FAILING
  tests first, then modify SessionAnalyzer to make them pass.

  Commit both Java file changes and tests together as: `feature: add zero-results hint to SessionAnalyzer search for additionalContext limitation`

- Run `mvn -f client/pom.xml verify -e` and fix all errors before committing.
- Update `.cat/issues/v2/v2.1/document-hook-additional-context-not-in-jsonl/index.json` with `status: closed,
  progress: 100%` in the final commit of Wave 2.
